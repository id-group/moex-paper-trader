package ru.moex.papertrader.core.marketdata;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import ru.moex.papertrader.core.domain.Instrument;

/**
 * Режим RECORD: декоратор над провайдером LIVE, который складывает входящие рыночные данные в
 * приёмник {@link Consumer} в виде снапшотов с меткой времени из порта {@link Clock}.
 *
 * <p>Записываются только запросы котировок, стакана и сделок; справочник инструментов, свечи,
 * состояние сессии, торговые дни и ADV делегируются без записи. Собранный приёмником таймлайн можно
 * воспроизвести через {@link ReplayMarketDataProvider}.
 */
public final class RecordingMarketDataProvider implements MarketDataProvider {

  private final MarketDataProvider delegate;
  private final Consumer<MarketDataSnapshot> sink;
  private final Clock clock;

  /**
   * @param delegate провайдер LIVE, чьи ответы записываются
   * @param sink приёмник снапшотов (например, коллекция или хранилище), не {@code null}
   * @param clock источник меток времени снапшотов
   */
  public RecordingMarketDataProvider(
      MarketDataProvider delegate, Consumer<MarketDataSnapshot> sink, Clock clock) {
    this.delegate = Objects.requireNonNull(delegate, "delegate");
    this.sink = Objects.requireNonNull(sink, "sink");
    this.clock = Objects.requireNonNull(clock, "clock");
  }

  @Override
  public List<Instrument> instruments(Collection<String> tickers) {
    return delegate.instruments(tickers);
  }

  @Override
  public Optional<Instrument> instrument(String ticker, String classCode) {
    return delegate.instrument(ticker, classCode);
  }

  @Override
  public Optional<Quote> quote(Instrument instrument) {
    Optional<Quote> quote = delegate.quote(instrument);
    quote.ifPresent(value -> sink.accept(new QuoteSnapshot(clock.instant(), value)));
    return quote;
  }

  @Override
  public OrderBookSnapshot orderBook(Instrument instrument, int depth) {
    OrderBookSnapshot orderBook = requireResult(delegate.orderBook(instrument, depth), "orderBook");
    sink.accept(new OrderBookEvent(clock.instant(), orderBook));
    return orderBook;
  }

  @Override
  public CandleSeries candles(
      Instrument instrument, Instant from, Instant to, TimeFrame timeFrame) {
    return delegate.candles(instrument, from, to, timeFrame);
  }

  @Override
  public TradingSession tradingSession(Instant at) {
    return delegate.tradingSession(at);
  }

  @Override
  public List<Trade> trades(Instrument instrument, Instant from, Instant to) {
    List<Trade> trades = requireResult(delegate.trades(instrument, from, to), "trades");
    for (Trade trade : trades) {
      sink.accept(new TradeEvent(clock.instant(), trade));
    }
    return trades;
  }

  @Override
  public BigDecimal adv(Instrument instrument) {
    return delegate.adv(instrument);
  }

  @Override
  public List<TradingDay> tradingDays(Instant from, Instant to) {
    return delegate.tradingDays(from, to);
  }

  /** Нарушение контракта провайдера: там, где {@code null} недопустим, ответ обязателен. */
  private static <T> T requireResult(T value, String name) {
    if (value == null) {
      throw new IllegalStateException("Провайдер вернул null вместо " + name);
    }
    return value;
  }
}
