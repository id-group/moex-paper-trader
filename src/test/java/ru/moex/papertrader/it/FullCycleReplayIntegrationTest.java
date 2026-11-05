package ru.moex.papertrader.it;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import ru.moex.papertrader.PostgresContainerSupport;
import ru.moex.papertrader.core.marketdata.Clock;
import ru.moex.papertrader.core.marketdata.VirtualClock;
import ru.moex.papertrader.core.reporting.BacktestComparator;
import ru.moex.papertrader.infra.services.MarginSettlementService;
import ru.moex.papertrader.it.replay.IdealBacktest;
import ru.moex.papertrader.it.replay.ReplayDataset;
import ru.moex.papertrader.it.replay.SmaCrossoverStrategy;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Сквозной прогон этапа 7: тестовая стратегия → HTTP-контракт БКС → эмулятор → REPLAY-матчинг →
 * портфель → отчёт этапа 6 → сверка с эталонным бэктестом.
 *
 * <p>Детерминированный набор {@link ReplayDataset} пишется в PostgreSQL до создания провайдера
 * рыночных данных ({@link ReplaySeedSupport}), часы — только {@link VirtualClock}, поэтому прогон
 * воспроизводим: те же снапшоты, тот же seed проскальзывания, те же метрики. Эталон — «идеальный»
 * бэктест стратегии без проскальзывания и комиссий по ценам стакана; расхождение метрик обязано
 * уложиться в порог 30% ({@link BacktestComparator#DEFAULT_THRESHOLD}).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@TestPropertySource(
    properties = {
      "emulator.marketdata.mode=REPLAY",
      // Начало таймлайна совпадает с первым снапшотом набора (2026-09-01T07:00:00Z).
      "emulator.marketdata.replay.start=2026-09-01T07:00:00Z",
      // Фоновый REPLAY-планировщик не должен двигать виртуальное время во время прогона.
      "emulator.marketdata.replay.tick-ms=3600000",
      "emulator.portfolio.account=IT-STAGE7-FULLCYCLE",
      "emulator.reporting.output-dir=build/reports/test/stage7",
      "emulator.execution.scheduler-enabled=false"
    })
@Import(FullCycleReplayIntegrationTest.ReplaySeedConfiguration.class)
class FullCycleReplayIntegrationTest extends PostgresContainerSupport {

  private static final String ACCOUNT = "IT-STAGE7-FULLCYCLE";
  private static final String STRATEGY_ID = "stage7-sma";
  private static final String REPORT_LABEL = "STAGE7-REPLAY";
  private static final String BACKTEST_LABEL = "ideal-no-frictions";
  private static final BigDecimal THRESHOLD = new BigDecimal("0.30");
  private static final String BASE_PATH = "/api/bks";
  private static final String TOKEN_PATH =
      BASE_PATH + "/trade-api-keycloak/realms/tradeapi/protocol/openid-connect/token";
  private static final String TRADE_REFRESH_TOKEN = "test-trade-refresh-token";
  private static final String WRITE_CLIENT = "trade-api-write";

  private static final JsonMapper JSON = JsonMapper.builder().build();
  private static final HttpClient HTTP = HttpClient.newHttpClient();

  @LocalServerPort private int port;

  @Autowired private Clock clock;
  @Autowired private MarginSettlementService settlementService;
  @Autowired private JdbcTemplate jdbcTemplate;

  private final ReplayDataset dataset = ReplayDataset.standard();
  private String token;

  @BeforeEach
  void prepare() {
    cleanAccountRows();
    token = accessToken();
  }

  @AfterEach
  void cleanUp() {
    cleanAccountRows();
    jdbcTemplate.update(
        "DELETE FROM market_data_snapshots WHERE ts BETWEEN ? AND ?",
        dataset.start().minus(Duration.ofDays(2)).atOffset(ZoneOffset.UTC),
        dataset.end().plus(Duration.ofDays(2)).atOffset(ZoneOffset.UTC));
    jdbcTemplate.update(
        "DELETE FROM trading_calendar WHERE trade_date BETWEEN ? AND ?",
        dataset.tradingDates().get(0).minusDays(2),
        dataset.tradingDates().get(dataset.tradingDates().size() - 1).plusDays(2));
    // Фьючерс создаётся только этим сценарием — не даём ему протечь в другие сьюты.
    jdbcTemplate.update(
        "DELETE FROM instruments WHERE ticker = ? AND class_code = ?", "SiU6", "SPBFUT");
  }

  @Test
  void fullReplayCycleProducesReportWithinBacktestThreshold() throws Exception {
    VirtualClock virtualClock = (VirtualClock) clock;
    List<SmaCrossoverStrategy.Signal> signals = SmaCrossoverStrategy.signals(dataset);
    assertThat(signals).as("стратегия должна породить сделки").isNotEmpty();

    Map<LocalDate, List<SmaCrossoverStrategy.Signal>> byDay =
        signals.stream()
            .collect(
                Collectors.groupingBy(signal -> signal.at().atZone(ZoneOffset.UTC).toLocalDate()));

    for (LocalDate date : dataset.tradingDates()) {
      Instant sessionOpen = sessionOpen(date);
      // Первая точка дня — открытие; токен выдаётся уже внутри сессии и живёт 24 часа виртуального
      // времени (прогон длится 24 торговых дня, между днями бывают выходные).
      virtualClock.advanceTo(sessionOpen.plusSeconds(1));
      token = accessToken();
      for (SmaCrossoverStrategy.Signal signal : byDay.getOrDefault(date, List.of())) {
        virtualClock.advanceTo(signal.at());
        placeMarketOrder(signal);
      }
      virtualClock.advanceTo(sessionClose(date));
      settlementService.closeDay(date);
    }

    IdealBacktest.IdealResult benchmark = IdealBacktest.run(dataset);
    assertThat(benchmark.nav()).as("эталонный NAV").hasSize(dataset.tradingDates().size());

    JsonNode reportResponse = generateReport(benchmark);
    JsonNode report = reportResponse.path("report");

    assertThat(report.path("accountId").asText()).isEqualTo(ACCOUNT);
    assertThat(report.path("label").asText()).isEqualTo(REPORT_LABEL);
    assertThat(report.path("navSeries")).hasSize(dataset.tradingDates().size());
    assertThat(report.path("journal")).hasSize(signals.size());
    assertThat(report.path("commissionTotal").decimalValue()).isPositive();
    assertThat(report.path("slippageTotal").decimalValue()).isPositive();
    assertThat(report.path("winRate").isNull()).isFalse();

    JsonNode backtest = report.path("backtest");
    assertThat(backtest.path("passed").asBoolean())
        .as("сверка с бэктестом должна пройти: %s", backtest)
        .isTrue();
    assertThat(backtest.path("threshold").decimalValue()).isEqualByComparingTo(THRESHOLD);

    JsonNode deviations = backtest.path("deviations");
    assertThat(deviations).as("сопоставимые метрики").hasSizeGreaterThanOrEqualTo(4);
    for (JsonNode deviation : deviations) {
      String metric = deviation.path("metric").asText();
      JsonNode percent = deviation.path("percentDeviation");
      if (percent.isNull()) {
        assertThat(deviation.path("actual").decimalValue())
            .as("нулевой эталон: %s", metric)
            .isZero();
      } else {
        assertThat(percent.decimalValue())
            .as("расхождение метрики %s не должно превышать 30%%", metric)
            .isLessThanOrEqualTo(THRESHOLD);
      }
      assertThat(deviation.path("withinThreshold").asBoolean()).isTrue();
    }
    assertThat(metricNames(deviations)).contains("cagr", "sharpe", "maxDrawdown", "winRate");

    // Контракт БКС после прогона: статусы заявок и сделки видны через HTTP.
    HttpResult portfolio = get(BASE_PATH + "/v1/portfolio", token);
    assertThat(portfolio.status()).isEqualTo(200);
    assertThat(portfolio.json()).isNotEmpty();

    HttpResult limits = get(BASE_PATH + "/v1/limits", token);
    assertThat(limits.status()).isEqualTo(200);

    HttpResult orders =
        postJson(BASE_PATH + "/v1/orders/search?page=0&size=100", token, "{}", null);
    assertThat(orders.status()).isEqualTo(200);
    assertThat(orders.json().path("totalRecords").asLong()).isEqualTo(signals.size());

    HttpResult trades =
        postJson(BASE_PATH + "/v1/trades/search?page=0&size=100", token, "{}", null);
    assertThat(trades.status()).isEqualTo(200);
    assertThat(trades.json().path("totalRecords").asLong()).isEqualTo(signals.size());

    // Файлы отчёта и сводка расхождений текущего прогона — для docs/agent-briefs/stage-7-report.md.
    assertThat(Files.exists(Path.of(reportResponse.path("files").path("json").asText()))).isTrue();
    assertThat(Files.exists(Path.of(reportResponse.path("files").path("csv").asText()))).isTrue();
    Path summary = Path.of("build/reports/test/stage7/divergence.json");
    Files.createDirectories(summary.getParent());
    Files.writeString(
        summary,
        JSON.writeValueAsString(
            Map.of(
                "accountId", ACCOUNT,
                "trades", signals.size(),
                "metrics", report.path("metrics"),
                "winRate", report.path("winRate"),
                "slippageTotal", report.path("slippageTotal"),
                "commissionTotal", report.path("commissionTotal"),
                "backtest", backtest)));
  }

  private void placeMarketOrder(SmaCrossoverStrategy.Signal signal) {
    Map<String, Object> order = new LinkedHashMap<>();
    order.put("clientOrderId", UUID.randomUUID().toString());
    order.put("side", signal.side().code());
    // 1 — MARKET: исполнение немедленным кроссом стакана.
    order.put("orderType", "1");
    order.put("timeInForce", "1");
    order.put("orderQuantity", signal.quantity());
    order.put("ticker", signal.ticker());
    order.put("classCode", signal.classCode());

    HttpResult response =
        postJson(BASE_PATH + "/v1/orders/sync", token, JSON.writeValueAsString(order), STRATEGY_ID);
    assertThat(response.status())
        .as(
            "постановка %s %s x%s: %s",
            signal.side(), signal.ticker(), signal.quantity(), response.body())
        .isEqualTo(200);
    JsonNode data = response.json().path("data");
    assertThat(data).isNotEmpty();
    // 2 — FILLED: маркет исполняется сразу, стакан набора глубже объёма заявки.
    assertThat(data.get(0).path("orderStatus").asText()).isEqualTo("2");
  }

  private JsonNode generateReport(IdealBacktest.IdealResult benchmark) {
    Map<String, Object> backtest = new LinkedHashMap<>();
    backtest.put("label", BACKTEST_LABEL);
    backtest.put("metrics", benchmark.metrics());

    Map<String, Object> request = new LinkedHashMap<>();
    request.put("accountId", ACCOUNT);
    request.put("label", REPORT_LABEL);
    request.put("from", dataset.tradingDates().get(0).toString());
    request.put("to", dataset.tradingDates().get(dataset.tradingDates().size() - 1).toString());
    request.put("backtest", backtest);

    HttpResult response =
        postJson("/api/internal/reports", null, JSON.writeValueAsString(request), null);
    assertThat(response.status()).as("генерация отчёта: %s", response.body()).isEqualTo(200);
    return response.json();
  }

  private String accessToken() {
    String form =
        "client_id="
            + encode(WRITE_CLIENT)
            + "&refresh_token="
            + encode(TRADE_REFRESH_TOKEN)
            + "&grant_type=refresh_token";
    HttpResult response = postForm(TOKEN_PATH, form);
    assertThat(response.status()).as("токен: %s", response.body()).isEqualTo(200);
    return response.json().path("access_token").asText();
  }

  private HttpResult get(String path, String bearer) {
    return send(HttpRequest.newBuilder(uri(path)).GET(), bearer);
  }

  private HttpResult postJson(String path, String bearer, String body, String strategyId) {
    HttpRequest.Builder builder =
        HttpRequest.newBuilder(uri(path))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
    if (strategyId != null) {
      builder.header("X-Strategy-Id", strategyId);
    }
    return send(builder, bearer);
  }

  private HttpResult postForm(String path, String form) {
    return send(
        HttpRequest.newBuilder(uri(path))
            .header("Content-Type", "application/x-www-form-urlencoded")
            .POST(HttpRequest.BodyPublishers.ofString(form, StandardCharsets.UTF_8)),
        null);
  }

  private HttpResult send(HttpRequest.Builder builder, String bearer) {
    if (bearer != null) {
      builder.header("Authorization", "Bearer " + bearer);
    }
    try {
      HttpResponse<String> response =
          HTTP.send(builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
      return new HttpResult(response.statusCode(), response.body());
    } catch (IOException exception) {
      throw new IllegalStateException("HTTP-вызов не удался", exception);
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("HTTP-вызов прерван", exception);
    }
  }

  private URI uri(String path) {
    return URI.create("http://localhost:" + port + path);
  }

  private Instant sessionOpen(LocalDate date) {
    return dataset.tradingDays().stream()
        .filter(day -> day.date().equals(date))
        .findFirst()
        .map(day -> day.openUtc())
        .orElseThrow();
  }

  private Instant sessionClose(LocalDate date) {
    return dataset.tradingDays().stream()
        .filter(day -> day.date().equals(date))
        .findFirst()
        .map(day -> day.closeUtc())
        .orElseThrow();
  }

  private static List<String> metricNames(JsonNode deviations) {
    List<String> names = new ArrayList<>();
    for (JsonNode node : deviations) {
      names.add(node.path("metric").asText());
    }
    return names;
  }

  private static String encode(String value) {
    return URLEncoder.encode(value, StandardCharsets.UTF_8);
  }

  private void cleanAccountRows() {
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
  }

  /** Ответ API БКС с разбором JSON по запросу. */
  private record HttpResult(int status, String body) {

    private JsonNode json() {
      return JSON.readTree(body);
    }
  }

  /** Регистрирует сидинг REPLAY-набора до создания провайдера рыночных данных. */
  @TestConfiguration
  static class ReplaySeedConfiguration {

    @Bean
    static ReplaySeedSupport replaySeedSupport() {
      return new ReplaySeedSupport();
    }
  }
}
