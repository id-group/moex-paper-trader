package ru.moex.papertrader.infra.services;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import ru.moex.papertrader.core.domain.Instrument;
import ru.moex.papertrader.core.marketdata.Candle;
import ru.moex.papertrader.core.marketdata.CandleSeries;
import ru.moex.papertrader.core.marketdata.Clock;
import ru.moex.papertrader.core.marketdata.MarketDataProvider;
import ru.moex.papertrader.core.marketdata.OrderBookSnapshot;
import ru.moex.papertrader.core.marketdata.Quote;
import ru.moex.papertrader.core.marketdata.TimeFrame;
import ru.moex.papertrader.core.marketdata.TradingSession;

/**
 * Заглушка порта рыночных данных: инструменты и базовые цены из конфигурации, котировки
 * генерируются детерминированно. Настоящие клиенты AlgoPack/MOEX ISS и режимы LIVE/RECORD/REPLAY
 * подключает {@code MarketDataConfiguration} (этап 4); заглушка остаётся режимом STUB по умолчанию.
 */
public class StubMarketDataProvider implements MarketDataProvider {

  private static final int TRADING_STATUS_OPEN = 17;
  private static final int LEVEL_QUANTITY_STEP = 100;

  private final Clock clock;
  private final Map<String, StubMarketDataProperties.InstrumentDefinition> definitions;
  private final Map<String, Instrument> instruments;

  public StubMarketDataProvider(StubMarketDataProperties properties, Clock clock) {
    this.clock = clock;
    Map<String, StubMarketDataProperties.InstrumentDefinition> definitions = new LinkedHashMap<>();
    Map<String, Instrument> instruments = new LinkedHashMap<>();
    for (StubMarketDataProperties.InstrumentDefinition definition : properties.instruments()) {
      String key = key(definition.ticker(), definition.classCode());
      definitions.put(key, definition);
      instruments.put(
          key,
          new Instrument(
              definition.ticker(),
              definition.classCode(),
              definition.type(),
              definition.shortName(),
              definition.displayName(),
              definition.isin(),
              definition.lotSize(),
              definition.minimumStep(),
              definition.currency()));
    }
    this.definitions = Map.copyOf(definitions);
    this.instruments = Map.copyOf(instruments);
  }

  @Override
  public List<Instrument> instruments(Collection<String> tickers) {
    List<Instrument> result = new ArrayList<>();
    for (String ticker : new LinkedHashSet<>(tickers)) {
      for (Instrument instrument : instruments.values()) {
        if (instrument.ticker().equals(ticker)) {
          result.add(instrument);
          break;
        }
      }
    }
    return result;
  }

  @Override
  public Optional<Instrument> instrument(String ticker, String classCode) {
    return Optional.ofNullable(instruments.get(key(ticker, classCode)));
  }

  @Override
  public Optional<Quote> quote(Instrument instrument) {
    StubMarketDataProperties.InstrumentDefinition definition =
        definitions.get(key(instrument.ticker(), instrument.classCode()));
    if (definition == null) {
      return Optional.empty();
    }
    BigDecimal price = definition.basePrice();
    BigDecimal step = definition.minimumStep();
    BigDecimal bid = price.subtract(step).max(step);
    BigDecimal offer = price.add(step);
    return Optional.of(
        new Quote(
            instrument.ticker(),
            instrument.classCode(),
            clock.instant(),
            TRADING_STATUS_OPEN,
            instrument.currency(),
            bid,
            offer,
            price,
            price,
            price.add(step),
            price.subtract(step),
            price,
            BigDecimal.ZERO,
            BigDecimal.ZERO));
  }

  @Override
  public OrderBookSnapshot orderBook(Instrument instrument, int depth) {
    StubMarketDataProperties.InstrumentDefinition definition =
        definitions.get(key(instrument.ticker(), instrument.classCode()));
    if (definition == null) {
      throw new IllegalArgumentException("Инструмент " + instrument.ticker() + " не найден");
    }
    BigDecimal price = definition.basePrice();
    BigDecimal step = definition.minimumStep();
    List<OrderBookSnapshot.Level> bids = new ArrayList<>(depth);
    List<OrderBookSnapshot.Level> asks = new ArrayList<>(depth);
    for (int i = 1; i <= depth; i++) {
      bids.add(
          new OrderBookSnapshot.Level(
              price.subtract(step.multiply(BigDecimal.valueOf(i))).max(step),
              (long) LEVEL_QUANTITY_STEP * i));
      asks.add(
          new OrderBookSnapshot.Level(
              price.add(step.multiply(BigDecimal.valueOf(i))), (long) LEVEL_QUANTITY_STEP * i));
    }
    return new OrderBookSnapshot(
        instrument.ticker(), instrument.classCode(), depth, clock.instant(), bids, asks);
  }

  @Override
  public CandleSeries candles(
      Instrument instrument, Instant from, Instant to, TimeFrame timeFrame) {
    StubMarketDataProperties.InstrumentDefinition definition =
        definitions.get(key(instrument.ticker(), instrument.classCode()));
    if (definition == null) {
      throw new IllegalArgumentException("Инструмент " + instrument.ticker() + " не найден");
    }
    long bars = Duration.between(from, to).dividedBy(timeFrame.step()) + 1;
    if (bars <= 0) {
      return new CandleSeries(
          instrument.ticker(), instrument.classCode(), from, to, timeFrame, List.of());
    }
    BigDecimal basePrice = definition.basePrice();
    BigDecimal step = definition.minimumStep();
    int scale = step.scale();
    List<Candle> candles = new ArrayList<>((int) bars);
    BigDecimal open = basePrice;
    for (int i = 0; i < bars; i++) {
      Instant time = from.plus(timeFrame.step().multipliedBy(i));
      BigDecimal close =
          basePrice
              .multiply(BigDecimal.valueOf(1 + 0.002 * Math.sin(i / 2.0)))
              .setScale(scale, RoundingMode.HALF_UP);
      BigDecimal high = open.max(close).add(step);
      BigDecimal low = open.min(close).subtract(step).max(step);
      candles.add(new Candle(time, open, close, high, low, BigDecimal.valueOf(10_000L + i * 10L)));
      open = close;
    }
    return new CandleSeries(
        instrument.ticker(), instrument.classCode(), from, to, timeFrame, candles);
  }

  @Override
  public TradingSession tradingSession(Instant at) {
    return new TradingSession(1, "Основная сессия", true, at.plus(Duration.ofHours(1)));
  }

  private static String key(String ticker, String classCode) {
    return ticker + "|" + classCode;
  }
}
