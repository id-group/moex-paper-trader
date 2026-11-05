package ru.moex.papertrader.it.replay;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import ru.moex.papertrader.core.domain.Instrument;
import ru.moex.papertrader.core.marketdata.MarketDataSnapshot;
import ru.moex.papertrader.core.marketdata.OrderBookEvent;
import ru.moex.papertrader.core.marketdata.OrderBookSnapshot;
import ru.moex.papertrader.core.marketdata.Quote;
import ru.moex.papertrader.core.marketdata.QuoteSnapshot;
import ru.moex.papertrader.core.marketdata.Trade;
import ru.moex.papertrader.core.marketdata.TradeDirection;
import ru.moex.papertrader.core.marketdata.TradeEvent;
import ru.moex.papertrader.core.marketdata.TradingDay;

/**
 * Детерминированный REPLAY-набор для сквозного интеграционного прогона этапа 7.
 *
 * <p>Это замена записи торговой сессии: 24 будних дня, три ликвидных инструмента (SBER, GAZP,
 * SiU6), по 32 пятнадцатиминутных бара в сессии 07:00–15:00 UTC. Цена каждого инструмента —
 * собственный {@link Random} с фиксированным seed: подъём (~8 дней), коррекция (~6 дней) и
 * восстановление, поэтому Max Drawdown и Calmar определены и устойчивы к издержкам.
 *
 * <p>На каждый бар пишутся котировка, стакан глубины 10 и две сделки по 1500 units, то есть дневной
 * объём 96 000 units — порог ADV 5% равен 4800, и объёмы заявок стратегии (500/500/5) заведомо
 * ниже. Снапшоты отличаются от начала бара на одну секунду: сигнал стратегии исполняется в {@code
 * бар + 2 с}, когда данные бара уже видимы REPLAY-провайдеру.
 *
 * <p>Набор полностью воспроизводим: ни системных часов, ни внешних источников, ни изменяемого
 * состояния. Возвращаемые коллекции неизменяемы.
 */
public final class ReplayDataset {

  /** Инструменты набора. */
  public static final String SBER = "SBER";

  public static final String GAZP = "GAZP";

  public static final String SI = "SiU6";

  public static final String TQBR = "TQBR";

  public static final String SPBFUT = "SPBFUT";

  /** Первый торговый день набора. */
  public static final LocalDate FIRST_DATE = LocalDate.of(2026, 9, 1);

  /** Число торговых дней. */
  public static final int TRADING_DAYS = 24;

  /** Баров в торговом дне. */
  public static final int BARS_PER_DAY = 32;

  /** Шаг бара. */
  public static final Duration BAR_STEP = Duration.ofMinutes(15);

  /** Начало основной сессии (UTC). */
  public static final LocalTime SESSION_OPEN = LocalTime.of(7, 0);

  /** Конец основной сессии (UTC). */
  public static final LocalTime SESSION_CLOSE = LocalTime.of(15, 0);

  /** Объём одной сделки в снапшоте; дневной объём — {@code 2 * 1500 * 32}. */
  public static final long TRADE_QUANTITY = 1_500;

  private static final ZoneOffset UTC = ZoneOffset.UTC;
  private static final long SEED = 20_260_901L;
  private static final int BOOK_DEPTH = 10;
  private static final long BOOK_LEVEL_QUANTITY = 5_000;
  private static final BigDecimal INITIAL_CASH = new BigDecimal("1000000.00");

  private final List<Instrument> instruments;
  private final List<InstrumentSpec> specs;
  private final List<TradingDay> tradingDays;
  private final List<LocalDate> tradingDates;
  private final Map<String, List<Bar>> bars;
  private final List<MarketDataSnapshot> snapshots;

  private ReplayDataset() {
    this.specs = specs();
    this.instruments = specs.stream().map(InstrumentSpec::instrument).toList();
    this.tradingDates = generateTradingDates();
    this.tradingDays = tradingDays(tradingDates);
    Map<String, List<Bar>> generated = new LinkedHashMap<>();
    List<MarketDataSnapshot> stream = new ArrayList<>();
    for (InstrumentSpec spec : specs) {
      List<Bar> instrumentBars = generateBars(spec);
      generated.put(spec.ticker(), instrumentBars);
      appendSnapshots(spec, instrumentBars, stream);
    }
    this.bars = Map.copyOf(generated);
    this.snapshots = List.copyOf(stream);
  }

  /** Стандартный набор: 24 торговых дня SBER/GAZP/SiU6 с фиксированным seed. */
  public static ReplayDataset standard() {
    return new ReplayDataset();
  }

  /** Снапшоты потока в хронологическом порядке: QUOTE, ORDER_BOOK, TRADE, TRADE на бар. */
  public List<MarketDataSnapshot> snapshots() {
    return snapshots;
  }

  /** Справочник инструментов набора. */
  public List<Instrument> instruments() {
    return instruments;
  }

  /**
   * Календарь окна набора: все календарные даты от первого дня минус один до последнего дня плюс
   * один, включая выходные с {@code trading = false}. Полнота календаря не даёт приложению
   * обращаться к MOEX ISS за недостающими датами.
   */
  public List<TradingDay> tradingDays() {
    return tradingDays;
  }

  /** Только торговые даты набора. */
  public List<LocalDate> tradingDates() {
    return tradingDates;
  }

  /** Момент первого снапшота набора. */
  public Instant start() {
    return snapshots.get(0).timestamp();
  }

  /** Момент последнего снапшота набора. */
  public Instant end() {
    return snapshots.get(snapshots.size() - 1).timestamp();
  }

  /** Начальный кэш счёта прогона. */
  public BigDecimal initialCash() {
    return INITIAL_CASH;
  }

  /** Бары инструмента по возрастанию времени. */
  public List<Bar> bars(String ticker) {
    List<Bar> instrumentBars = bars.get(ticker);
    if (instrumentBars == null) {
      throw new IllegalArgumentException("В наборе нет инструмента " + ticker);
    }
    return instrumentBars;
  }

  /** Бар инструмента: момент начала, цена последней сделки и лучшие цены стакана. */
  public record Bar(
      Instant timestamp, BigDecimal last, BigDecimal bid, BigDecimal ask, long volume) {}

  private static List<InstrumentSpec> specs() {
    return List.of(
        new InstrumentSpec(
            SBER,
            TQBR,
            "STOCK",
            "Сбербанк",
            "Сбербанк",
            "RU0009029540",
            new BigDecimal("10"),
            new BigDecimal("0.01"),
            new BigDecimal("285.50"),
            0.012,
            -0.022,
            0.012,
            0.0006),
        new InstrumentSpec(
            GAZP,
            TQBR,
            "STOCK",
            "Газпром",
            "Газпром",
            "RU0007661625",
            new BigDecimal("10"),
            new BigDecimal("0.01"),
            new BigDecimal("165.20"),
            0.011,
            -0.021,
            0.011,
            0.0006),
        new InstrumentSpec(
            SI,
            SPBFUT,
            "FUTURES",
            "Si-12.26",
            "Фьючерс на доллар США",
            "",
            BigDecimal.ONE,
            BigDecimal.ONE,
            new BigDecimal("90000"),
            0.010,
            -0.019,
            0.010,
            0.0005));
  }

  private static List<LocalDate> generateTradingDates() {
    List<LocalDate> dates = new ArrayList<>(TRADING_DAYS);
    LocalDate date = FIRST_DATE;
    while (dates.size() < TRADING_DAYS) {
      if (date.getDayOfWeek() != DayOfWeek.SATURDAY && date.getDayOfWeek() != DayOfWeek.SUNDAY) {
        dates.add(date);
      }
      date = date.plusDays(1);
    }
    return List.copyOf(dates);
  }

  private static List<TradingDay> tradingDays(List<LocalDate> dates) {
    LocalDate first = dates.get(0).minusDays(1);
    LocalDate last = dates.get(dates.size() - 1).plusDays(1);
    List<TradingDay> days = new ArrayList<>();
    for (LocalDate date = first; !date.isAfter(last); date = date.plusDays(1)) {
      boolean trading = dates.contains(date);
      days.add(
          trading
              ? new TradingDay(date, true, sessionOpen(date), sessionClose(date))
              : new TradingDay(date, false, null, null));
    }
    return List.copyOf(days);
  }

  private List<Bar> generateBars(InstrumentSpec spec) {
    Random random = new Random(SEED * 31 + spec.ticker().hashCode());
    int scale = spec.minimumStep().scale();
    BigDecimal price = spec.basePrice().setScale(scale, RoundingMode.HALF_UP);
    List<Bar> result = new ArrayList<>(TRADING_DAYS * BARS_PER_DAY);
    for (int day = 0; day < TRADING_DAYS; day++) {
      double dailyDrift =
          day < 8 ? spec.riseDrift() : day < 14 ? spec.fallDrift() : spec.recoverDrift();
      double barDrift = dailyDrift / BARS_PER_DAY;
      Instant dayOpen = sessionOpen(tradingDates.get(day));
      for (int bar = 0; bar < BARS_PER_DAY; bar++) {
        double shock = random.nextGaussian() * spec.barVolatility();
        price =
            price
                .multiply(BigDecimal.valueOf(1.0 + barDrift + shock), MathContext.DECIMAL64)
                .setScale(scale, RoundingMode.HALF_UP);
        if (price.signum() <= 0) {
          price = spec.minimumStep();
        }
        BigDecimal tick = spec.minimumStep();
        result.add(
            new Bar(
                dayOpen.plus(BAR_STEP.multipliedBy(bar)),
                price,
                price.subtract(tick),
                price.add(tick),
                2 * TRADE_QUANTITY));
      }
    }
    return List.copyOf(result);
  }

  private void appendSnapshots(
      InstrumentSpec spec, List<Bar> instrumentBars, List<MarketDataSnapshot> stream) {
    int index = 0;
    for (LocalDate date : tradingDates) {
      BigDecimal open = instrumentBars.get(index).last();
      BigDecimal high = open;
      BigDecimal low = open;
      BigDecimal close = open;
      for (int bar = 0; bar < BARS_PER_DAY; bar++) {
        Bar current = instrumentBars.get(index++);
        high = high.max(current.last());
        low = low.min(current.last());
        close = current.last();
        Instant timestamp = current.timestamp().plusSeconds(1);
        stream.add(new QuoteSnapshot(timestamp, quote(spec, timestamp, open, high, low, close)));
        stream.add(new OrderBookEvent(timestamp, orderBook(spec, timestamp, current)));
        stream.add(
            new TradeEvent(
                timestamp,
                new Trade(
                    timestamp,
                    spec.ticker(),
                    spec.classCode(),
                    current.last(),
                    TRADE_QUANTITY,
                    TradeDirection.BUY)));
        stream.add(
            new TradeEvent(
                timestamp,
                new Trade(
                    timestamp,
                    spec.ticker(),
                    spec.classCode(),
                    current.last(),
                    TRADE_QUANTITY,
                    TradeDirection.SELL)));
      }
    }
  }

  private static Quote quote(
      InstrumentSpec spec,
      Instant timestamp,
      BigDecimal open,
      BigDecimal high,
      BigDecimal low,
      BigDecimal close) {
    return new Quote(
        spec.ticker(),
        spec.classCode(),
        timestamp,
        1,
        "RUB",
        close.subtract(spec.minimumStep()),
        close.add(spec.minimumStep()),
        open,
        close,
        high,
        low,
        close,
        BigDecimal.ZERO,
        BigDecimal.ZERO);
  }

  private static OrderBookSnapshot orderBook(InstrumentSpec spec, Instant timestamp, Bar bar) {
    List<OrderBookSnapshot.Level> bids = new ArrayList<>(BOOK_DEPTH);
    List<OrderBookSnapshot.Level> asks = new ArrayList<>(BOOK_DEPTH);
    for (int level = 1; level <= BOOK_DEPTH; level++) {
      BigDecimal offset = spec.minimumStep().multiply(BigDecimal.valueOf(level));
      bids.add(new OrderBookSnapshot.Level(bar.last().subtract(offset), BOOK_LEVEL_QUANTITY));
      asks.add(new OrderBookSnapshot.Level(bar.last().add(offset), BOOK_LEVEL_QUANTITY));
    }
    return new OrderBookSnapshot(
        spec.ticker(), spec.classCode(), BOOK_DEPTH, timestamp, bids, asks);
  }

  private static Instant sessionOpen(LocalDate date) {
    return date.atTime(SESSION_OPEN).toInstant(UTC);
  }

  private static Instant sessionClose(LocalDate date) {
    return date.atTime(SESSION_CLOSE).toInstant(UTC);
  }

  /** Описание инструмента и детерминированной модели его цены. */
  private record InstrumentSpec(
      String ticker,
      String classCode,
      String type,
      String shortName,
      String displayName,
      String isin,
      BigDecimal lotSize,
      BigDecimal minimumStep,
      BigDecimal basePrice,
      double riseDrift,
      double fallDrift,
      double recoverDrift,
      double barVolatility) {

    Instrument instrument() {
      return new Instrument(
          ticker, classCode, type, shortName, displayName, isin, lotSize, minimumStep, "RUB");
    }
  }
}
