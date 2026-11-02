package ru.moex.papertrader.infra.algoapi;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.moex.papertrader.core.domain.Instrument;
import ru.moex.papertrader.core.marketdata.Candle;
import ru.moex.papertrader.core.marketdata.CandleSeries;
import ru.moex.papertrader.core.marketdata.Clock;
import ru.moex.papertrader.core.marketdata.MarketDataProvider;
import ru.moex.papertrader.core.marketdata.OrderBookSnapshot;
import ru.moex.papertrader.core.marketdata.Quote;
import ru.moex.papertrader.core.marketdata.SystemClock;
import ru.moex.papertrader.core.marketdata.TimeFrame;
import ru.moex.papertrader.core.marketdata.Trade;
import ru.moex.papertrader.core.marketdata.TradeDirection;
import ru.moex.papertrader.core.marketdata.TradingCalendar;
import ru.moex.papertrader.core.marketdata.TradingDay;
import ru.moex.papertrader.core.marketdata.TradingSession;
import ru.moex.papertrader.infra.algoapi.dto.IssCandleDto;
import ru.moex.papertrader.infra.algoapi.dto.IssOrderBookDto;
import ru.moex.papertrader.infra.algoapi.dto.IssTradeDto;

/**
 * Провайдер рыночных данных на MOEX ISS: используется как fallback, когда AlgoPack недоступен, и
 * как источник справочника инструментов.
 *
 * <p>Не Spring-бин: собирается вручную оркестратором (infra/services).
 */
public class IssMarketDataProvider implements MarketDataProvider {

  private static final Logger log = LoggerFactory.getLogger(IssMarketDataProvider.class);

  /** Числовой статус «торги идут» в контракте БКС. */
  private static final int TRADING_STATUS_OPEN = 17;

  private static final int ADV_WINDOW_DAYS = 30;
  private static final int DAILY_INTERVAL = 24;

  private final IssClient iss;
  private final TradingCalendar calendar;
  private final Clock clock;
  private final String engine;
  private final String market;
  private final String board;
  private final IssInstrumentCatalog catalog;

  /** Конструктор контракта: время берётся из системных часов (UTC). */
  public IssMarketDataProvider(IssClient iss, TradingCalendar calendar, IssProperties properties) {
    this(iss, calendar, properties, new SystemClock());
  }

  /** Конструктор с явным портом времени — для детерминированных тестов. */
  public IssMarketDataProvider(
      IssClient iss, TradingCalendar calendar, IssProperties properties, Clock clock) {
    this.iss = iss;
    this.calendar = calendar;
    this.clock = clock;
    this.engine = properties.engine();
    this.market = properties.market();
    this.board = properties.board();
    this.catalog = new IssInstrumentCatalog(iss, engine, market, board);
  }

  @Override
  public List<Instrument> instruments(Collection<String> tickers) {
    return catalog.instruments(tickers);
  }

  @Override
  public Optional<Instrument> instrument(String ticker, String classCode) {
    return catalog.instrument(ticker, classCode);
  }

  @Override
  public Optional<Quote> quote(Instrument instrument) {
    try {
      IssOrderBookDto dto = iss.orderBook(engine, market, board, instrument.ticker());
      BigDecimal bid = dto.bids().isEmpty() ? null : dto.bids().get(0).price();
      BigDecimal offer = dto.asks().isEmpty() ? null : dto.asks().get(0).price();
      BigDecimal last = dto.last() != null ? dto.last() : bid;
      if (last == null) {
        return Optional.empty();
      }
      Instant time = dto.updatedAt() != null ? dto.updatedAt() : clock.instant();
      return Optional.of(
          new Quote(
              instrument.ticker(),
              instrument.classCode(),
              time,
              TRADING_STATUS_OPEN,
              instrument.currency(),
              bid,
              offer,
              null,
              last,
              null,
              null,
              last,
              BigDecimal.ZERO,
              BigDecimal.ZERO));
    } catch (AlgoApiException e) {
      log.warn("ISS: котировка {} недоступна: {}", instrument.ticker(), e.getMessage());
      return Optional.empty();
    }
  }

  @Override
  public OrderBookSnapshot orderBook(Instrument instrument, int depth) {
    IssOrderBookDto dto = iss.orderBook(engine, market, board, instrument.ticker());
    Instant time = dto.updatedAt() != null ? dto.updatedAt() : clock.instant();
    return new OrderBookSnapshot(
        instrument.ticker(),
        instrument.classCode(),
        depth,
        time,
        levels(dto.bids(), depth),
        levels(dto.asks(), depth));
  }

  @Override
  public CandleSeries candles(
      Instrument instrument, Instant from, Instant to, TimeFrame timeFrame) {
    OptionalInt interval = CandleIntervals.toIntervalCode(timeFrame);
    if (interval.isEmpty()) {
      log.warn("ISS: таймфрейм {} не поддерживается, серия пустая", timeFrame);
      return new CandleSeries(
          instrument.ticker(), instrument.classCode(), from, to, timeFrame, List.of());
    }
    List<IssCandleDto> candles =
        iss.candles(engine, market, board, instrument.ticker(), interval.getAsInt(), from, to);
    List<Candle> bars = new ArrayList<>(candles.size());
    for (IssCandleDto candle : candles) {
      bars.add(
          new Candle(
              candle.begin(),
              candle.open(),
              candle.close(),
              candle.high(),
              candle.low(),
              candle.volume()));
    }
    return new CandleSeries(instrument.ticker(), instrument.classCode(), from, to, timeFrame, bars);
  }

  @Override
  public List<Trade> trades(Instrument instrument, Instant from, Instant to) {
    List<IssTradeDto> trades = iss.trades(engine, market, board, instrument.ticker(), from, to);
    List<Trade> result = new ArrayList<>(trades.size());
    for (IssTradeDto trade : trades) {
      result.add(
          new Trade(
              trade.tradeTime(),
              instrument.ticker(),
              instrument.classCode(),
              trade.price(),
              trade.quantity(),
              direction(trade.buySell())));
    }
    return List.copyOf(result);
  }

  @Override
  public BigDecimal adv(Instrument instrument) {
    Instant now = clock.instant();
    Instant from = now.minus(ADV_WINDOW_DAYS, ChronoUnit.DAYS);
    try {
      List<IssCandleDto> candles =
          iss.candles(engine, market, board, instrument.ticker(), DAILY_INTERVAL, from, now);
      return AdvCalculator.averageDailyVolume(
          candles.stream().map(IssCandleDto::volume).toList(), instrument.lotSize());
    } catch (RuntimeException e) {
      log.warn("ISS: ADV {} недоступен: {}", instrument.ticker(), e.getMessage());
      return BigDecimal.ZERO;
    }
  }

  @Override
  public TradingSession tradingSession(Instant at) {
    return calendar.tradingSession(at);
  }

  @Override
  public List<TradingDay> tradingDays(Instant from, Instant to) {
    return calendar.tradingDays(from, to);
  }

  private static List<OrderBookSnapshot.Level> levels(
      List<IssOrderBookDto.Level> levels, int depth) {
    int limit = Math.max(depth, 0);
    List<OrderBookSnapshot.Level> result = new ArrayList<>(Math.min(levels.size(), limit));
    for (IssOrderBookDto.Level level : levels) {
      if (result.size() >= limit) {
        break;
      }
      result.add(new OrderBookSnapshot.Level(level.price(), level.quantity()));
    }
    return result;
  }

  /** Сторона сделки; при пустом {@code BUYSELL} считаем сделку покупкой (допущение этапа 4). */
  private static TradeDirection direction(String buySell) {
    return "S".equalsIgnoreCase(buySell) ? TradeDirection.SELL : TradeDirection.BUY;
  }
}
