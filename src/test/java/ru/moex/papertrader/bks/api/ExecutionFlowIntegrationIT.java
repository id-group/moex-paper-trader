package ru.moex.papertrader.bks.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import ru.moex.papertrader.core.domain.Order;
import ru.moex.papertrader.core.domain.OrderIdType;
import ru.moex.papertrader.core.domain.OrderStatus;
import ru.moex.papertrader.core.execution.OrderEventType;
import ru.moex.papertrader.core.marketdata.Clock;
import ru.moex.papertrader.infra.model.OrderEventEntity;
import ru.moex.papertrader.infra.repository.OrderEventRepository;
import ru.moex.papertrader.infra.services.ExecutionService;
import ru.moex.papertrader.infra.services.FixedClockTestConfiguration;
import ru.moex.papertrader.infra.services.OrderService;
import ru.moex.papertrader.infra.services.PortfolioProperties;
import ru.moex.papertrader.testutil.FixedClock;
import tools.jackson.databind.JsonNode;

/**
 * Сквозной сценарий этапа 5 по HTTP: заявка БКС → матчинг по стакану → сделка, портфель и журнал
 * {@code order_events}.
 *
 * <p>Сценарий работает на отдельном счёте {@value #ACCOUNT} и очищает его данные после каждого
 * теста: заявки, сделки и события не должны влиять на другие интеграционные тесты.
 */
@TestPropertySource(properties = "emulator.portfolio.account=" + ExecutionFlowIntegrationIT.ACCOUNT)
@Import(FixedClockTestConfiguration.class)
class ExecutionFlowIntegrationIT extends BksApiTestSupport {

  static final String ACCOUNT = "IT-EXEC-ACC-1";

  private static final String ORDERS = BASE_PATH + "/v1/orders";
  private static final String PORTFOLIO = BASE_PATH + "/v1/portfolio";

  /** Минимальная цена ask в стакане SBER (base 285.50 + шаг 0.01). */
  private static final BigDecimal SBER_BEST_ASK = new BigDecimal("285.51");

  /** Суммарный объём asks стакана глубины 10 по 100 × i: 100 + ... + 1000. */
  private static final long SBER_BOOK_VOLUME = 5_500L;

  @Autowired private ExecutionService executionService;
  @Autowired private OrderService orderService;
  @Autowired private OrderEventRepository orderEvents;
  @Autowired private PortfolioProperties portfolioProperties;
  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private Clock applicationClock;

  private FixedClock clock;

  @BeforeEach
  void resetClock() {
    clock = (FixedClock) applicationClock;
    clock.advanceTo(FixedClockTestConfiguration.NOW);
  }

  @AfterEach
  void deleteAccountData() {
    jdbcTemplate.update(
        "DELETE FROM order_events WHERE order_id IN (SELECT order_id FROM orders WHERE account_id = ?)",
        ACCOUNT);
    jdbcTemplate.update("DELETE FROM fills WHERE account_id = ?", ACCOUNT);
    jdbcTemplate.update("DELETE FROM orders WHERE account_id = ?", ACCOUNT);
    jdbcTemplate.update("DELETE FROM positions WHERE account_id = ?", ACCOUNT);
    jdbcTemplate.update("DELETE FROM nav_daily WHERE account_id = ?", ACCOUNT);
    jdbcTemplate.update("DELETE FROM account_balances WHERE account_id = ?", ACCOUNT);
    jdbcTemplate.update("DELETE FROM accounts WHERE account_id = ?", ACCOUNT);
  }

  @Test
  void marketBuyIsFilledByOrderBookAndJournalAndPortfolioUpdated() {
    BigDecimal initialCash = portfolioProperties.initialCash();

    JsonNode created = createOrder(marketOrder("SBER", "TQBR", "1", 10));

    assertThat(created.path("orderStatus").asText()).isEqualTo("2");
    assertThat(created.path("executedQuantity").asLong()).isEqualTo(10);
    assertThat(created.path("remainedQuantity").asLong()).isZero();
    BigDecimal averagePrice = decimal(created.path("averagePrice"));
    assertThat(averagePrice)
        .as("маркет-покупка исполняется по ask с проскальзыванием вверх")
        .isGreaterThan(SBER_BEST_ASK);
    assertThat(averagePrice)
        .as("базовое проскальзывание не больше 0.15%")
        .isLessThan(SBER_BEST_ASK.multiply(new BigDecimal("1.0015")));

    String orderId = created.path("orderId").asText();
    assertThat(eventTypes(orderId)).containsExactly(OrderEventType.CREATED, OrderEventType.FILLED);

    BigDecimal turnover = money(averagePrice.multiply(BigDecimal.valueOf(10)));
    BigDecimal commission = money(turnover.multiply(new BigDecimal("0.001")));
    BigDecimal expectedCash = initialCash.subtract(turnover).subtract(commission);

    JsonNode portfolio = get(PORTFOLIO, tradeToken()).json();
    JsonNode moneyLimit = entryOfType(portfolio, "moneyLimit");
    assertThat(decimal(moneyLimit.path("quantity")))
        .as("кэш уменьшен на стоимость сделки и комиссию")
        .isEqualByComparingTo(expectedCash);
    assertThat(decimal(moneyLimit.path("locked"))).isEqualByComparingTo("0");
    assertThat(decimal(entryByTicker(portfolio, "depoLimit", "SBER").path("quantity")))
        .isEqualByComparingTo("10");
    assertThat(fillCount(orderId)).isEqualTo(1);
  }

  @Test
  void crossedLimitBuyExecutesAtLimitOrBetterWithoutSlippage() {
    BigDecimal initialCash = portfolioProperties.initialCash();

    JsonNode created = createOrder(limitOrder("SBER", "TQBR", "1", 10, "286.00", null));

    assertThat(created.path("orderStatus").asText()).isEqualTo("2");
    assertThat(decimal(created.path("averagePrice")))
        .as("лимит 286.00 пересекает ask 285.51 — исполнение по цене стакана")
        .isEqualByComparingTo(SBER_BEST_ASK);

    BigDecimal turnover = money(SBER_BEST_ASK.multiply(BigDecimal.valueOf(10)));
    BigDecimal commission = money(turnover.multiply(new BigDecimal("0.001")));
    JsonNode portfolio = get(PORTFOLIO, tradeToken()).json();
    assertThat(decimal(entryOfType(portfolio, "moneyLimit").path("quantity")))
        .isEqualByComparingTo(initialCash.subtract(turnover).subtract(commission));
    assertThat(eventTypes(created.path("orderId").asText()))
        .containsExactly(OrderEventType.CREATED, OrderEventType.FILLED);
  }

  @Test
  void uncrossedLimitOrderRemainsNewAfterExecutionAttempt() {
    JsonNode created = createOrder(limitOrder("SBER", "TQBR", "1", 10, "285.50", null));

    assertThat(created.path("orderStatus").asText()).isEqualTo("0");
    String orderId = created.path("orderId").asText();

    executionService.tick();

    assertThat(orderService.get(OrderIdType.EXCHANGE, orderId).status()).isEqualTo(OrderStatus.NEW);
    assertThat(eventTypes(orderId)).containsExactly(OrderEventType.CREATED);
  }

  @Test
  void fokIsRejectedWhenBookCannotFillWholeVolume() {
    JsonNode created =
        createOrder(limitOrder("SBER", "TQBR", "1", SBER_BOOK_VOLUME + 10, "286.00", null, "2"));

    assertThat(created.path("orderStatus").asText()).isEqualTo("8");
    assertThat(created.path("executedQuantity").asLong()).isZero();
    String orderId = created.path("orderId").asText();
    assertThat(fillCount(orderId)).isZero();
    assertThat(eventTypes(orderId))
        .containsExactly(OrderEventType.CREATED, OrderEventType.REJECTED);

    OrderEventEntity rejected = events(orderId).get(1);
    assertThat(rejected.getReason()).contains("FOK");
    assertThat(orderService.get(OrderIdType.EXCHANGE, orderId).status())
        .isEqualTo(OrderStatus.REJECTED);
  }

  @Test
  void gtdOrderIsExpiredAfterExpiryDate() {
    LocalDate expiryDate = LocalDate.of(2026, 11, 3);
    JsonNode created = createOrder(limitOrder("SBER", "TQBR", "1", 10, "285.50", expiryDate));

    assertThat(created.path("orderStatus").asText()).isEqualTo("0");
    assertThat(created.path("expiryDate").asText()).isEqualTo(expiryDate.toString());

    String orderId = created.path("orderId").asText();
    clock.advanceTo(Instant.parse("2026-11-04T10:15:30Z"));
    executionService.tick();

    Order expired = orderService.get(OrderIdType.EXCHANGE, orderId);
    assertThat(expired.status()).isEqualTo(OrderStatus.CANCELED);
    assertThat(eventTypes(orderId)).containsExactly(OrderEventType.CREATED, OrderEventType.EXPIRED);
    OrderEventEntity event = events(orderId).get(1);
    assertThat(event.getPreviousStatus()).isEqualTo(OrderStatus.NEW);
    assertThat(event.getStatus()).isEqualTo(OrderStatus.CANCELED);
    assertThat(event.getReason()).contains("Срок действия");
    assertThat(fillCount(orderId)).isZero();
  }

  @Test
  void marketSellWithoutPositionIsRejectedButOrderIsPersisted() {
    JsonNode created = createOrder(marketOrder("GAZP", "TQBR", "2", 10));

    assertThat(created.path("orderStatus").asText()).isEqualTo("8");
    assertThat(created.path("executedQuantity").asLong()).isZero();
    String orderId = created.path("orderId").asText();
    assertThat(fillCount(orderId)).isZero();
    assertThat(eventTypes(orderId))
        .containsExactly(OrderEventType.CREATED, OrderEventType.REJECTED);
    assertThat(events(orderId).get(1).getReason()).contains("в позиции только");

    Order stored = orderService.get(OrderIdType.EXCHANGE, orderId);
    assertThat(stored.status()).isEqualTo(OrderStatus.REJECTED);
  }

  private JsonNode createOrder(Map<String, Object> request) {
    ApiResponse response = post(ORDERS + "/sync", tradeToken(), request);
    assertThat(response.status()).as("create order: %s", response.body()).isEqualTo(200);
    return response.json().path("data").get(0);
  }

  private List<OrderEventEntity> events(String orderId) {
    return orderEvents.findByOrderIdOrderByOccurredAtAscIdAsc(orderId);
  }

  private List<OrderEventType> eventTypes(String orderId) {
    return events(orderId).stream().map(OrderEventEntity::getEventType).toList();
  }

  private int fillCount(String orderId) {
    Integer count =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM fills WHERE order_id = ?", Integer.class, orderId);
    return count == null ? 0 : count;
  }

  private static Map<String, Object> marketOrder(
      String ticker, String classCode, String side, long quantity) {
    Map<String, Object> request = new LinkedHashMap<>();
    request.put("clientOrderId", UUID.randomUUID().toString());
    request.put("side", side);
    request.put("orderType", "1");
    request.put("timeInForce", "1");
    request.put("orderQuantity", quantity);
    request.put("ticker", ticker);
    request.put("classCode", classCode);
    return request;
  }

  private static Map<String, Object> limitOrder(
      String ticker, String classCode, String side, long quantity, String price, LocalDate expiry) {
    return limitOrder(ticker, classCode, side, quantity, price, expiry, expiry == null ? "1" : "3");
  }

  private static Map<String, Object> limitOrder(
      String ticker,
      String classCode,
      String side,
      long quantity,
      String price,
      LocalDate expiry,
      String timeInForce) {
    Map<String, Object> request = new LinkedHashMap<>();
    request.put("clientOrderId", UUID.randomUUID().toString());
    request.put("side", side);
    request.put("orderType", "2");
    request.put("timeInForce", timeInForce);
    if (expiry != null) {
      request.put("expiryDate", expiry.toString());
    }
    request.put("orderQuantity", quantity);
    request.put("ticker", ticker);
    request.put("classCode", classCode);
    request.put("price", new BigDecimal(price));
    return request;
  }

  private static JsonNode entryOfType(JsonNode portfolio, String type) {
    for (JsonNode entry : portfolio) {
      if (type.equals(entry.path("type").asText())) {
        return entry;
      }
    }
    throw new AssertionError("В портфеле нет записи типа " + type + ": " + portfolio);
  }

  private static JsonNode entryByTicker(JsonNode entries, String type, String ticker) {
    for (JsonNode entry : entries) {
      if (ticker.equals(entry.path("ticker").asText())
          && (type == null || type.equals(entry.path("type").asText()))) {
        return entry;
      }
    }
    throw new AssertionError("Нет записи " + ticker + ": " + entries);
  }

  private static BigDecimal decimal(JsonNode node) {
    assertThat(node.isMissingNode() || node.isNull())
        .as("денежное/количественное поле отсутствует в ответе")
        .isFalse();
    return new BigDecimal(node.asText());
  }

  private static BigDecimal money(BigDecimal value) {
    return value.setScale(2, RoundingMode.HALF_UP);
  }
}
