package ru.moex.papertrader.core.marketdata;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import ru.moex.papertrader.core.domain.Instrument;

/**
 * Режим REPLAY: воспроизводит записанный таймлайн снапшотов по виртуальным часам.
 *
 * <p>Все чтения выполняются «на момент» {@link VirtualClock#instant()}: видны только снапшоты с
 * меткой времени не позже текущего виртуального момента. Никаких обращений к системным часам нет,
 * поэтому прогон детерминирован: одинаковый таймлайн и одинаковая последовательность сдвигов часов
 * дают одинаковые ответы.
 *
 * <p>Снапшоты сортируются по метке времени устойчиво (при равных метках сохраняется исходный
 * порядок), что делает выбор «последнего» снапшота предсказуемым.
 */
public final class ReplayMarketDataProvider implements MarketDataProvider {

  private static final ZoneId MOSCOW = ZoneId.of("Europe/Moscow");
  private static final int SESSION_ID = 1;
  private static final String SESSION_NAME = "Основная сессия";

  /** Глубина истории ADV в торговых днях, как в контракте {@link MarketDataProvider#adv}. */
  private static final int ADV_DAYS = 30;

  private final List<MarketDataSnapshot> timeline;
  private final VirtualClock clock;
  private final Map<String, Instrument> instruments;
  private final List<TradingDay> calendar;

  /**
   * @param timeline снапшоты в любом порядке; внутри копируются и сортируются по метке времени
   * @param clock виртуальные часы таймлайна
   * @param instruments справочник инструментов с ключом {@code "ticker|classCode"}
   * @param calendar торговый календарь
   */
  public ReplayMarketDataProvider(
      List<MarketDataSnapshot> timeline,
      VirtualClock clock,
      Map<String, Instrument> instruments,
      List<TradingDay> calendar) {
    Objects.requireNonNull(timeline, "timeline");
    this.clock = Objects.requireNonNull(clock, "clock");
    this.instruments = Map.copyOf(Objects.requireNonNull(instruments, "instruments"));
    this.timeline = sortedTimeline(timeline);
    this.calendar = sortedCalendar(calendar);
  }

  @Override
  public List<Instrument> instruments(Collection<String> tickers) {
    Objects.requireNonNull(tickers, "tickers");
    List<Instrument> result = new ArrayList<>();
    for (String ticker : new LinkedHashSet<>(tickers)) {
      for (Instrument instrument : instruments.values()) {
        if (instrument.ticker().equals(ticker)) {
          result.add(instrument);
          break;
        }
      }
    }
    return List.copyOf(result);
  }

  @Override
  public Optional<Instrument> instrument(String ticker, String classCode) {
    return Optional.ofNullable(instruments.get(key(ticker, classCode)));
  }

  @Override
  public Optional<Quote> quote(Instrument instrument) {
    Objects.requireNonNull(instrument, "instrument");
    if (!instruments.containsKey(key(instrument.ticker(), instrument.classCode()))) {
      return Optional.empty();
    }
    Instant now = clock.instant();
    for (int i = timeline.size() - 1; i >= 0; i--) {
      MarketDataSnapshot snapshot = timeline.get(i);
      if (!snapshot.timestamp().isAfter(now)
          && snapshot instanceof QuoteSnapshot quoteSnapshot
          && quoteSnapshot.ticker().equals(instrument.ticker())) {
        return Optional.of(quoteSnapshot.quote());
      }
    }
    return Optional.empty();
  }

  @Override
  public OrderBookSnapshot orderBook(Instrument instrument, int depth) {
    Objects.requireNonNull(instrument, "instrument");
    if (!instruments.containsKey(key(instrument.ticker(), instrument.classCode()))) {
      throw new IllegalArgumentException("Инструмент " + instrument.ticker() + " не найден");
    }
    Instant now = clock.instant();
    for (int i = timeline.size() - 1; i >= 0; i--) {
      MarketDataSnapshot snapshot = timeline.get(i);
      if (!snapshot.timestamp().isAfter(now)
          && snapshot instanceof OrderBookEvent event
          && event.ticker().equals(instrument.ticker())) {
        return event.orderBook();
      }
    }
    return new OrderBookSnapshot(
        instrument.ticker(), instrument.classCode(), depth, now, List.of(), List.of());
  }

  @Override
  public List<Trade> trades(Instrument instrument, Instant from, Instant to) {
    Objects.requireNonNull(instrument, "instrument");
    Objects.requireNonNull(from, "from");
    Objects.requireNonNull(to, "to");
    Instant now = clock.instant();
    List<Trade> result = new ArrayList<>();
    for (MarketDataSnapshot snapshot : timeline) {
      if (!(snapshot instanceof TradeEvent event)) {
        continue;
      }
      Instant ts = event.timestamp();
      if (!ts.isBefore(from)
          && !ts.isAfter(to)
          && !ts.isAfter(now)
          && event.ticker().equals(instrument.ticker())) {
        result.add(event.trade());
      }
    }
    return List.copyOf(result);
  }

  @Override
  public CandleSeries candles(
      Instrument instrument, Instant from, Instant to, TimeFrame timeFrame) {
    Objects.requireNonNull(instrument, "instrument");
    Objects.requireNonNull(from, "from");
    Objects.requireNonNull(to, "to");
    Objects.requireNonNull(timeFrame, "timeFrame");
    Instant now = clock.instant();
    Duration step = timeFrame.step();
    Map<Instant, CandleAccumulator> windows = new TreeMap<>();
    for (MarketDataSnapshot snapshot : timeline) {
      if (!(snapshot instanceof TradeEvent event)) {
        continue;
      }
      Instant ts = event.timestamp();
      // Свеча покрывает полуинтервал [from, to): граница to принадлежит следующему окну.
      if (ts.isBefore(from)
          || !ts.isBefore(to)
          || ts.isAfter(now)
          || !event.ticker().equals(instrument.ticker())) {
        continue;
      }
      windows
          .computeIfAbsent(windowStart(ts, step), time -> new CandleAccumulator())
          .add(event.trade());
    }
    List<Candle> bars = new ArrayList<>(windows.size());
    for (Map.Entry<Instant, CandleAccumulator> entry : windows.entrySet()) {
      bars.add(entry.getValue().toCandle(entry.getKey()));
    }
    return new CandleSeries(instrument.ticker(), instrument.classCode(), from, to, timeFrame, bars);
  }

  @Override
  public BigDecimal adv(Instrument instrument) {
    Objects.requireNonNull(instrument, "instrument");
    Instant now = clock.instant();
    Map<LocalDate, Long> volumeByDay = new TreeMap<>();
    for (MarketDataSnapshot snapshot : timeline) {
      if (!(snapshot instanceof TradeEvent event)) {
        continue;
      }
      Instant ts = event.timestamp();
      if (ts.isAfter(now) || !event.ticker().equals(instrument.ticker())) {
        continue;
      }
      LocalDate day = ts.atZone(MOSCOW).toLocalDate();
      volumeByDay.merge(day, event.trade().quantity(), Long::sum);
    }
    if (volumeByDay.isEmpty()) {
      return BigDecimal.ZERO;
    }
    List<LocalDate> days = new ArrayList<>(volumeByDay.keySet());
    List<LocalDate> window = days.subList(Math.max(0, days.size() - ADV_DAYS), days.size());
    long total = 0L;
    for (LocalDate day : window) {
      total = Math.addExact(total, volumeByDay.get(day));
    }
    return BigDecimal.valueOf(total)
        .divide(BigDecimal.valueOf(window.size()), 2, RoundingMode.HALF_UP);
  }

  @Override
  public TradingSession tradingSession(Instant at) {
    Objects.requireNonNull(at, "at");
    if (calendar.isEmpty()) {
      return new TradingSession(SESSION_ID, SESSION_NAME, false, at);
    }
    TradingDay current = findDay(at.atZone(MOSCOW).toLocalDate());
    if (current != null && isOpen(current, at)) {
      return new TradingSession(SESSION_ID, SESSION_NAME, true, current.closeUtc());
    }
    Instant nextOpen = nextOpenAfter(at);
    return new TradingSession(SESSION_ID, SESSION_NAME, false, nextOpen != null ? nextOpen : at);
  }

  @Override
  public List<TradingDay> tradingDays(Instant from, Instant to) {
    Objects.requireNonNull(from, "from");
    Objects.requireNonNull(to, "to");
    LocalDate fromDate = from.atZone(MOSCOW).toLocalDate();
    LocalDate toDate = to.atZone(MOSCOW).toLocalDate();
    List<TradingDay> result = new ArrayList<>();
    for (TradingDay day : calendar) {
      if (!day.date().isBefore(fromDate) && !day.date().isAfter(toDate)) {
        result.add(day);
      }
    }
    return List.copyOf(result);
  }

  /** Открытая сессия: день торговый и момент внутри [open, close]. */
  private static boolean isOpen(TradingDay day, Instant at) {
    return day.trading()
        && day.openUtc() != null
        && day.closeUtc() != null
        && !at.isBefore(day.openUtc())
        && !at.isAfter(day.closeUtc());
  }

  private TradingDay findDay(LocalDate date) {
    for (TradingDay day : calendar) {
      if (day.date().equals(date)) {
        return day;
      }
    }
    return null;
  }

  /** Ближайшее открытие торгов после момента; {@code null} — в календаре больше нет торгов. */
  private Instant nextOpenAfter(Instant at) {
    for (TradingDay day : calendar) {
      if (day.trading() && day.openUtc() != null && day.openUtc().isAfter(at)) {
        return day.openUtc();
      }
    }
    return null;
  }

  private static List<MarketDataSnapshot> sortedTimeline(List<MarketDataSnapshot> timeline) {
    List<MarketDataSnapshot> sorted = new ArrayList<>(timeline.size());
    for (MarketDataSnapshot snapshot : timeline) {
      sorted.add(Objects.requireNonNull(snapshot, "snapshot"));
    }
    // List.sort устойчив: при равных метках сохраняется порядок записи.
    sorted.sort(Comparator.comparing(MarketDataSnapshot::timestamp));
    return List.copyOf(sorted);
  }

  private static List<TradingDay> sortedCalendar(List<TradingDay> calendar) {
    Objects.requireNonNull(calendar, "calendar");
    List<TradingDay> sorted = new ArrayList<>(calendar.size());
    for (TradingDay day : calendar) {
      sorted.add(Objects.requireNonNull(day, "day"));
    }
    sorted.sort(Comparator.comparing(TradingDay::date));
    return List.copyOf(sorted);
  }

  /** Начало окна свечи: floor((timestamp - EPOCH) / step) * step. */
  private static Instant windowStart(Instant timestamp, Duration step) {
    long stepNanos = step.toNanos();
    long offsetNanos = Duration.between(Instant.EPOCH, timestamp).toNanos();
    long windowIndex = Math.floorDiv(offsetNanos, stepNanos);
    return Instant.EPOCH.plusNanos(Math.multiplyExact(windowIndex, stepNanos));
  }

  private static String key(String ticker, String classCode) {
    return ticker + "|" + classCode;
  }

  /** Накопитель свечи: первый и последний трейд окна, экстремумы и суммарный объём. */
  private static final class CandleAccumulator {

    private BigDecimal open;
    private BigDecimal close;
    private BigDecimal high;
    private BigDecimal low;
    private long volume;

    void add(Trade trade) {
      BigDecimal price = trade.price();
      if (open == null) {
        open = price;
        high = price;
        low = price;
      } else {
        high = high.max(price);
        low = low.min(price);
      }
      close = price;
      volume = Math.addExact(volume, trade.quantity());
    }

    Candle toCandle(Instant time) {
      return new Candle(time, open, close, high, low, BigDecimal.valueOf(volume));
    }
  }
}
