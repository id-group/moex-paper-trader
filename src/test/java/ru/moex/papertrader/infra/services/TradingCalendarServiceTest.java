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
import java.util.regex.Pattern;
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

  /**
   * ISS распознаёт {@code from} только как дату или дату-время («yyyy-MM-dd[ 18:00:00]») по Москве.
   */
  private static final Pattern ISS_FROM = Pattern.compile("\\d{4}-\\d{2}-\\d{2}( 18:00:00)?");

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
    // Фикстуры (реальный ответ ISS) — для 2026-10-30; для 2026-11-02 синтезируем бары той же
    // даты, чтобы границы дня соответствовали запрошенной дате (сервис такое проверяет).
    // Остальные даты отдаём без свечей.
    byte[] openFixture = fixture("candles_minute_open.json");
    byte[] closeFixture = fixture("candles_minute_close.json");
    byte[] emptyCandles =
        "{\"candles\":{\"columns\":[\"begin\",\"end\"],\"data\":[]}}"
            .getBytes(StandardCharsets.UTF_8);
    server.createContext(
        "/iss/engines/stock/markets/shares/boards/TQBR/securities/SBER/candles.json",
        exchange -> {
          httpRequests.incrementAndGet();
          String from = issFromParameter(exchange);
          if (!ISS_FROM.matcher(from).matches()) {
            respond(exchange, foreignDayCandles());
            return;
          }
          boolean evening = from.endsWith("18:00:00");
          String date = from.substring(0, 10);
          if (date.equals("2026-10-30")) {
            respond(exchange, evening ? closeFixture : openFixture);
          } else if (date.equals("2026-11-02")) {
            String day = "2026-11-02";
            String bars =
                evening
                    ? ("{\"candles\":{\"columns\":[\"begin\",\"end\"],\"data\":[[\""
                        + day
                        + " 23:49:00\",\""
                        + day
                        + " 23:49:59\"]]}}")
                    : ("{\"candles\":{\"columns\":[\"begin\",\"end\"],\"data\":[[\""
                        + day
                        + " 09:59:00\",\""
                        + day
                        + " 09:59:59\"]]}}");
            respond(exchange, bars.getBytes(StandardCharsets.UTF_8));
          } else {
            respond(exchange, emptyCandles);
          }
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

    // Один запрос истории + по два запроса свечей (открытие/закрытие) на 29-е, 30-е
    // и живую проверку 31-го (после последней даты истории ISS).
    assertThat(httpRequests).hasValue(7);
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
  void liveDayWithOutOfRangeCandlesIsNotCached() throws IOException {
    // Регресс прогона 2026-11-14: ISS отдал бар за пределами запрошенного окна, close уехал
    // на месяц вперёд. Такие границы некорректны: день не кэшируется, статус дня решает
    // fallback-окно настроек.
    HttpServer corrupt = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    byte[] historyFixture = fixture();
    AtomicInteger requests = new AtomicInteger();
    corrupt.createContext(
        "/iss/history/engines/stock/markets/shares/boards/TQBR/securities/SBER.json",
        exchange -> {
          requests.incrementAndGet();
          respond(exchange, historyFixture);
        });
    byte[] openCandles =
        "{\"candles\":{\"columns\":[\"begin\",\"end\"],\"data\":[[\"2026-11-02 09:59:00\",\"2026-11-02 10:00:00\"]]}}"
            .getBytes(StandardCharsets.UTF_8);
    byte[] futureCloseCandles =
        "{\"candles\":{\"columns\":[\"begin\",\"end\"],\"data\":[[\"2026-12-15 17:18:00\",\"2026-12-15 17:19:59\"]]}}"
            .getBytes(StandardCharsets.UTF_8);
    corrupt.createContext(
        "/iss/engines/stock/markets/shares/boards/TQBR/securities/SBER/candles.json",
        exchange -> {
          requests.incrementAndGet();
          respond(exchange, issEveningRequest(exchange) ? futureCloseCandles : openCandles);
        });
    corrupt.start();
    try {
      TradingCalendarService guarded =
          new TradingCalendarService(
              repository,
              properties("http://127.0.0.1:" + corrupt.getAddress().getPort()),
              JsonMapper.builder().build());
      when(repository.findByEngineAndMarketAndBoardAndTradeDateBetweenOrderByTradeDate(
              eq(ENGINE), eq(MARKET), eq(BOARD), any(), any()))
          .thenReturn(List.of());
      when(repository.upsert(any(), any(), any(), any(), anyBoolean(), any(), any())).thenReturn(1);

      List<TradingDay> days =
          guarded.tradingDays(
              Instant.parse("2026-11-02T00:00:00Z"), Instant.parse("2026-11-02T20:00:00Z"));

      assertThat(days).extracting(TradingDay::trading).containsExactly(false);
      verify(repository, never()).upsert(any(), any(), any(), any(), anyBoolean(), any(), any());
    } finally {
      corrupt.stop(0);
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
  void unknownWeekdayInsideConfiguredWindowReturnsOpenFallbackSession() {
    // Пустой календарь (LIVE до первой синхронизации): будний день внутри окна настроек открыт.
    TradingSession session = service.tradingSession(Instant.parse("2026-11-02T10:00:00Z"));

    assertThat(session.open()).isTrue();
    assertThat(session.id()).isEqualTo(1);
    assertThat(session.nextSessionAt()).isEqualTo(Instant.parse("2026-11-02T15:50:00Z"));
  }

  @Test
  void unknownWeekdayBeforeOpenPointsToConfiguredOpen() {
    TradingSession session = service.tradingSession(Instant.parse("2026-11-02T03:00:00Z"));

    assertThat(session.open()).isFalse();
    assertThat(session.nextSessionAt()).isEqualTo(Instant.parse("2026-11-02T07:00:00Z"));
  }

  @Test
  void unknownWeekdayAfterClosePointsToNextWeekdayOpen() {
    TradingSession session = service.tradingSession(Instant.parse("2026-11-02T17:00:00Z"));

    assertThat(session.open()).isFalse();
    assertThat(session.nextSessionAt()).isEqualTo(Instant.parse("2026-11-03T07:00:00Z"));
  }

  @Test
  void unknownWeekendReturnsClosedSessionWithNextWeekdayOpen() {
    TradingSession session = service.tradingSession(Instant.parse("2026-10-31T10:00:00Z"));

    assertThat(session.open()).isFalse();
    assertThat(session.nextSessionAt()).isEqualTo(Instant.parse("2026-11-02T07:00:00Z"));
  }

  @Test
  void dayAfterLastTradedDateWithLiveCandlesIsCachedAsTrading() {
    // 2-е позже последней даты истории ISS, но минутные свечи дня уже существуют
    // (торги идут): день кэшируется как торговый с границами из свечей.
    when(repository.findByEngineAndMarketAndBoardAndTradeDateBetweenOrderByTradeDate(
            eq(ENGINE), eq(MARKET), eq(BOARD), any(), any()))
        .thenReturn(List.of());
    when(repository.upsert(any(), any(), any(), any(), anyBoolean(), any(), any())).thenReturn(1);

    List<TradingDay> days =
        service.tradingDays(
            Instant.parse("2026-11-02T00:00:00Z"), Instant.parse("2026-11-02T20:00:00Z"));

    assertThat(days).extracting(TradingDay::trading).containsExactly(true);
    // Границы из синтезированных свечей дня: 09:59 МСК -> 06:59 UTC, 23:49:59 МСК -> 20:49:59 UTC.
    verify(repository)
        .upsert(
            eq(ENGINE),
            eq(MARKET),
            eq(BOARD),
            eq(LocalDate.of(2026, 11, 2)),
            eq(true),
            eq(Instant.parse("2026-11-02T06:59:00Z")),
            eq(Instant.parse("2026-11-02T20:49:59Z")));
  }

  @Test
  void dayAfterLastTradedDateWithoutCandlesIsNotCached() {
    // Будущие дни (сб/вс) без свечей: не кэшируются — статус дня решает fallback-окно.
    when(repository.findByEngineAndMarketAndBoardAndTradeDateBetweenOrderByTradeDate(
            eq(ENGINE), eq(MARKET), eq(BOARD), any(), any()))
        .thenReturn(List.of());
    when(repository.upsert(any(), any(), any(), any(), anyBoolean(), any(), any())).thenReturn(1);

    List<TradingDay> days =
        service.tradingDays(
            Instant.parse("2026-10-31T00:00:00Z"), Instant.parse("2026-11-01T20:00:00Z"));

    assertThat(days).extracting(TradingDay::trading).containsExactly(false, false);
    verify(repository, never()).upsert(any(), any(), any(), any(), anyBoolean(), any(), any());
  }

  @Test
  void liveDayBeforeEveningChunkIsCachedWithExchangeOpenAndPlannedClose() throws IOException {
    // Текущий день торгов идёт, но вечернего фрагмента (запрос с 18:00 МСК) ещё нет.
    // День обязан быть закэширован сразу: открытие — из минутных свечей биржи, закрытие —
    // плановое из настроек. Иначе день остаётся неизвестным и tradingSession() всю первую
    // половину дня отдаёт fallback-окно настроек вместо биржевого (найденный дефект).
    HttpServer morningOnly =
        servingCandles(
            "2026-11-19 06:59:00|2026-11-19 06:59:59", // открытие дня: первый бар
            null); // вечерних торгов ещё не было
    try {
      when(repository.findByEngineAndMarketAndBoardAndTradeDateBetweenOrderByTradeDate(
              eq(ENGINE), eq(MARKET), eq(BOARD), any(), any()))
          .thenReturn(List.of());
      when(repository.upsert(any(), any(), any(), any(), anyBoolean(), any(), any())).thenReturn(1);
      TradingCalendarService live = serviceAt(morningOnly);

      List<TradingDay> days =
          live.tradingDays(
              Instant.parse("2026-11-19T00:00:00Z"), Instant.parse("2026-11-19T20:00:00Z"));

      assertThat(days).extracting(TradingDay::trading).containsExactly(true);
      // Открытие биржевое: 06:59 МСК -> 03:59 UTC. Закрытие плановое: 18:50 МСК -> 15:50 UTC.
      verify(repository)
          .upsert(
              eq(ENGINE),
              eq(MARKET),
              eq(BOARD),
              eq(LocalDate.of(2026, 11, 19)),
              eq(true),
              eq(Instant.parse("2026-11-19T03:59:00Z")),
              eq(Instant.parse("2026-11-19T15:50:00Z")));
    } finally {
      morningOnly.stop(0);
    }
  }

  @Test
  void refreshLiveBoundsAdoptsEveningCloseOnceItAppears() throws IOException {
    // День закэширован утром (закрытие плановое). Периодический refresh обязан подтянуть
    // фактическое закрытие биржи, не дожидаясь перезапуска приложения.
    HttpServer withEvening =
        servingCandles(
            "2026-11-19 06:59:00|2026-11-19 06:59:59", "2026-11-19 23:49:00|2026-11-19 23:49:59");
    try {
      LocalDate date = LocalDate.of(2026, 11, 19);
      when(repository.findByEngineAndMarketAndBoardAndTradeDate(
              eq(ENGINE), eq(MARKET), eq(BOARD), eq(date)))
          .thenReturn(Optional.of(day(date, true)));
      when(repository.upsert(any(), any(), any(), any(), anyBoolean(), any(), any())).thenReturn(1);

      serviceAt(withEvening).refreshLiveBounds(Instant.parse("2026-11-19T16:30:00Z"));

      verify(repository)
          .upsert(
              eq(ENGINE),
              eq(MARKET),
              eq(BOARD),
              eq(date),
              eq(true),
              eq(Instant.parse("2026-11-19T03:59:00Z")),
              eq(Instant.parse("2026-11-19T20:49:59Z")));
    } finally {
      withEvening.stop(0);
    }
  }

  @Test
  void refreshLiveBoundsKeepsCachedCloseWhenEveningChunkIsGone() throws IOException {
    // Фактическое закрытие уже в кэше, а биржа вечером ничего не отдала (сбой источника).
    // Сужать день нельзя: закэшированные границы сохраняются, записи в БД нет.
    HttpServer morningOnly = servingCandles("2026-11-19 06:59:00|2026-11-19 06:59:59", null);
    try {
      LocalDate date = LocalDate.of(2026, 11, 19);
      Instant cachedOpen = Instant.parse("2026-11-19T03:59:00Z");
      Instant actualClose = Instant.parse("2026-11-19T20:49:59Z");
      when(repository.findByEngineAndMarketAndBoardAndTradeDate(
              eq(ENGINE), eq(MARKET), eq(BOARD), eq(date)))
          .thenReturn(
              Optional.of(
                  new TradingCalendarEntity(
                      ENGINE, MARKET, BOARD, date, true, cachedOpen, actualClose)));
      when(repository.upsert(any(), any(), any(), any(), anyBoolean(), any(), any())).thenReturn(1);

      serviceAt(morningOnly).refreshLiveBounds(Instant.parse("2026-11-19T17:00:00Z"));

      verify(repository, never()).upsert(any(), any(), any(), any(), anyBoolean(), any(), any());
    } finally {
      morningOnly.stop(0);
    }
  }

  @Test
  void refreshLiveBoundsRepairsStaleFallbackCloseOfKnownDay() throws IOException {
    // Найденный дефект: дни 11–18.11 записаны с fallback-окном настроек, а tradingDays()
    // известные дни пропускает — граница застыла бы навсегда. Refresh переписывает её на
    // биржевые свечи, когда вечерний фрагмент получен.
    HttpServer withEvening =
        servingCandles(
            "2026-11-18 06:59:00|2026-11-18 06:59:59", "2026-11-18 23:49:00|2026-11-18 23:49:59");
    try {
      LocalDate date = LocalDate.of(2026, 11, 18);
      when(repository.findByEngineAndMarketAndBoardAndTradeDate(
              eq(ENGINE), eq(MARKET), eq(BOARD), eq(date)))
          .thenReturn(Optional.of(day(date, true))); // 10:00–18:50 из настроек
      when(repository.upsert(any(), any(), any(), any(), anyBoolean(), any(), any())).thenReturn(1);

      serviceAt(withEvening).refreshLiveBounds(Instant.parse("2026-11-18T17:00:00Z"));

      verify(repository)
          .upsert(
              eq(ENGINE),
              eq(MARKET),
              eq(BOARD),
              eq(date),
              eq(true),
              eq(Instant.parse("2026-11-18T03:59:00Z")),
              eq(Instant.parse("2026-11-18T20:49:59Z")));
    } finally {
      withEvening.stop(0);
    }
  }

  @Test
  void refreshLiveBoundsOnNonTradingDayDoesNotWrite() throws IOException {
    // Выходной: свечей нет, писать в календарь нечего.
    HttpServer empty = servingCandles(null, null);
    try {
      when(repository.upsert(any(), any(), any(), any(), anyBoolean(), any(), any())).thenReturn(1);

      serviceAt(empty).refreshLiveBounds(Instant.parse("2026-11-21T10:00:00Z"));

      verify(repository, never()).upsert(any(), any(), any(), any(), anyBoolean(), any(), any());
    } finally {
      empty.stop(0);
    }
  }

  @Test
  void refreshLiveBoundsKeepsPlannedCloseWhileDayIsStillTrading() throws IOException {
    // День идёт: вечерний фрагмент уже есть, но он неполный (последний бар — 19:29 МСК).
    // Фактическое закрытие можно принять только после планового закрытия дня, иначе торговое
    // окно сузится и исполнение встанет вечером. Открытие при этом берётся биржевое.
    HttpServer partialEvening =
        servingCandles(
            "2026-11-19 06:59:00|2026-11-19 06:59:59", "2026-11-19 19:29:00|2026-11-19 19:29:59");
    try {
      LocalDate date = LocalDate.of(2026, 11, 19);
      when(repository.findByEngineAndMarketAndBoardAndTradeDate(
              eq(ENGINE), eq(MARKET), eq(BOARD), eq(date)))
          .thenReturn(Optional.of(day(date, true)));
      when(repository.upsert(any(), any(), any(), any(), anyBoolean(), any(), any())).thenReturn(1);

      serviceAt(partialEvening).refreshLiveBounds(Instant.parse("2026-11-19T09:00:00Z"));

      verify(repository)
          .upsert(
              eq(ENGINE),
              eq(MARKET),
              eq(BOARD),
              eq(date),
              eq(true),
              eq(Instant.parse("2026-11-19T03:59:00Z")),
              eq(Instant.parse("2026-11-19T15:50:00Z")));
    } finally {
      partialEvening.stop(0);
    }
  }

  @Test
  void emptyCalendarUsesTheFullExchangeDayOfDefaultSettings() {
    // Найденный дефект: в LIVE с пустым календарём (до первой синхронизации) fallback-окно
    // настроек 10:00–18:50 МСК удерживало заявки вне него. Значения по умолчанию обязаны
    // покрывать весь торговый день TQBR: 07:00–23:50 МСК.
    TradingCalendarService live =
        new TradingCalendarService(
            repository, MarketDataProperties.defaults(), JsonMapper.builder().build());

    // 08:00 МСК — утро биржевого дня, 20:00 МСК — вечер биржевого дня.
    assertThat(live.tradingSession(Instant.parse("2026-11-19T05:00:00Z")).open()).isTrue();
    assertThat(live.tradingSession(Instant.parse("2026-11-19T17:00:00Z")).open()).isTrue();
    // 06:00 МСК — до открытия.
    assertThat(live.tradingSession(Instant.parse("2026-11-19T03:00:00Z")).open()).isFalse();
  }

  @Test
  void candlesRequestEncodesFromParameterExactlyOnce() throws IOException {
    // Дефект: RestClient.uri(String) видит строку как URI-шаблон и кодирует её повторно, поэтому
    // from=2026-11-18 18:00:00 уходил как from=...%252018:00:00. Биржа такую дату не распознаёт и
    // отвечает барами чужого дня — границы сессии не записывались вовсе (тихий ноль записей).
    List<String> rawQueries = java.util.Collections.synchronizedList(new java.util.ArrayList<>());
    HttpServer echoing = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    byte[] historyFixture = fixture();
    echoing.createContext(
        "/iss/history/engines/stock/markets/shares/boards/TQBR/securities/SBER.json",
        exchange -> respond(exchange, historyFixture));
    echoing.createContext(
        "/iss/engines/stock/markets/shares/boards/TQBR/securities/SBER/candles.json",
        exchange -> {
          rawQueries.add(exchange.getRequestURI().getRawQuery());
          respond(exchange, candles("2026-11-19 06:59:00|2026-11-19 06:59:59"));
        });
    echoing.start();
    try {
      serviceAt(echoing).refreshLiveBounds(Instant.parse("2026-11-19T17:00:00Z"));

      assertThat(rawQueries).isNotEmpty();
      assertThat(rawQueries).allSatisfy(query -> assertThat(query).doesNotContain("%25"));
      assertThat(rawQueries)
          .anySatisfy(query -> assertThat(query).contains("from=2026-11-19%2018:00:00"));
    } finally {
      echoing.stop(0);
    }
  }

  @Test
  void refreshLiveBoundsInsertsUncachedTradingDayFromExchangeBounds() throws IOException {
    // Ветка вставки нового дня: в календаре дня нет (например, день не попал в synchronize),
    // а биржа отдаёт его свечи — refresh обязан записать день с границами биржи.
    HttpServer withEvening =
        servingCandles(
            "2026-11-19 06:59:00|2026-11-19 06:59:59", "2026-11-19 23:49:00|2026-11-19 23:49:59");
    try {
      LocalDate date = LocalDate.of(2026, 11, 19);
      when(repository.findByEngineAndMarketAndBoardAndTradeDate(
              eq(ENGINE), eq(MARKET), eq(BOARD), eq(date)))
          .thenReturn(Optional.empty());
      when(repository.upsert(any(), any(), any(), any(), anyBoolean(), any(), any())).thenReturn(1);

      serviceAt(withEvening).refreshLiveBounds(Instant.parse("2026-11-19T16:30:00Z"));

      verify(repository)
          .upsert(
              eq(ENGINE),
              eq(MARKET),
              eq(BOARD),
              eq(date),
              eq(true),
              eq(Instant.parse("2026-11-19T03:59:00Z")),
              eq(Instant.parse("2026-11-19T20:49:59Z")));
    } finally {
      withEvening.stop(0);
    }
  }

  @Test
  void refreshLiveBoundsIgnoresEveningChunkOfForeignDay() throws IOException {
    // Вечерний хвост вышел за календарные сутки МСК запрошенного дня: такой ответ биржи
    // отбраковывается целиком (guard в fetchCandleBounds), некорректная граница не попадает в БД.
    HttpServer foreignEvening =
        servingCandles(
            "2026-11-19 06:59:00|2026-11-19 06:59:59", "2026-11-20 00:10:00|2026-11-20 00:10:59");
    try {
      LocalDate date = LocalDate.of(2026, 11, 19);
      when(repository.findByEngineAndMarketAndBoardAndTradeDate(
              eq(ENGINE), eq(MARKET), eq(BOARD), eq(date)))
          .thenReturn(Optional.of(day(date, true)));
      when(repository.upsert(any(), any(), any(), any(), anyBoolean(), any(), any())).thenReturn(1);

      serviceAt(foreignEvening).refreshLiveBounds(Instant.parse("2026-11-19T21:00:00Z"));

      verify(repository, never()).upsert(any(), any(), any(), any(), anyBoolean(), any(), any());
    } finally {
      foreignEvening.stop(0);
    }
  }

  @Test
  void refreshLiveBoundsKeepsCachedBoundsOutsideCalendarDayUntouched() throws IOException {
    // Некорректная граница уже в кэше (запись сделана вне этих прогонов), а вечернего хвоста нет:
    // закрытие дня — плановое, расширенное до закэшированного, поэтому оно остаётся за сутками МСК.
    // Итоговая проверка границ перед записью (guard в refreshBounds) обязана отказать, а не
    // переписать день тем же некорректным значением.
    HttpServer morningOnly = servingCandles("2026-11-19 06:59:00|2026-11-19 06:59:59", null);
    try {
      LocalDate date = LocalDate.of(2026, 11, 19);
      Instant bogusClose = date.plusDays(1).atTime(0, 30).atZone(MOSCOW).toInstant();
      when(repository.findByEngineAndMarketAndBoardAndTradeDate(
              eq(ENGINE), eq(MARKET), eq(BOARD), eq(date)))
          .thenReturn(
              Optional.of(
                  new TradingCalendarEntity(
                      ENGINE,
                      MARKET,
                      BOARD,
                      date,
                      true,
                      Instant.parse("2026-11-19T07:00:00Z"),
                      bogusClose)));
      when(repository.upsert(any(), any(), any(), any(), anyBoolean(), any(), any())).thenReturn(1);

      serviceAt(morningOnly).refreshLiveBounds(Instant.parse("2026-11-19T15:00:00Z"));

      verify(repository, never()).upsert(any(), any(), any(), any(), anyBoolean(), any(), any());
    } finally {
      morningOnly.stop(0);
    }
  }

  /**
   * Значение {@code from} ровно так, как его понимает ISS: одно декодирование percent-encoding.
   * Запрос с двойным кодированием ({@code %2520}) ISS не распознаёт и отвечает барами другой даты —
   * двойники ведут так же, иначе тесты не увидят дефект.
   */
  private static String issFromParameter(com.sun.net.httpserver.HttpExchange exchange) {
    String raw = exchange.getRequestURI().getRawQuery();
    for (String pair : (raw == null ? "" : raw).split("&")) {
      if (pair.startsWith("from=")) {
        return java.net.URLDecoder.decode(pair.substring("from=".length()), StandardCharsets.UTF_8);
      }
    }
    return "";
  }

  /** Вечерний запрос дня: {@code from} распознан как «yyyy-MM-dd 18:00:00» по Москве. */
  private static boolean issEveningRequest(com.sun.net.httpserver.HttpExchange exchange) {
    String from = issFromParameter(exchange);
    return ISS_FROM.matcher(from).matches() && from.endsWith("18:00:00");
  }

  /**
   * Бары вне запрошенного дня — ответ ISS на нераспознанный {@code from} (замерено на живом ISS).
   */
  private static byte[] foreignDayCandles() {
    String body =
        "{\"candles\":{\"columns\":[\"begin\",\"end\"],\"data\":[["
            + "\"2011-12-15 10:00:00\",\"2011-12-15 10:00:59\"]]}}";
    return body.getBytes(StandardCharsets.UTF_8);
  }

  /**
   * Локальный ISS: история — фикстура, минутные свечи — переданные бары. {@code openBar} отвечает
   * на запрос по дате, {@code closeBar} — на вечерний запрос с 18:00 МСК; {@code null} означает
   * пустой ответ свечей.
   */
  private static HttpServer servingCandles(String openBar, String closeBar) throws IOException {
    HttpServer issued = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    byte[] historyFixture = fixture();
    issued.createContext(
        "/iss/history/engines/stock/markets/shares/boards/TQBR/securities/SBER.json",
        exchange -> respond(exchange, historyFixture));
    issued.createContext(
        "/iss/engines/stock/markets/shares/boards/TQBR/securities/SBER/candles.json",
        exchange -> {
          String from = issFromParameter(exchange);
          if (!ISS_FROM.matcher(from).matches()) {
            // from не распознан (в частности, при двойном кодировании) — бары чужого дня.
            respond(exchange, foreignDayCandles());
            return;
          }
          respond(exchange, candles(from.endsWith("18:00:00") ? closeBar : openBar));
        });
    issued.start();
    return issued;
  }

  /** Тело ответа candles.json по одному бару «begin|end»; {@code null} — ноль свечей. */
  private static byte[] candles(String bar) {
    String data = bar == null ? "[]" : "[[\"" + bar.replace("|", "\",\"") + "\"]]";
    return ("{\"candles\":{\"columns\":[\"begin\",\"end\"],\"data\":" + data + "}}")
        .getBytes(StandardCharsets.UTF_8);
  }

  private TradingCalendarService serviceAt(HttpServer issued) {
    return new TradingCalendarService(
        repository,
        properties("http://127.0.0.1:" + issued.getAddress().getPort()),
        JsonMapper.builder().build());
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
