package ru.moex.papertrader.infra.algoapi;

import java.math.BigDecimal;
import java.math.RoundingMode;
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
import ru.moex.papertrader.infra.algoapi.dto.AlgoPackCandleDto;
import ru.moex.papertrader.infra.algoapi.dto.AlgoPackOrderBookDto;
import ru.moex.papertrader.infra.algoapi.dto.AlgoPackQuoteDto;
import ru.moex.papertrader.infra.algoapi.dto.AlgoPackTradeDto;

/**
 * Провайдер рыночных данных на AlgoPack (LIVE): котировки, стакан, свечи и сделки — из AlgoPack,
 * справочник инструментов — из MOEX ISS.
 *
 * <p>Не Spring-бин: собирается вручную оркестратором (infra/services). Справочник инструментов
 * кэшируется лениво и загружается один раз. Торговый календарь делегируется {@link
 * TradingCalendar}.
 */
public class AlgoPackMarketDataProvider implements MarketDataProvider {

  private static final Logger log = LoggerFactory.getLogger(AlgoPackMarketDataProvider.class);

  /** Числовой статус «торги идут» в контракте БКС; в ответе AlgoPack статус строковый. */
  private static final int TRADING_STATUS_OPEN = 17;

  private static final int ADV_WINDOW_DAYS = 30;
  private static final int ADV_SCALE = 2;
  private static final String DAILY_INTERVAL = "24";
  private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

  private final AlgoPackClient algoPack;
  private final TradingCalendar calendar;
  private final Clock clock;
  private final IssInstrumentCatalog catalog;

  /** Конструктор контракта: время берётся из системных часов (UTC). */
  public AlgoPackMarketDataProvider(
      AlgoPackClient algoPack,
      IssClient iss,
      TradingCalendar calendar,
      AlgoPackProperties properties) {
    this(algoPack, iss, calendar, properties, new SystemClock());
  }

  /** Конструктор с явным портом времени — для детерминированных тестов и REPLAY. */
  public AlgoPackMarketDataProvider(
      AlgoPackClient algoPack,
      IssClient iss,
      TradingCalendar calendar,
      AlgoPackProperties properties,
      Clock clock) {
    this.algoPack = algoPack;
    this.calendar = calendar;
    this.clock = clock;
    this.catalog =
        new IssInstrumentCatalog(iss, properties.engine(), properties.market(), properties.board());
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
      AlgoPackQuoteDto dto = algoPack.quote(instrument.ticker());
      BigDecimal last = dto.last() != null ? dto.last() : dto.bid();
      BigDecimal change =
          dto.change() != null ? dto.change() : subtractOrZero(last, dto.prevPrice());
      BigDecimal changeRate =
          dto.changeRate() != null ? dto.changeRate() : percentOrZero(change, dto.prevPrice());
      Instant time = dto.updatedAt() != null ? dto.updatedAt() : clock.instant();
      return Optional.of(
          new Quote(
              instrument.ticker(),
              instrument.classCode(),
              time,
              TRADING_STATUS_OPEN,
              instrument.currency(),
              dto.bid(),
              dto.offer(),
              dto.open(),
              last,
              dto.high(),
              dto.low(),
              last,
              change,
              changeRate));
    } catch (AlgoApiException e) {
      log.warn("AlgoPack: котировка {} недоступна: {}", instrument.ticker(), e.getMessage());
      return Optional.empty();
    }
  }

  @Override
  public OrderBookSnapshot orderBook(Instrument instrument, int depth) {
    AlgoPackOrderBookDto dto = algoPack.orderBook(instrument.ticker(), depth);
    Instant time = dto.updatedAt() != null ? dto.updatedAt() : clock.instant();
    return new OrderBookSnapshot(
        instrument.ticker(),
        instrument.classCode(),
        depth,
        time,
        levels(dto.bids()),
        levels(dto.asks()));
  }

  @Override
  public CandleSeries candles(
      Instrument instrument, Instant from, Instant to, TimeFrame timeFrame) {
    OptionalInt interval = CandleIntervals.toIntervalCode(timeFrame);
    if (interval.isEmpty()) {
      log.warn("AlgoPack: таймфрейм {} не поддерживается, серия пустая", timeFrame);
      return new CandleSeries(
          instrument.ticker(), instrument.classCode(), from, to, timeFrame, List.of());
    }
    List<AlgoPackCandleDto> candles =
        algoPack.candles(instrument.ticker(), Integer.toString(interval.getAsInt()), from, to);
    List<Candle> bars = new ArrayList<>(candles.size());
    for (AlgoPackCandleDto candle : candles) {
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
    List<AlgoPackTradeDto> trades = algoPack.allTrades(instrument.ticker(), from, to);
    List<Trade> result = new ArrayList<>(trades.size());
    for (AlgoPackTradeDto trade : trades) {
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
      List<AlgoPackCandleDto> candles =
          algoPack.candles(instrument.ticker(), DAILY_INTERVAL, from, now);
      return AdvCalculator.averageDailyVolume(
          candles.stream().map(AlgoPackCandleDto::volume).toList(), instrument.lotSize());
    } catch (RuntimeException e) {
      log.warn("AlgoPack: ADV {} недоступен: {}", instrument.ticker(), e.getMessage());
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

  private static List<OrderBookSnapshot.Level> levels(List<AlgoPackOrderBookDto.Level> levels) {
    List<OrderBookSnapshot.Level> result = new ArrayList<>(levels.size());
    for (AlgoPackOrderBookDto.Level level : levels) {
      result.add(new OrderBookSnapshot.Level(level.price(), level.quantity()));
    }
    return result;
  }

  /**
   * Сторона сделки. AlgoPack отдаёт {@code BUYSELL}; при пустом значении считаем сделку покупкой
   * (допущение зафиксировано в отчёте этапа 4).
   */
  private static TradeDirection direction(String buySell) {
    return "S".equalsIgnoreCase(buySell) ? TradeDirection.SELL : TradeDirection.BUY;
  }

  private static BigDecimal subtractOrZero(BigDecimal left, BigDecimal right) {
    return left == null || right == null ? BigDecimal.ZERO : left.subtract(right);
  }

  private static BigDecimal percentOrZero(BigDecimal change, BigDecimal base) {
    if (base == null || base.signum() == 0) {
      return BigDecimal.ZERO;
    }
    return change.multiply(HUNDRED).divide(base, ADV_SCALE, RoundingMode.HALF_UP);
  }
}
