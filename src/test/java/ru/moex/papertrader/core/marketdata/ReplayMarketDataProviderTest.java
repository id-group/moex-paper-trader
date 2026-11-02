package ru.moex.papertrader.core.marketdata;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import ru.moex.papertrader.TestInstruments;
import ru.moex.papertrader.core.domain.Instrument;

class ReplayMarketDataProviderTest {

  private static final Instant T0 = Instant.parse("2026-11-02T07:00:00Z");
  private static final Instrument SBER = TestInstruments.sber();
  private static final Instrument GAZP = TestInstruments.gazp();

  @Test
  void quoteShowsOnlySnapshotsUpToCurrentVirtualInstant() {
    VirtualClock clock = new VirtualClock(T0);
    ReplayMarketDataProvider provider = provider(clock, timeline(), calendar());

    assertThat(provider.quote(SBER))
        .get()
        .extracting(Quote::last)
        .isEqualTo(new BigDecimal("100.00"));

    clock.advance(Duration.ofMinutes(10));

    assertThat(provider.quote(SBER))
        .get()
        .extracting(Quote::last)
        .isEqualTo(new BigDecimal("101.00"));
  }

  @Test
  void latestSnapshotIsChosenByTimestampRegardlessOfInputOrder() {
    List<MarketDataSnapshot> unsorted =
        List.of(
            new QuoteSnapshot(T0.plusSeconds(60), quote(T0.plusSeconds(60), "101.00")),
            new QuoteSnapshot(T0, quote(T0, "100.00")));
    VirtualClock clock = new VirtualClock(T0.plusSeconds(120));
    ReplayMarketDataProvider provider = provider(clock, unsorted, calendar());

    assertThat(provider.quote(SBER))
        .get()
        .extracting(Quote::last)
        .isEqualTo(new BigDecimal("101.00"));
  }

  @Test
  void tradesAreReturnedInAscendingTimeOrder() {
    VirtualClock clock = new VirtualClock(T0.plus(Duration.ofHours(2)));
    ReplayMarketDataProvider provider = provider(clock, timeline(), calendar());

    List<Trade> trades = provider.trades(SBER, T0.minusSeconds(3600), T0.plus(Duration.ofHours(2)));

    assertThat(trades).extracting(Trade::ts).isSorted();
    assertThat(trades).extracting(Trade::quantity).containsExactly(10L, 5L, 3L);
  }

  @Test
  void tradesRespectInclusiveBoundaries() {
    List<MarketDataSnapshot> onlyTrades =
        List.of(
            tradeEvent(T0.minusSeconds(1), "99.00", 1),
            tradeEvent(T0, "100.00", 10),
            tradeEvent(T0.plusSeconds(30), "102.00", 5),
            tradeEvent(T0.plusSeconds(31), "103.00", 2));
    VirtualClock clock = new VirtualClock(T0.plusSeconds(3600));
    ReplayMarketDataProvider provider = provider(clock, onlyTrades, calendar());

    List<Trade> trades = provider.trades(SBER, T0, T0.plusSeconds(30));

    assertThat(trades).extracting(Trade::quantity).containsExactly(10L, 5L);
  }

  @Test
  void futureTradesAreInvisibleUntilClockAdvances() {
    List<MarketDataSnapshot> onlyTrades = List.of(tradeEvent(T0.plusSeconds(600), "100.00", 10));
    VirtualClock clock = new VirtualClock(T0);
    ReplayMarketDataProvider provider = provider(clock, onlyTrades, calendar());

    assertThat(provider.trades(SBER, T0, T0.plus(Duration.ofHours(2)))).isEmpty();

    clock.advance(Duration.ofMinutes(10));

    assertThat(provider.trades(SBER, T0, T0.plus(Duration.ofHours(2)))).hasSize(1);
  }

  @Test
  void candlesAggregateTradesIntoTimeFrameWindows() {
    List<MarketDataSnapshot> onlyTrades =
        List.of(
            tradeEvent(T0, "100.00", 10),
            tradeEvent(T0.plusSeconds(30), "102.00", 5),
            tradeEvent(T0.plusSeconds(90), "99.00", 3));
    VirtualClock clock = new VirtualClock(T0.plus(Duration.ofHours(1)));
    ReplayMarketDataProvider provider = provider(clock, onlyTrades, calendar());

    CandleSeries series = provider.candles(SBER, T0, T0.plusSeconds(120), TimeFrame.M1);

    assertThat(series.ticker()).isEqualTo(SBER.ticker());
    assertThat(series.classCode()).isEqualTo(SBER.classCode());
    assertThat(series.timeFrame()).isEqualTo(TimeFrame.M1);
    assertThat(series.bars()).hasSize(2);
    Candle first = series.bars().get(0);
    assertThat(first.time()).isEqualTo(T0);
    assertThat(first.open()).isEqualByComparingTo("100.00");
    assertThat(first.close()).isEqualByComparingTo("102.00");
    assertThat(first.high()).isEqualByComparingTo("102.00");
    assertThat(first.low()).isEqualByComparingTo("100.00");
    assertThat(first.volume()).isEqualByComparingTo("15");
    Candle second = series.bars().get(1);
    assertThat(second.time()).isEqualTo(T0.plusSeconds(60));
    assertThat(second.open()).isEqualByComparingTo("99.00");
    assertThat(second.close()).isEqualByComparingTo("99.00");
    assertThat(second.volume()).isEqualByComparingTo("3");
  }

  @Test
  void candlesWithoutTradesAreEmpty() {
    VirtualClock clock = new VirtualClock(T0.plus(Duration.ofHours(5)));
    ReplayMarketDataProvider provider = provider(clock, timeline(), calendar());

    CandleSeries series =
        provider.candles(
            SBER, T0.plus(Duration.ofHours(3)), T0.plus(Duration.ofHours(4)), TimeFrame.M5);

    assertThat(series.bars()).isEmpty();
  }

  @Test
  void advAveragesVolumeAcrossMoscowTradingDays() {
    List<MarketDataSnapshot> onlyTrades =
        List.of(
            tradeEvent(T0, "100.00", 10),
            tradeEvent(T0.plusSeconds(3600), "101.00", 5),
            tradeEvent(T0.plus(Duration.ofDays(1)), "105.00", 20));
    VirtualClock clock = new VirtualClock(T0.plus(Duration.ofDays(2)));
    ReplayMarketDataProvider provider = provider(clock, onlyTrades, calendar());

    BigDecimal adv = provider.adv(SBER);

    assertThat(adv).isEqualByComparingTo("17.50");
    assertThat(adv.scale()).isEqualTo(2);
  }

  @Test
  void advWithoutTradesIsZero() {
    VirtualClock clock = new VirtualClock(T0);
    ReplayMarketDataProvider provider = provider(clock, timeline(), calendar());

    assertThat(provider.adv(GAZP)).isEqualByComparingTo("0");
  }

  @Test
  void tradingSessionIsOpenInsideSession() {
    VirtualClock clock = new VirtualClock(T0.plus(Duration.ofHours(1)));
    ReplayMarketDataProvider provider = provider(clock, timeline(), calendar());

    TradingSession session = provider.tradingSession(clock.instant());

    assertThat(session.open()).isTrue();
    assertThat(session.id()).isEqualTo(1);
    assertThat(session.name()).isEqualTo("Основная сессия");
    assertThat(session.nextSessionAt()).isEqualTo(T0.plus(Duration.ofHours(8)));
  }

  @Test
  void tradingSessionOutsideSessionPointsToNextOpen() {
    VirtualClock clock = new VirtualClock(T0.plus(Duration.ofHours(20)));
    ReplayMarketDataProvider provider = provider(clock, timeline(), calendar());

    TradingSession session = provider.tradingSession(clock.instant());

    assertThat(session.open()).isFalse();
    assertThat(session.nextSessionAt()).isEqualTo(T0.plus(Duration.ofDays(1)));
  }

  @Test
  void tradingSessionAfterClosePointsToNextTradingDay() {
    VirtualClock clock = new VirtualClock(T0.plus(Duration.ofHours(9)));
    ReplayMarketDataProvider provider = provider(clock, timeline(), calendar());

    TradingSession session = provider.tradingSession(clock.instant());

    assertThat(session.open()).isFalse();
    assertThat(session.nextSessionAt()).isEqualTo(T0.plus(Duration.ofDays(1)));
  }

  @Test
  void tradingSessionWithoutCalendarIsClosedAtRequestedInstant() {
    VirtualClock clock = new VirtualClock(T0);
    ReplayMarketDataProvider provider = provider(clock, timeline(), List.of());
    Instant at = T0.plus(Duration.ofHours(3));

    TradingSession session = provider.tradingSession(at);

    assertThat(session.open()).isFalse();
    assertThat(session.nextSessionAt()).isEqualTo(at);
  }

  @Test
  void tradingSessionWithoutNextTradingDayIsClosedAtRequestedInstant() {
    List<TradingDay> onlyFirstDay = List.of(calendar().get(0));
    VirtualClock clock = new VirtualClock(T0);
    ReplayMarketDataProvider provider = provider(clock, timeline(), onlyFirstDay);
    Instant at = T0.plus(Duration.ofDays(3));

    TradingSession session = provider.tradingSession(at);

    assertThat(session.open()).isFalse();
    assertThat(session.nextSessionAt()).isEqualTo(at);
  }

  @Test
  void tradingDaysAreFilteredByMoscowDateInclusive() {
    VirtualClock clock = new VirtualClock(T0);
    ReplayMarketDataProvider provider = provider(clock, timeline(), calendar());

    List<TradingDay> days = provider.tradingDays(T0, T0.plus(Duration.ofDays(2)));

    assertThat(days)
        .extracting(TradingDay::date)
        .containsExactly(
            LocalDate.parse("2026-11-02"),
            LocalDate.parse("2026-11-03"),
            LocalDate.parse("2026-11-04"));
  }

  @Test
  void instrumentsKeepRequestOrderAndSkipUnknown() {
    VirtualClock clock = new VirtualClock(T0);
    ReplayMarketDataProvider provider = provider(clock, timeline(), calendar());

    assertThat(provider.instruments(List.of("GAZP", "SBER", "UNKNOWN")))
        .extracting(Instrument::ticker)
        .containsExactly("GAZP", "SBER");
    assertThat(provider.instrument("SBER", "TQBR")).contains(SBER);
    assertThat(provider.instrument("SBER", "SPBFUT")).isEmpty();
  }

  @Test
  void unknownInstrumentHasNoQuoteAndCannotBeTraded() {
    VirtualClock clock = new VirtualClock(T0);
    ReplayMarketDataProvider provider = provider(clock, timeline(), calendar());
    Instrument unknown =
        new Instrument(
            "NOPE", "TQBR", "STOCK", "Нет", "Нет", "", BigDecimal.ONE, BigDecimal.ONE, "RUB");

    assertThat(provider.quote(unknown)).isEmpty();
    assertThat(provider.trades(unknown, T0, T0.plus(Duration.ofHours(1)))).isEmpty();
    assertThat(provider.candles(unknown, T0, T0.plus(Duration.ofHours(1)), TimeFrame.M1).bars())
        .isEmpty();
    assertThatThrownBy(() -> provider.orderBook(unknown, 5))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("NOPE");
  }

  @Test
  void knownInstrumentWithoutOrderBookYieldsEmptyBookAtCurrentInstant() {
    List<MarketDataSnapshot> onlyQuotes = List.of(quoteSnapshot(T0, new BigDecimal("100.00")));
    VirtualClock clock = new VirtualClock(T0);
    ReplayMarketDataProvider provider = provider(clock, onlyQuotes, calendar());

    OrderBookSnapshot book = provider.orderBook(GAZP, 3);

    assertThat(book.ticker()).isEqualTo(GAZP.ticker());
    assertThat(book.classCode()).isEqualTo(GAZP.classCode());
    assertThat(book.depth()).isEqualTo(3);
    assertThat(book.dateTime()).isEqualTo(T0);
    assertThat(book.bids()).isEmpty();
    assertThat(book.asks()).isEmpty();
  }

  @Test
  void sameTimelineAndAdvanceSequenceProduceSameResults() {
    List<MarketDataSnapshot> timeline = timeline();
    VirtualClock firstClock = new VirtualClock(T0);
    VirtualClock secondClock = new VirtualClock(T0);
    ReplayMarketDataProvider first = provider(firstClock, timeline, calendar());
    ReplayMarketDataProvider second = provider(secondClock, timeline, calendar());

    for (int minute = 0; minute <= 30; minute += 5) {
      if (minute > 0) {
        firstClock.advance(Duration.ofMinutes(5));
        secondClock.advance(Duration.ofMinutes(5));
      }
      assertThat(first.quote(SBER)).isEqualTo(second.quote(SBER));
      assertThat(first.orderBook(SBER, 5)).isEqualTo(second.orderBook(SBER, 5));
      assertThat(first.trades(SBER, T0.minusSeconds(3600), T0.plus(Duration.ofHours(2))))
          .isEqualTo(second.trades(SBER, T0.minusSeconds(3600), T0.plus(Duration.ofHours(2))));
      assertThat(first.candles(SBER, T0, T0.plus(Duration.ofHours(2)), TimeFrame.M1))
          .isEqualTo(second.candles(SBER, T0, T0.plus(Duration.ofHours(2)), TimeFrame.M1));
      assertThat(first.adv(SBER)).isEqualTo(second.adv(SBER));
      assertThat(first.tradingSession(firstClock.instant()))
          .isEqualTo(second.tradingSession(secondClock.instant()));
      assertThat(first.tradingDays(T0, T0.plus(Duration.ofDays(2))))
          .isEqualTo(second.tradingDays(T0, T0.plus(Duration.ofDays(2))));
    }
  }

  @Test
  void constructorRejectsNullArguments() {
    VirtualClock clock = new VirtualClock(T0);

    assertThatThrownBy(() -> new ReplayMarketDataProvider(null, clock, Map.of(), List.of()))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(() -> new ReplayMarketDataProvider(List.of(), null, Map.of(), List.of()))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(() -> new ReplayMarketDataProvider(List.of(), clock, null, List.of()))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(() -> new ReplayMarketDataProvider(List.of(), clock, Map.of(), null))
        .isInstanceOf(NullPointerException.class);
  }

  private static ReplayMarketDataProvider provider(
      VirtualClock clock, List<MarketDataSnapshot> timeline, List<TradingDay> calendar) {
    return new ReplayMarketDataProvider(
        timeline, clock, Map.of("SBER|TQBR", SBER, "GAZP|TQBR", GAZP), calendar);
  }

  private static List<MarketDataSnapshot> timeline() {
    List<MarketDataSnapshot> timeline = new ArrayList<>();
    timeline.add(quoteSnapshot(T0.plusSeconds(600), new BigDecimal("101.00")));
    timeline.add(tradeEvent(T0.plusSeconds(30), "102.00", 5));
    timeline.add(new OrderBookEvent(T0.plusSeconds(600), booked(T0.plusSeconds(600))));
    timeline.add(tradeEvent(T0, "100.00", 10));
    timeline.add(new OrderBookEvent(T0, booked(T0)));
    timeline.add(quoteSnapshot(T0, new BigDecimal("100.00")));
    timeline.add(tradeEvent(T0.plusSeconds(90), "99.00", 3));
    timeline.add(tradeEvent(T0.plus(Duration.ofDays(1)), "105.00", 20));
    return timeline;
  }

  private static List<TradingDay> calendar() {
    return List.of(
        new TradingDay(LocalDate.parse("2026-11-02"), true, T0, T0.plus(Duration.ofHours(8))),
        new TradingDay(
            LocalDate.parse("2026-11-03"),
            true,
            T0.plus(Duration.ofDays(1)),
            T0.plus(Duration.ofDays(1)).plus(Duration.ofHours(8))),
        new TradingDay(LocalDate.parse("2026-11-04"), false, null, null));
  }

  private static QuoteSnapshot quoteSnapshot(Instant ts, BigDecimal last) {
    return new QuoteSnapshot(ts, quote(ts, last.toPlainString()));
  }

  private static Quote quote(Instant ts, String last) {
    BigDecimal price = new BigDecimal(last);
    return new Quote(
        SBER.ticker(),
        SBER.classCode(),
        ts,
        17,
        "RUB",
        price,
        price,
        price,
        price,
        price,
        price,
        price,
        BigDecimal.ZERO,
        BigDecimal.ZERO);
  }

  private static TradeEvent tradeEvent(Instant ts, String price, long quantity) {
    return new TradeEvent(
        ts,
        new Trade(
            ts,
            SBER.ticker(),
            SBER.classCode(),
            new BigDecimal(price),
            quantity,
            TradeDirection.BUY));
  }

  private static OrderBookSnapshot booked(Instant ts) {
    BigDecimal bid = new BigDecimal("99.00");
    BigDecimal ask = new BigDecimal("101.00");
    return new OrderBookSnapshot(
        SBER.ticker(),
        SBER.classCode(),
        5,
        ts,
        List.of(new OrderBookSnapshot.Level(bid, 10)),
        List.of(new OrderBookSnapshot.Level(ask, 20)));
  }
}
