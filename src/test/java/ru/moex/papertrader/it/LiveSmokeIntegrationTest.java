package ru.moex.papertrader.it;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import ru.moex.papertrader.PostgresContainerSupport;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Ручной smoke-тест против живых рыночных данных MOEX (режим LIVE: AlgoPack с fallback на ISS).
 *
 * <p>Запуск вручную, со своим токеном AlgoPack:
 *
 * <pre>
 *   $env:ALGO_PACK_TOKEN="..."
 *   .\gradlew.bat test --tests "ru.moex.papertrader.it.LiveSmokeIntegrationTest"
 * </pre>
 *
 * <p>По умолчанию тест пропускается: в обычном {@code .\gradlew.bat test} переменная окружения
 * {@code ALGO_PACK_TOKEN} не задана, и guard {@code @EnabledIfEnvironmentVariable} помечает тест
 * skipped ещё до подъёма Spring-контекста и Docker-контейнера. Без Docker или сети тест тоже не
 * выполняется — раньше срабатывает этот же guard.
 *
 * <p>Проверяет сквозной контракт БКС на живых данных: токен → справочник инструментов → котировки →
 * стакан → LIMIT-заявка «дальше от рынка» (не должна исполниться) → отмена → очистка тестовых
 * данных счёта. Стакан вне подписки AlgoPack может быть пустым: это фиксируется предупреждением в
 * лог, а не отказом теста. Тест детерминированным быть не обязан: он показывает живое окружение, а
 * не воспроизводимость (детерминизм обеспечивают REPLAY-тесты).
 */
@Tag("live")
@EnabledIfEnvironmentVariable(named = "ALGO_PACK_TOKEN", matches = ".+")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@TestPropertySource(
    properties = {
      "emulator.marketdata.mode=LIVE",
      "emulator.portfolio.account=IT-LIVE-SMOKE",
      "emulator.execution.scheduler-enabled=false",
      "emulator.marketdata.iss-base-url=https://iss.moex.com"
    })
class LiveSmokeIntegrationTest extends PostgresContainerSupport {

  private static final Logger log = LoggerFactory.getLogger(LiveSmokeIntegrationTest.class);

  private static final String BASE_PATH = "/api/bks";
  private static final String TOKEN_PATH =
      BASE_PATH + "/trade-api-keycloak/realms/tradeapi/protocol/openid-connect/token";

  /** Тестовый refresh-токен write-клиента из {@code application-test.yml}. */
  private static final String TRADE_REFRESH_TOKEN = "test-trade-refresh-token";

  private static final String WRITE_CLIENT = "trade-api-write";

  /** Тестовый счёт: удаляется в {@link #deleteAccountData()} без следов в БД. */
  private static final String ACCOUNT = "IT-LIVE-SMOKE";

  private static final String TQBR = "TQBR";

  private static final JsonMapper JSON = JsonMapper.builder().build();
  private static final HttpClient HTTP = HttpClient.newHttpClient();

  @LocalServerPort private int port;

  @Autowired private JdbcTemplate jdbcTemplate;

  @AfterEach
  void deleteAccountData() {
    jdbcTemplate.update("DELETE FROM backtest_results WHERE account_id = ?", ACCOUNT);
    jdbcTemplate.update("DELETE FROM reports WHERE account_id = ?", ACCOUNT);
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
  void liveSmokeRunsWithRealAlgoPackAndIssData() {
    // 1) Токен write-клиента.
    String token = accessToken();

    // 2) Справочник инструментов (запрос TickersRequest: {"tickers":["SBER","GAZP"]}).
    ApiResponse instruments =
        post(
            BASE_PATH + "/v1/instruments/by-tickers",
            token,
            Map.of("tickers", List.of("SBER", "GAZP")));
    assertThat(instruments.status()).as("справочник по тикерам").isEqualTo(200);
    assertThat(instruments.json().size()).as("справочник не пуст").isPositive();

    // 3) Котировки (запрос QuotesRequest: {"instruments":[{ticker, classCode}]}).
    ApiResponse quotes =
        post(
            BASE_PATH + "/v1/quotes",
            token,
            Map.of("instruments", List.of(instrumentRef("SBER"), instrumentRef("GAZP"))));
    assertThat(quotes.status()).as("котировки").isEqualTo(200);
    assertThat(quotes.json().path("records").size()).as("котировки не пусты").isPositive();
    assertThat(hasPositivePrice(quotes.json().path("records")))
        .as("как минимум одна котировка с положительной ценой last/bid/ask")
        .isTrue();

    // 4) Стакан SBER. Живой стакан вне подписки AlgoPack может быть пуст — только предупреждение.
    ApiResponse book =
        get(BASE_PATH + "/v1/order-book?ticker=SBER&classCode=" + TQBR + "&depth=5", token);
    assertThat(book.status()).as("стакан SBER").isEqualTo(200);
    warnAboutEmptyOrderBook(book.json());

    // 5) LIMIT-заявка «дальше от рынка»: цена 1.00 заметно ниже беста, исполнение не ожидается.
    String clientOrderId = UUID.randomUUID().toString();
    ApiResponse created =
        post(
            BASE_PATH + "/v1/orders/sync",
            token,
            limitBuyOrder(clientOrderId),
            Map.of("X-Strategy-Id", "live-smoke"));
    assertThat(created.status()).as("создание LIMIT-заявки").isEqualTo(200);

    // 6) Отмена заявки по клиентскому идентификатору (orderIdType "1" — CLIENT).
    ApiResponse cancelled =
        post(
            BASE_PATH + "/v1/orders/cancel/sync",
            token,
            Map.of(
                "orderIdType",
                "1",
                "orderId",
                clientOrderId,
                "clientOrderId",
                UUID.randomUUID().toString()));
    assertThat(cancelled.status()).as("отмена заявки").isEqualTo(200);
  }

  /** Есть ли у списка котировок положительная цена last, bid или ask. */
  private static boolean hasPositivePrice(JsonNode records) {
    for (int i = 0; i < records.size(); i++) {
      JsonNode quote = records.get(i);
      if (quote.path("last").asDouble() > 0
          || quote.path("bid").asDouble() > 0
          || quote.path("offer").asDouble() > 0) {
        return true;
      }
    }
    return false;
  }

  /** Стакан вне подписки AlgoPack часто пуст; пустой стакан — не отказ smoke-теста. */
  private static void warnAboutEmptyOrderBook(JsonNode book) {
    JsonNode bids = book.path("bids");
    JsonNode asks = book.path("asks");
    if (bids.isEmpty() || asks.isEmpty()) {
      log.warn(
          "Стакан SBER/TQBR пуст: AlgoPack/ISS может требовать подписку (bids={}, asks={}).",
          bids.size(),
          asks.size());
    }
  }

  private static Map<String, Object> instrumentRef(String ticker) {
    return Map.of("ticker", ticker, "classCode", TQBR);
  }

  /**
   * Тело {@code POST /api/bks/v1/orders/sync} по схеме {@code CreateOrderRequest}: side "1" (BUY),
   * orderType "2" (LIMIT), timeInForce "1" (DAY).
   */
  private static Map<String, Object> limitBuyOrder(String clientOrderId) {
    return Map.of(
        "clientOrderId",
        clientOrderId,
        "side",
        "1",
        "orderType",
        "2",
        "timeInForce",
        "1",
        "orderQuantity",
        1,
        "ticker",
        "SBER",
        "classCode",
        TQBR,
        "price",
        1.00);
  }

  /** Access-токен эмулятора Keycloak по refresh-токену тестового профиля. */
  private String accessToken() {
    String form =
        "client_id="
            + encode(WRITE_CLIENT)
            + "&refresh_token="
            + encode(TRADE_REFRESH_TOKEN)
            + "&grant_type=refresh_token";
    ApiResponse response = postForm(TOKEN_PATH, form);
    if (response.status() != 200) {
      throw new IllegalStateException("Не удалось получить access-токен: " + response.body());
    }
    return response.json().path("access_token").asText();
  }

  private ApiResponse get(String path, String token) {
    return send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).GET(), token);
  }

  private ApiResponse post(String path, String token, Object body) {
    return post(path, token, body, Map.of());
  }

  private ApiResponse postForm(String path, String form) {
    HttpRequest.Builder builder =
        HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
            .header("Content-Type", "application/x-www-form-urlencoded")
            .POST(HttpRequest.BodyPublishers.ofString(form, StandardCharsets.UTF_8));
    return send(builder, null);
  }

  private ApiResponse post(
      String path, String token, Object body, Map<String, String> extraHeaders) {
    HttpRequest.Builder builder =
        HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
            .header("Content-Type", "application/json")
            .POST(
                HttpRequest.BodyPublishers.ofString(
                    JSON.writeValueAsString(body), StandardCharsets.UTF_8));
    extraHeaders.forEach(builder::header);
    return send(builder, token);
  }

  private ApiResponse send(HttpRequest.Builder builder, String token) {
    if (token != null) {
      builder.header("Authorization", "Bearer " + token);
    }
    try {
      HttpResponse<String> response =
          HTTP.send(builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
      return new ApiResponse(response.statusCode(), response.body());
    } catch (IOException exception) {
      throw new IllegalStateException("HTTP-вызов не удался", exception);
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("HTTP-вызов прерван", exception);
    }
  }

  private static String encode(String value) {
    return URLEncoder.encode(value, StandardCharsets.UTF_8);
  }

  /**
   * Ответ API с разбором JSON по запросу — минимальная копия {@code BksApiTestSupport.ApiResponse}.
   */
  private record ApiResponse(int status, String body) {

    private JsonNode json() {
      return JSON.readTree(body);
    }
  }
}
