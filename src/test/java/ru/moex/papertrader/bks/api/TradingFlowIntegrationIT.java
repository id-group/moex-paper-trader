package ru.moex.papertrader.bks.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import ru.moex.papertrader.core.domain.Order;
import ru.moex.papertrader.core.domain.OrderIdType;
import ru.moex.papertrader.core.domain.OrderStatus;
import ru.moex.papertrader.infra.services.FillService;
import ru.moex.papertrader.infra.services.PortfolioProperties;
import tools.jackson.databind.JsonNode;

/**
 * Сквозной сценарий этапа 3 по HTTP: заявка через REST → сделка через внутренний {@link
 * FillService} → портфель и лимиты через REST отражают позицию, кэш и ГО.
 *
 * <p>Сценарий коммитит данные, поэтому работает на отдельном счёте {@value #ACCOUNT}: заявки
 * BKS-тестов на {@code PAPER-0001} не должны влиять на блокировки и кэш. Данные счёта удаляются в
 * {@link #deleteAccountData()} в порядке зависимостей внешних ключей; справочник инструментов общий
 * и не трогается.
 */
@TestPropertySource(properties = "emulator.portfolio.account=" + TradingFlowIntegrationIT.ACCOUNT)
class TradingFlowIntegrationIT extends BksApiTestSupport {

  static final String ACCOUNT = "IT-E2E-ACC-1";

  private static final String ORDERS = BASE_PATH + "/v1/orders";
  private static final String PORTFOLIO = BASE_PATH + "/v1/portfolio";
  private static final String LIMITS = BASE_PATH + "/v1/limits";

  /** Заглушка рыночных данных: цена SBER (application.yml, marketdata.stub). */
  private static final BigDecimal SBER_LAST_PRICE = new BigDecimal("285.50");

  /** 0.1% от 2855.00 — комиссия покупки 10 акций. */
  private static final BigDecimal STOCK_COMMISSION = new BigDecimal("2.86");

  /** Биржевой сбор фьючерса — 1.00 RUB за контракт. */
  private static final BigDecimal FUTURES_COMMISSION = new BigDecimal("1.00");

  @Autowired private FillService fillService;
  @Autowired private PortfolioProperties portfolioProperties;
  @Autowired private JdbcTemplate jdbcTemplate;

  @AfterEach
  void deleteAccountData() {
    // Порядок продиктован внешними ключами: fills → orders → positions → nav_daily →
    // account_balances → accounts. Общие для BKS-тестов инструменты не трогаем, кроме SiU6/SPBFUT:
    // его создаёт только этот сценарий, а JpaOrderServiceImplIT проверяет автозоздание инструмента
    // и требует, чтобы строки ещё не было.
    jdbcTemplate.update("DELETE FROM fills WHERE account_id = ?", ACCOUNT);
    jdbcTemplate.update("DELETE FROM orders WHERE account_id = ?", ACCOUNT);
    jdbcTemplate.update("DELETE FROM positions WHERE account_id = ?", ACCOUNT);
    jdbcTemplate.update("DELETE FROM nav_daily WHERE account_id = ?", ACCOUNT);
    jdbcTemplate.update("DELETE FROM account_balances WHERE account_id = ?", ACCOUNT);
    jdbcTemplate.update("DELETE FROM accounts WHERE account_id = ?", ACCOUNT);
    jdbcTemplate.update(
        "DELETE FROM instruments WHERE ticker = ? AND class_code = ?", "SiU6", "SPBFUT");
  }

  @Test
  void stockThenFuturesFlowIsReflectedInPortfolio() {
    BigDecimal initialCash = portfolioProperties.initialCash();
    assertThat(portfolioProperties.account()).isEqualTo(ACCOUNT);
    assertThat(initialCash)
        .as("начальный остаток счёта из application.yml")
        .isEqualByComparingTo("1000000.00");

    // 1. Заявка на покупку 10 SBER по 285.5 принимается по HTTP в статусе NEW.
    String clientOrderId = UUID.randomUUID().toString();
    ApiResponse created =
        post(
            ORDERS + "/sync", tradeToken(), limitOrder(clientOrderId, "SBER", "TQBR", 10, "285.5"));

    assertThat(created.status()).isEqualTo(200);
    JsonNode createdOrder = created.json().path("data").get(0);
    assertThat(createdOrder.path("orderStatus").asText()).isEqualTo("0");
    assertThat(createdOrder.path("orderNumber").asText()).isNotBlank();
    String exchangeOrderId = createdOrder.path("orderId").asText();
    long stockOrderNum = createdOrder.path("orderNumber").asLong();

    // Активная заявка блокирует стоимость покупки, кэш при этом не меняется.
    JsonNode limitsBeforeFill = get(LIMITS, tradeToken()).json();
    assertThat(decimal(limitsBeforeFill.path("moneyLimits").get(0).path("quantity").path("value")))
        .as("счёт создан с начальным остатком")
        .isEqualByComparingTo(initialCash);
    assertThat(decimal(limitsBeforeFill.path("moneyLimits").get(0).path("locked")))
        .as("блокировка 10 x 285.50 под активную заявку")
        .isEqualByComparingTo("2855.00");

    // 2. Сделка исполняется внутренним сервисом по последней цене заглушки (285.50).
    Order filled = fillService.fill(OrderIdType.EXCHANGE, exchangeOrderId, 10, null);
    assertThat(filled.status()).isEqualTo(OrderStatus.FILLED);
    assertThat(filled.averagePrice()).isEqualByComparingTo(SBER_LAST_PRICE);

    BigDecimal cashAfterStock =
        initialCash
            .subtract(SBER_LAST_PRICE.multiply(BigDecimal.valueOf(10)))
            .subtract(STOCK_COMMISSION);

    // 3. Статус заявки по HTTP — FILLED (код 2 контракта).
    ApiResponse status = get(ORDERS + "?orderIdType=2&orderId=" + exchangeOrderId, tradeToken());
    assertThat(status.status()).isEqualTo(200);
    JsonNode storedOrder = status.json().path("data").get(0);
    assertThat(storedOrder.path("orderStatus").asText()).isEqualTo("2");
    assertThat(storedOrder.path("executedQuantity").asDouble()).isEqualTo(10.0);
    assertThat(storedOrder.path("remainedQuantity").asDouble()).isEqualTo(0.0);

    // 4. Портфель и лимиты: кэш уменьшен на стоимость и комиссию, SBER 10 шт., блокировок нет.
    JsonNode portfolio = get(PORTFOLIO, tradeToken()).json();
    JsonNode moneyEntry = entryOfType(portfolio, "moneyLimit");
    assertThat(moneyEntry.path("account").asText()).isEqualTo(ACCOUNT);
    assertThat(decimal(moneyEntry.path("quantity")))
        .as("кэш moneyLimit после покупки акций")
        .isEqualByComparingTo(cashAfterStock);
    assertThat(decimal(moneyEntry.path("locked"))).isEqualByComparingTo("0");
    assertThat(decimal(entryByTicker(portfolio, "depoLimit", "SBER").path("quantity")))
        .isEqualByComparingTo("10");

    JsonNode limits = get(LIMITS, tradeToken()).json();
    JsonNode moneyLimit = limits.path("moneyLimits").get(0);
    assertThat(decimal(moneyLimit.path("quantity").path("value")))
        .as("кэш в лимитах после покупки акций")
        .isEqualByComparingTo(cashAfterStock);
    assertThat(decimal(moneyLimit.path("locked")))
        .as("lockedCash: исполненная заявка ничего не блокирует")
        .isEqualByComparingTo("0");

    JsonNode sberDepo = entryByTicker(limits.path("depoLimit"), null, "SBER");
    assertThat(decimal(sberDepo.path("quantity").path("value"))).isEqualByComparingTo("10");
    assertThat(decimal(sberDepo.path("quantityBatch").path("value")))
        .as("10 акций при лоте 10 — один лот")
        .isEqualByComparingTo("1");
    assertThat(limits.path("futuresLimits").size())
        .as("без фьючерсной позиции и ГО фьючерсных лимитов нет")
        .isZero();

    // 5. Фьючерс SiU6 на том же счёте: заявка блокирует ГО заявки 10% от 90000 = 9000.
    ApiResponse futuresCreated =
        post(
            ORDERS + "/sync",
            tradeToken(),
            limitOrder(UUID.randomUUID().toString(), "SiU6", "SPBFUT", 1, "90000"));

    assertThat(futuresCreated.status()).isEqualTo(200);
    JsonNode futuresOrder = futuresCreated.json().path("data").get(0);
    assertThat(futuresOrder.path("orderStatus").asText()).isEqualTo("0");
    String futuresOrderId = futuresOrder.path("orderId").asText();

    JsonNode limitsWithOrder = get(LIMITS, tradeToken()).json();
    JsonNode futuresLimit = limitsWithOrder.path("futuresLimits").get(0);
    assertThat(decimal(futuresLimit.path("cbplUsedForOrders")))
        .as("ГО активной фьючерсной заявки")
        .isEqualByComparingTo("9000");
    assertThat(decimal(futuresLimit.path("cbplUsedForPositions")))
        .as("открытой фьючерсной позиции ещё нет")
        .isEqualByComparingTo("0");
    assertThat(decimal(futuresLimit.path("cbplUsed")))
        .as("суммарное ГО — только ГО заявки")
        .isEqualByComparingTo("9000");
    assertThat(decimal(limitsWithOrder.path("moneyLimits").get(0).path("quantity").path("value")))
        .as("размещение заявки кэш не меняет")
        .isEqualByComparingTo(cashAfterStock);

    // Сделка по фьючерсу: контракт не оплачивается, списывается только сбор 1.00;
    // ГО переезжает из заявки в позицию.
    Order futuresFilled = fillService.fill(OrderIdType.EXCHANGE, futuresOrderId, 1, null);
    assertThat(futuresFilled.status()).isEqualTo(OrderStatus.FILLED);

    BigDecimal cashAfterFutures = cashAfterStock.subtract(FUTURES_COMMISSION);
    JsonNode limitsAfterFill = get(LIMITS, tradeToken()).json();
    JsonNode futuresLimitAfterFill = limitsAfterFill.path("futuresLimits").get(0);
    assertThat(decimal(futuresLimitAfterFill.path("cbplUsedForOrders")))
        .as("исполненная заявка ГО не держит")
        .isEqualByComparingTo("0");
    assertThat(decimal(futuresLimitAfterFill.path("cbplUsedForPositions")))
        .as("ГО открытой фьючерсной позиции")
        .isEqualByComparingTo("9000");
    assertThat(decimal(futuresLimitAfterFill.path("cbplUsed")))
        .as("суммарное ГО — ГО позиции")
        .isEqualByComparingTo("9000");
    assertThat(decimal(limitsAfterFill.path("moneyLimits").get(0).path("locked")))
        .as("lockedCash = ГО позиции")
        .isEqualByComparingTo("9000");
    assertThat(decimal(limitsAfterFill.path("moneyLimits").get(0).path("quantity").path("value")))
        .as("кэш после биржевого сбора фьючерса")
        .isEqualByComparingTo(cashAfterFutures);

    JsonNode siHolding = entryByTicker(limitsAfterFill.path("futureHolding"), null, "SiU6");
    assertThat(decimal(siHolding.path("averagePrice"))).isEqualByComparingTo("90000");

    JsonNode portfolioAfterFill = get(PORTFOLIO, tradeToken()).json();
    JsonNode futuresHolding = entryByTicker(portfolioAfterFill, "futuresHolding", "SiU6");
    assertThat(decimal(futuresHolding.path("quantity"))).isEqualByComparingTo("1");
    JsonNode moneyEntryAfterFill = entryOfType(portfolioAfterFill, "moneyLimit");
    assertThat(decimal(moneyEntryAfterFill.path("lockedForFutures")))
        .as("ГО фьючерсной позиции в записи moneyLimit")
        .isEqualByComparingTo("9000");

    assertThat(stockOrderNum).isLessThan(futuresOrder.path("orderNumber").asLong());
  }

  private static Map<String, Object> limitOrder(
      String clientOrderId, String ticker, String classCode, long quantity, String price) {
    Map<String, Object> request = new LinkedHashMap<>();
    request.put("clientOrderId", clientOrderId);
    request.put("side", "1");
    request.put("orderType", "2");
    request.put("timeInForce", "1");
    request.put("orderQuantity", quantity);
    request.put("ticker", ticker);
    request.put("classCode", classCode);
    request.put("price", new BigDecimal(price));
    return request;
  }

  /** Запись портфеля с заданным {@code type}; отсутствие записи — ошибка сценария. */
  private static JsonNode entryOfType(JsonNode portfolio, String type) {
    for (JsonNode entry : portfolio) {
      if (type.equals(entry.path("type").asText())) {
        return entry;
      }
    }
    throw new AssertionError("В портфеле нет записи типа " + type + ": " + portfolio);
  }

  /** Запись массива по тикеру; {@code type == null} — без фильтра по типу. */
  private static JsonNode entryByTicker(JsonNode entries, String type, String ticker) {
    for (JsonNode entry : entries) {
      if (ticker.equals(entry.path("ticker").asText())
          && (type == null || type.equals(entry.path("type").asText()))) {
        return entry;
      }
    }
    throw new AssertionError("Нет записи " + ticker + ": " + entries);
  }

  /** Числовое сравнение: масштаб BigDecimal в JSON может отличаться от ожидаемого. */
  private static BigDecimal decimal(JsonNode node) {
    assertThat(node.isMissingNode() || node.isNull())
        .as("денежное/количественное поле отсутствует в ответе")
        .isFalse();
    return new BigDecimal(node.asText());
  }
}
