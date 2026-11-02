package ru.moex.papertrader.core.marketdata;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import ru.moex.papertrader.TestInstruments;
import ru.moex.papertrader.core.domain.Instrument;

class RecordingMarketDataProviderTest {

  private static final Instant NOW = Instant.parse("2026-11-02T10:00:00Z");
  private static final Instrument SBER = TestInstruments.sber();
  private static final BigDecimal LAST = new BigDecimal("100.00");

  private final VirtualClock clock = new VirtualClock(NOW);

  @Test
  void quoteIsReturnedAndRecordedWithClockTimestamp() {
    MarketDataProvider delegate = mock(MarketDataProvider.class);
    List<MarketDataSnapshot> recorded = new ArrayList<>();
    Quote quote = quote(LAST);
    when(delegate.quote(SBER)).thenReturn(Optional.of(quote));
    RecordingMarketDataProvider provider = provider(delegate, recorded);

    Optional<Quote> result = provider.quote(SBER);

    assertThat(result).contains(quote);
    assertThat(recorded).containsExactly(new QuoteSnapshot(NOW, quote));
  }

  @Test
  void missingQuoteIsNotRecorded() {
    MarketDataProvider delegate = mock(MarketDataProvider.class);
    List<MarketDataSnapshot> recorded = new ArrayList<>();
    when(delegate.quote(SBER)).thenReturn(Optional.empty());
    RecordingMarketDataProvider provider = provider(delegate, recorded);

    assertThat(provider.quote(SBER)).isEmpty();
    assertThat(recorded).isEmpty();
  }

  @Test
  void orderBookIsReturnedAndRecordedWithClockTimestamp() {
    MarketDataProvider delegate = mock(MarketDataProvider.class);
    List<MarketDataSnapshot> recorded = new ArrayList<>();
    OrderBookSnapshot book = orderBook(3);
    when(delegate.orderBook(SBER, 3)).thenReturn(book);
    RecordingMarketDataProvider provider = provider(delegate, recorded);

    OrderBookSnapshot result = provider.orderBook(SBER, 3);

    assertThat(result).isSameAs(book);
    assertThat(recorded).containsExactly(new OrderBookEvent(NOW, book));
  }

  @Test
  void tradesAreReturnedAndEachIsRecordedWithClockTimestamp() {
    MarketDataProvider delegate = mock(MarketDataProvider.class);
    List<MarketDataSnapshot> recorded = new ArrayList<>();
    Instant from = NOW.minusSeconds(60);
    List<Trade> trades =
        List.of(
            trade(NOW.minusSeconds(30), "100.00", 10), trade(NOW.minusSeconds(10), "101.00", 5));
    when(delegate.trades(SBER, from, NOW)).thenReturn(trades);
    RecordingMarketDataProvider provider = provider(delegate, recorded);

    List<Trade> result = provider.trades(SBER, from, NOW);

    assertThat(result).isEqualTo(trades);
    assertThat(recorded)
        .containsExactly(new TradeEvent(NOW, trades.get(0)), new TradeEvent(NOW, trades.get(1)));
  }

  @Test
  void nonRecordingMethodsDelegateWithoutWritingSnapshots() {
    MarketDataProvider delegate = mock(MarketDataProvider.class);
    List<MarketDataSnapshot> recorded = new ArrayList<>();
    List<Instrument> instruments = List.of(SBER);
    CandleSeries candles =
        new CandleSeries(
            SBER.ticker(), SBER.classCode(), NOW, NOW.plusSeconds(3600), TimeFrame.M1, List.of());
    TradingSession session = new TradingSession(1, "Основная сессия", true, NOW);
    List<TradingDay> days =
        List.of(
            new TradingDay(
                NOW.atZone(java.time.ZoneOffset.UTC).toLocalDate(),
                true,
                NOW,
                NOW.plusSeconds(3600)));
    when(delegate.instruments(any())).thenReturn(instruments);
    when(delegate.instrument("SBER", "TQBR")).thenReturn(Optional.of(SBER));
    when(delegate.candles(eq(SBER), eq(NOW), eq(NOW.plusSeconds(3600)), eq(TimeFrame.M1)))
        .thenReturn(candles);
    when(delegate.tradingSession(NOW)).thenReturn(session);
    when(delegate.tradingDays(eq(NOW), eq(NOW.plusSeconds(86400)))).thenReturn(days);
    when(delegate.adv(SBER)).thenReturn(new BigDecimal("1000.00"));
    RecordingMarketDataProvider provider = provider(delegate, recorded);

    assertThat(provider.instruments(List.of("SBER"))).isEqualTo(instruments);
    assertThat(provider.instrument("SBER", "TQBR")).contains(SBER);
    assertThat(provider.candles(SBER, NOW, NOW.plusSeconds(3600), TimeFrame.M1)).isSameAs(candles);
    assertThat(provider.tradingSession(NOW)).isSameAs(session);
    assertThat(provider.tradingDays(NOW, NOW.plusSeconds(86400))).isEqualTo(days);
    assertThat(provider.adv(SBER)).isEqualByComparingTo("1000.00");

    assertThat(recorded).isEmpty();
    verify(delegate).instruments(List.of("SBER"));
    verify(delegate).instrument("SBER", "TQBR");
    verify(delegate).candles(SBER, NOW, NOW.plusSeconds(3600), TimeFrame.M1);
    verify(delegate).tradingSession(NOW);
    verify(delegate).tradingDays(NOW, NOW.plusSeconds(86400));
    verify(delegate).adv(SBER);
    verify(delegate, times(0)).quote(any());
  }

  @Test
  void nullSinkIsRejected() {
    MarketDataProvider delegate = mock(MarketDataProvider.class);

    assertThatThrownBy(() -> new RecordingMarketDataProvider(delegate, null, clock))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  void nullOrderBookResultIsRejectedAsContractViolation() {
    MarketDataProvider delegate = mock(MarketDataProvider.class);
    List<MarketDataSnapshot> recorded = new ArrayList<>();
    when(delegate.orderBook(eq(SBER), anyInt())).thenReturn(null);
    RecordingMarketDataProvider provider = provider(delegate, recorded);

    assertThatThrownBy(() -> provider.orderBook(SBER, 5))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("orderBook");

    assertThat(recorded).isEmpty();
  }

  @Test
  void recordedTimelineReplaysIdenticalQuotesAndOrderBooks() {
    Quote quote = quote(LAST);
    OrderBookSnapshot book = orderBook(5);
    MarketDataProvider live = new FixedMarketDataProvider(quote, book);
    List<MarketDataSnapshot> recorded = new ArrayList<>();
    RecordingMarketDataProvider recording =
        new RecordingMarketDataProvider(live, recorded::add, clock);

    Quote recordedQuote = recording.quote(SBER).orElseThrow();
    OrderBookSnapshot recordedBook = recording.orderBook(SBER, 5);
    assertThat(recorded).hasSize(2);

    ReplayMarketDataProvider replay =
        new ReplayMarketDataProvider(recorded, clock, Map.of("SBER|TQBR", SBER), List.of());

    assertThat(replay.quote(SBER)).contains(recordedQuote);
    assertThat(replay.orderBook(SBER, 5)).isEqualTo(recordedBook);
  }

  private RecordingMarketDataProvider provider(
      MarketDataProvider delegate, List<MarketDataSnapshot> recorded) {
    return new RecordingMarketDataProvider(delegate, recorded::add, clock);
  }

  private static Quote quote(BigDecimal last) {
    return new Quote(
        SBER.ticker(),
        SBER.classCode(),
        NOW,
        17,
        "RUB",
        last,
        last,
        last,
        last,
        last,
        last,
        last,
        BigDecimal.ZERO,
        BigDecimal.ZERO);
  }

  private static OrderBookSnapshot orderBook(int depth) {
    return new OrderBookSnapshot(
        SBER.ticker(),
        SBER.classCode(),
        depth,
        NOW,
        List.of(new OrderBookSnapshot.Level(new BigDecimal("99.00"), 10)),
        List.of(new OrderBookSnapshot.Level(new BigDecimal("101.00"), 20)));
  }

  private static Trade trade(Instant ts, String price, long quantity) {
    return new Trade(
        ts, SBER.ticker(), SBER.classCode(), new BigDecimal(price), quantity, TradeDirection.BUY);
  }

  /** Провайдер с фиксированными котировкой и стаканом — источник записи для round-trip теста. */
  private static final class FixedMarketDataProvider implements MarketDataProvider {

    private final Quote quote;
    private final OrderBookSnapshot orderBook;

    private FixedMarketDataProvider(Quote quote, OrderBookSnapshot orderBook) {
      this.quote = quote;
      this.orderBook = orderBook;
    }

    @Override
    public List<Instrument> instruments(Collection<String> tickers) {
      return tickers.contains(SBER.ticker()) ? List.of(SBER) : List.of();
    }

    @Override
    public Optional<Instrument> instrument(String ticker, String classCode) {
      return SBER.ticker().equals(ticker) && SBER.classCode().equals(classCode)
          ? Optional.of(SBER)
          : Optional.empty();
    }

    @Override
    public Optional<Quote> quote(Instrument instrument) {
      return SBER.ticker().equals(instrument.ticker()) ? Optional.of(quote) : Optional.empty();
    }

    @Override
    public OrderBookSnapshot orderBook(Instrument instrument, int depth) {
      return orderBook;
    }

    @Override
    public CandleSeries candles(
        Instrument instrument, Instant from, Instant to, TimeFrame timeFrame) {
      return new CandleSeries(
          instrument.ticker(), instrument.classCode(), from, to, timeFrame, List.of());
    }

    @Override
    public TradingSession tradingSession(Instant at) {
      return new TradingSession(1, "Основная сессия", true, at);
    }
  }
}
