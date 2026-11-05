package ru.moex.papertrader.it.replay;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;
import ru.moex.papertrader.core.domain.Instrument;
import ru.moex.papertrader.core.domain.Side;
import ru.moex.papertrader.core.reporting.MetricsCalculator;
import ru.moex.papertrader.core.reporting.NavPoint;
import ru.moex.papertrader.core.reporting.PerformanceMetrics;

/**
 * Эталонный («идеальный») бэктест стратегии по тому же REPLAY-набору.
 *
 * <p>Исполнение — по ценам стакана без проскальзывания и без комиссий: покупка по лучшему ask,
 * продажа по лучшему bid. Это нижняя граница издержек эмулятора, поэтому расхождение метрик
 * показывает суммарный эффект модели исполнения и тарифов, а не разницу стратегий.
 *
 * <p>NAV считается по формуле эмулятора ({@code MarginSettlementServiceImpl}): кэш плюс рыночная
 * стоимость нефинансовых инструментов; результат фьючерсной позиции уходит в кэш и к закрытию дня
 * позиция закрыта, поэтому отдельного слагаемого рыночной стоимости у фьючерса нет.
 */
public final class IdealBacktest {

  private static final BigDecimal RISK_FREE_RATE = new BigDecimal("0.16");
  private static final int MONEY_SCALE = 2;
  private static final int RATE_SCALE = 6;

  private IdealBacktest() {}

  /** Эталонный результат: дневной NAV, метрики и закрытые FIFO-пары. */
  public record IdealResult(
      List<NavPoint> nav, Map<String, BigDecimal> metrics, List<TradePair> closedTrades) {

    public IdealResult {
      nav = List.copyOf(nav);
      metrics = Map.copyOf(metrics);
      closedTrades = List.copyOf(closedTrades);
    }
  }

  /** Закрытая FIFO-пара: покупка и продажа одного инструмента. */
  public record TradePair(String ticker, BigDecimal pnl) {}

  /** Прогоняет стратегию по набору и возвращает эталонные метрики для сверки. */
  public static IdealResult run(ReplayDataset dataset) {
    Map<String, NavigableMap<Instant, ReplayDataset.Bar>> barsByTicker = barIndex(dataset);
    Set<String> futures =
        dataset.instruments().stream()
            .filter(instrument -> "FUTURES".equalsIgnoreCase(instrument.type()))
            .map(Instrument::ticker)
            .collect(Collectors.toUnmodifiableSet());

    List<SmaCrossoverStrategy.Signal> signals = SmaCrossoverStrategy.signals(dataset);
    Map<String, Long> positions = new HashMap<>();
    Map<String, Deque<Lot>> openLots = new HashMap<>();
    List<TradePair> closed = new ArrayList<>();
    List<NavPoint> nav = new ArrayList<>();
    BigDecimal cash = dataset.initialCash();

    int index = 0;
    for (LocalDate date : dataset.tradingDates()) {
      while (index < signals.size() && dateOf(signals.get(index)).equals(date)) {
        SmaCrossoverStrategy.Signal signal = signals.get(index++);
        Map.Entry<Instant, ReplayDataset.Bar> barEntry =
            barsByTicker.get(signal.ticker()).floorEntry(signal.at());
        if (barEntry == null) {
          throw new IllegalStateException("Нет бара для сигнала " + signal);
        }
        ReplayDataset.Bar bar = barEntry.getValue();
        BigDecimal price = signal.side() == Side.BUY ? bar.ask() : bar.bid();
        BigDecimal turnover = price.multiply(BigDecimal.valueOf(signal.quantity()));
        if (signal.side() == Side.BUY) {
          cash = cash.subtract(turnover);
          positions.merge(signal.ticker(), signal.quantity(), Long::sum);
          openLots
              .computeIfAbsent(signal.ticker(), key -> new ArrayDeque<>())
              .addLast(new Lot(price, signal.quantity()));
        } else {
          cash = cash.add(turnover);
          long remaining = signal.quantity();
          Deque<Lot> lots = openLots.getOrDefault(signal.ticker(), new ArrayDeque<>());
          while (remaining > 0) {
            Lot lot = lots.peekFirst();
            if (lot == null) {
              throw new IllegalStateException("Продажа без открытой позиции: " + signal);
            }
            long matched = Math.min(remaining, lot.quantity());
            closed.add(
                new TradePair(
                    signal.ticker(),
                    price
                        .subtract(lot.price())
                        .multiply(BigDecimal.valueOf(matched))
                        .setScale(MONEY_SCALE, RoundingMode.HALF_UP)));
            remaining -= matched;
            if (matched == lot.quantity()) {
              lots.removeFirst();
            } else {
              lots.removeFirst();
              lots.addFirst(new Lot(lot.price(), lot.quantity() - matched));
            }
          }
          positions.merge(signal.ticker(), -signal.quantity(), Long::sum);
        }
      }

      BigDecimal navValue = cash;
      for (Map.Entry<String, Long> position : positions.entrySet()) {
        if (position.getValue() == 0) {
          continue;
        }
        if (futures.contains(position.getKey())) {
          throw new IllegalStateException(
              "Фьючерсная позиция не закрыта к концу дня: " + position.getKey());
        }
        navValue =
            navValue.add(
                lastBar(dataset, position.getKey(), date)
                    .last()
                    .multiply(BigDecimal.valueOf(position.getValue())));
      }
      nav.add(new NavPoint(date, navValue.setScale(MONEY_SCALE, RoundingMode.HALF_UP)));
    }

    PerformanceMetrics performance = new MetricsCalculator(RISK_FREE_RATE).calculate(nav);
    Map<String, BigDecimal> metrics = new LinkedHashMap<>();
    putIfPresent(metrics, "cagr", performance.cagr());
    putIfPresent(metrics, "sharpe", performance.sharpe());
    putIfPresent(metrics, "sortino", performance.sortino());
    putIfPresent(metrics, "maxDrawdown", performance.maxDrawdown());
    putIfPresent(metrics, "calmar", performance.calmar());
    putIfPresent(metrics, "winRate", winRate(closed));
    return new IdealResult(nav, metrics, closed);
  }

  private static Map<String, NavigableMap<Instant, ReplayDataset.Bar>> barIndex(
      ReplayDataset dataset) {
    Map<String, NavigableMap<Instant, ReplayDataset.Bar>> bars = new HashMap<>();
    for (Instrument instrument : dataset.instruments()) {
      NavigableMap<Instant, ReplayDataset.Bar> byTimestamp = new TreeMap<>();
      for (ReplayDataset.Bar bar : dataset.bars(instrument.ticker())) {
        byTimestamp.put(bar.timestamp(), bar);
      }
      bars.put(instrument.ticker(), Collections.unmodifiableNavigableMap(byTimestamp));
    }
    return Map.copyOf(bars);
  }

  private static ReplayDataset.Bar lastBar(ReplayDataset dataset, String ticker, LocalDate date) {
    List<ReplayDataset.Bar> bars = dataset.bars(ticker);
    ReplayDataset.Bar last = null;
    for (ReplayDataset.Bar bar : bars) {
      if (bar.timestamp().atZone(ZoneOffset.UTC).toLocalDate().isAfter(date)) {
        break;
      }
      last = bar;
    }
    if (last == null) {
      throw new IllegalStateException("Нет баров " + ticker + " к " + date);
    }
    return last;
  }

  private static BigDecimal winRate(List<TradePair> closed) {
    if (closed.isEmpty()) {
      return null;
    }
    long profitable = closed.stream().filter(pair -> pair.pnl().signum() > 0).count();
    return BigDecimal.valueOf(profitable)
        .divide(BigDecimal.valueOf(closed.size()), RATE_SCALE, RoundingMode.HALF_UP);
  }

  private static LocalDate dateOf(SmaCrossoverStrategy.Signal signal) {
    return signal.at().atZone(ZoneOffset.UTC).toLocalDate();
  }

  private static void putIfPresent(Map<String, BigDecimal> metrics, String name, BigDecimal value) {
    if (value != null) {
      metrics.put(name, value);
    }
  }

  private record Lot(BigDecimal price, long quantity) {}
}
