package ru.moex.papertrader.infra.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ru.moex.papertrader.core.marketdata.TradingDay;
import ru.moex.papertrader.core.marketdata.TradingSession;
import ru.moex.papertrader.infra.model.TradingCalendarEntity;
import ru.moex.papertrader.infra.repository.TradingCalendarRepository;
import tools.jackson.databind.json.JsonMapper;

/**
 * Торговый календарь: кэш БД, дозагрузка с локального HTTP-сервера (fixture реального ответа ISS) и
 * состояние сессии.
 *
 * <p>Ответ MOEX ISS подменяется {@link HttpServer}: проверяется, что при полном кэше HTTP не
 * вызывается, а при пробеле — вызывается ровно один раз.
 */
class TradingCalendarServiceTest {

  private static final String ENGINE = "stock";
  private static final String MARKET = "shares";
  private static final String BOARD = "TQBR";
  private static final ZoneId MOSCOW = ZoneId.of("Europe/Moscow");

  private HttpServer server;
  private AtomicInteger httpRequests;
  private TradingCalendarRepository repository;
  private TradingCalendarService service;

  @BeforeEach
  void setUp() throws IOException {
    httpRequests = new AtomicInteger();
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    byte[] historyFixture = fixture();
    server.createContext(
        "/iss/history/engines/stock/markets/shares/boards/TQBR/securities/SBER.json",
        exchange -> {
          httpRequests.incrementAndGet();
          respond(exchange, historyFixture);
        });
    // Минутные свечи: запрос по дате отдаёт открытие дня, запрос с 18:00 — вечерний хвост.
    byte[] openFixture = fixture("candles_minute_open.json");
    byte[] closeFixture = fixture("candles_minute_close.json");
    server.createContext(
        "/iss/engines/stock/markets/shares/boards/TQBR/securities/SBER/candles.json",
        exchange -> {
          httpRequests.incrementAndGet();
          String query =
              java.net.URLDecoder.decode(
                  exchange.getRequestURI().getRawQuery(), StandardCharsets.UTF_8);
          respond(exchange, query.contains("18:00:00") ? closeFixture : openFixture);
        });
    server.start();
    repository = mock(TradingCalendarRepository.class);
    service =
        new TradingCalendarService(repository, properties(baseUrl()), JsonMapper.builder().build());
  }

  @AfterEach
  void tearDown() {
    if (server != null) {
      server.stop(0);
    }
  }

  @Test
  void completeCacheDoesNotCallIssAndReturnsDaysAscending() {
    when(repository.findByEngineAndMarketAndBoardAndTradeDateBetweenOrderByTradeDate(
            eq(ENGINE), eq(MARKET), eq(BOARD), any(), any()))
        .thenReturn(
            List.of(
                day(LocalDate.of(2026, 10, 28), true),
                day(LocalDate.of(2026, 10, 29), true),
                day(LocalDate.of(2026, 10, 30), true)));

    List<TradingDay> days =
        service.tradingDays(
            Instant.parse("2026-10-28T00:00:00Z"), Instant.parse("2026-10-30T20:00:00Z"));

    assertThat(days)
        .extracting(TradingDay::date)
        .containsExactly(
            LocalDate.of(2026, 10, 28), LocalDate.of(2026, 10, 29), LocalDate.of(2026, 10, 30));
    assertThat(days).allMatch(TradingDay::trading);
    assertThat(days.getFirst().openUtc()).isEqualTo(Instant.parse("2026-10-28T07:00:00Z"));
    assertThat(days.getFirst().closeUtc()).isEqualTo(Instant.parse("2026-10-28T15:50:00Z"));
    assertThat(httpRequests).hasValue(0);
    verify(repository, never()).upsert(any(), any(), any(), any(), anyBoolean(), any(), any());
  }

  @Test
  void gapTriggersIssAndIdempotentUpsertOfMissingDays() {
    when(repository.findByEngineAndMarketAndBoardAndTradeDateBetweenOrderByTradeDate(
            eq(ENGINE), eq(MARKET), eq(BOARD), any(), any()))
        .thenReturn(List.of(day(LocalDate.of(2026, 10, 28), true)));
    when(repository.upsert(any(), any(), any(), any(), anyBoolean(), any(), any())).thenReturn(1);

    List<TradingDay> days =
        service.tradingDays(
            Instant.parse("2026-10-28T00:00:00Z"), Instant.parse("2026-10-31T20:00:00Z"));

    // Один запрос истории + по два запроса свечей (открытие/закрытие) на каждый новый день.
    assertThat(httpRequests).hasValue(5);
    assertThat(days)
        .extracting(TradingDay::date)
        .containsExactly(
            LocalDate.of(2026, 10, 28),
            LocalDate.of(2026, 10, 29),
            LocalDate.of(2026, 10, 30),
            LocalDate.of(2026, 10, 31));
    assertThat(days).extracting(TradingDay::trading).containsExactly(true, true, true, false);
    // 31 октября после последней опубликованной даты ISS: не кэшируется как неторговый день.
    assertThat(days.getLast().openUtc()).isNull();
    verify(repository, times(2)).upsert(any(), any(), any(), any(), anyBoolean(), any(), any());
    // Границы сессии — из минутных свечей биржи (fixture от 2026-10-30), а не из настроек:
    // открытие 06:59 МСК -> 03:59 UTC, закрытие 23:49:59 МСК -> 20:49:59 UTC.
    verify(repository)
        .upsert(
            eq(ENGINE),
            eq(MARKET),
            eq(BOARD),
            eq(LocalDate.of(2026, 10, 30)),
            eq(true),
            eq(Instant.parse("2026-10-30T03:59:00Z")),
            eq(Instant.parse("2026-10-30T20:49:59Z")));
  }

  @Test
  void candlesUnavailableFallsBackToConfiguredSessionWindow() throws IOException {
    // Свечи не отдаём (404 на запрос candles.json) — границы берутся из настроек.
    HttpServer historyOnly = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    byte[] historyFixture = fixture();
    AtomicInteger requests = new AtomicInteger();
    historyOnly.createContext(
        "/iss/history/engines/stock/markets/shares/boards/TQBR/securities/SBER.json",
        exchange -> {
          requests.incrementAndGet();
          respond(exchange, historyFixture);
        });
    historyOnly.start();
    try {
      TradingCalendarService fallbackService =
          new TradingCalendarService(
              repository,
              properties("http://127.0.0.1:" + historyOnly.getAddress().getPort()),
              JsonMapper.builder().build());
      when(repository.findByEngineAndMarketAndBoardAndTradeDateBetweenOrderByTradeDate(
              eq(ENGINE), eq(MARKET), eq(BOARD), any(), any()))
          .thenReturn(List.of(day(LocalDate.of(2026, 10, 28), true)));
      when(repository.upsert(any(), any(), any(), any(), anyBoolean(), any(), any())).thenReturn(1);

      List<TradingDay> days =
          fallbackService.tradingDays(
              Instant.parse("2026-10-28T00:00:00Z"), Instant.parse("2026-10-30T20:00:00Z"));

      assertThat(days)
          .extracting(TradingDay::date)
          .containsExactly(
              LocalDate.of(2026, 10, 28), LocalDate.of(2026, 10, 29), LocalDate.of(2026, 10, 30));
      // Fallback-окно настроек: 10:00–18:50 МСК -> 07:00–15:50 UTC.
      verify(repository)
          .upsert(
              eq(ENGINE),
              eq(MARKET),
              eq(BOARD),
              eq(LocalDate.of(2026, 10, 29)),
              eq(true),
              eq(Instant.parse("2026-10-29T07:00:00Z")),
              eq(Instant.parse("2026-10-29T15:50:00Z")));
    } finally {
      historyOnly.stop(0);
    }
  }

  @Test
  void networkFailureReturnsCachedDaysWithoutException() {
    when(repository.findByEngineAndMarketAndBoardAndTradeDateBetweenOrderByTradeDate(
            eq(ENGINE), eq(MARKET), eq(BOARD), any(), any()))
        .thenReturn(List.of(day(LocalDate.of(2026, 10, 28), true)));
    TradingCalendarService offline =
        new TradingCalendarService(
            repository, properties("http://127.0.0.1:1"), JsonMapper.builder().build());

    List<TradingDay> days =
        offline.tradingDays(
            Instant.parse("2026-10-28T00:00:00Z"), Instant.parse("2026-10-29T20:00:00Z"));

    assertThat(days).hasSize(1);
    assertThat(days.getFirst().date()).isEqualTo(LocalDate.of(2026, 10, 28));
  }

  @Test
  void tradingSessionIsOpenInsideMainSession() {
    LocalDate date = LocalDate.of(2026, 10, 28);
    when(repository.findByEngineAndMarketAndBoardAndTradeDate(
            eq(ENGINE), eq(MARKET), eq(BOARD), eq(date)))
        .thenReturn(Optional.of(day(date, true)));

    TradingSession session = service.tradingSession(Instant.parse("2026-10-28T10:00:00Z"));

    assertThat(session.open()).isTrue();
    assertThat(session.id()).isEqualTo(1);
    assertThat(session.name()).isEqualTo("Основная сессия");
    assertThat(session.nextSessionAt()).isEqualTo(Instant.parse("2026-10-28T15:50:00Z"));
  }

  @Test
  void tradingSessionBeforeOpenPointsToTodayOpen() {
    LocalDate date = LocalDate.of(2026, 10, 28);
    when(repository.findByEngineAndMarketAndBoardAndTradeDate(
            eq(ENGINE), eq(MARKET), eq(BOARD), eq(date)))
        .thenReturn(Optional.of(day(date, true)));

    TradingSession session = service.tradingSession(Instant.parse("2026-10-28T06:00:00Z"));

    assertThat(session.open()).isFalse();
    assertThat(session.nextSessionAt()).isEqualTo(Instant.parse("2026-10-28T07:00:00Z"));
  }

  @Test
  void tradingSessionAfterClosePointsToNextTradingDay() {
    LocalDate date = LocalDate.of(2026, 10, 28);
    LocalDate next = LocalDate.of(2026, 10, 29);
    when(repository.findByEngineAndMarketAndBoardAndTradeDate(
            eq(ENGINE), eq(MARKET), eq(BOARD), eq(date)))
        .thenReturn(Optional.of(day(date, true)));
    when(repository
            .findTopByEngineAndMarketAndBoardAndTradeDateGreaterThanAndTradingTrueOrderByTradeDateAsc(
                eq(ENGINE), eq(MARKET), eq(BOARD), eq(date)))
        .thenReturn(Optional.of(day(next, true)));

    TradingSession session = service.tradingSession(Instant.parse("2026-10-28T16:00:00Z"));

    assertThat(session.open()).isFalse();
    assertThat(session.nextSessionAt()).isEqualTo(Instant.parse("2026-10-29T07:00:00Z"));
  }

  @Test
  void nonTradingDayPointsToNextTradingDay() {
    LocalDate saturday = LocalDate.of(2026, 10, 31);
    LocalDate monday = LocalDate.of(2026, 11, 2);
    when(repository.findByEngineAndMarketAndBoardAndTradeDate(
            eq(ENGINE), eq(MARKET), eq(BOARD), eq(saturday)))
        .thenReturn(Optional.of(day(saturday, false)));
    when(repository
            .findTopByEngineAndMarketAndBoardAndTradeDateGreaterThanAndTradingTrueOrderByTradeDateAsc(
                eq(ENGINE), eq(MARKET), eq(BOARD), eq(saturday)))
        .thenReturn(Optional.of(day(monday, true)));

    TradingSession session = service.tradingSession(Instant.parse("2026-10-31T10:00:00Z"));

    assertThat(session.open()).isFalse();
    assertThat(session.nextSessionAt()).isEqualTo(Instant.parse("2026-11-02T07:00:00Z"));
  }

  @Test
  void emptyCalendarReturnsClosedSessionWithoutNextSession() {
    Instant at = Instant.parse("2026-11-02T10:00:00Z");
    when(repository.findByEngineAndMarketAndBoardAndTradeDate(
            eq(ENGINE), eq(MARKET), eq(BOARD), eq(LocalDate.of(2026, 11, 2))))
        .thenReturn(Optional.empty());
    when(repository
            .findTopByEngineAndMarketAndBoardAndTradeDateGreaterThanAndTradingTrueOrderByTradeDateAsc(
                eq(ENGINE), eq(MARKET), eq(BOARD), eq(LocalDate.of(2026, 11, 2))))
        .thenReturn(Optional.empty());

    TradingSession session = service.tradingSession(at);

    assertThat(session.open()).isFalse();
    assertThat(session.nextSessionAt()).isEqualTo(at);
  }

  private String baseUrl() {
    return "http://127.0.0.1:" + server.getAddress().getPort();
  }

  private static MarketDataProperties properties(String baseUrl) {
    return new MarketDataProperties(
        7,
        ENGINE,
        MARKET,
        BOARD,
        "SBER",
        baseUrl,
        500,
        1_000,
        LocalTime.of(10, 0),
        LocalTime.of(18, 50));
  }

  private static TradingCalendarEntity day(LocalDate date, boolean trading) {
    if (!trading) {
      return new TradingCalendarEntity(ENGINE, MARKET, BOARD, date, false, null, null);
    }
    Instant open = date.atTime(10, 0).atZone(MOSCOW).toInstant();
    Instant close = date.atTime(18, 50).atZone(MOSCOW).toInstant();
    return new TradingCalendarEntity(ENGINE, MARKET, BOARD, date, true, open, close);
  }

  private static void respond(com.sun.net.httpserver.HttpExchange exchange, byte[] body)
      throws IOException {
    exchange.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");
    exchange.sendResponseHeaders(200, body.length);
    try (OutputStream out = exchange.getResponseBody()) {
      out.write(body);
    }
  }

  private static byte[] fixture() throws IOException {
    return fixture("trading-schedule.json");
  }

  private static byte[] fixture(String name) throws IOException {
    try (InputStream stream =
        TradingCalendarServiceTest.class.getResourceAsStream("/fixtures/iss/" + name)) {
      assertThat(stream).as("fixture ответа ISS: %s", name).isNotNull();
      return stream.readAllBytes();
    }
  }
}
