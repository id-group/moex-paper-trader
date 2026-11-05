package ru.moex.papertrader.it.replay;

import java.math.BigDecimal;
import java.math.MathContext;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import ru.moex.papertrader.core.domain.Side;

/**
 * Тестовая стратегия-генератор: SMA-кроссовер на 15-минутных барах.
 *
 * <p>Стратегия нужна только для прогона цикла, а не как продукт. Правила детерминированы и смотрят
 * только назад: покупка на пересечении быстрой SMA(16) вверх через медленную SMA(64), полное
 * закрытие позиции — на обратном пересечении. Позиция всегда длинная и не более одной на
 * инструмент. Дополнительно набор содержит один внутридневной фьючерсный round trip SiU6: он
 * проверяет ГО и клиринг ВМ, оставаясь плоским к закрытию дня (так NAV сравнивается с эталоном без
 * расхождений в учёте фьючерсов).
 */
public final class SmaCrossoverStrategy {

  /** Период быстрой средней (баров): половина торгового дня. */
  public static final int FAST_PERIOD = 16;

  /** Период медленной средней (баров): два торговых дня. */
  public static final int SLOW_PERIOD = 64;

  /** Объём заявки SBER/GAZP (units); ниже 5% дневного ADV = 4800. */
  public static final long STOCK_QUANTITY = 1500;

  /** Объём заявки SiU6 (контрактов). */
  public static final long FUTURES_QUANTITY = 5;

  private static final Duration SIGNAL_DELAY = Duration.ofSeconds(2);
  private static final int FUTURES_DAY_INDEX = 10;

  private SmaCrossoverStrategy() {}

  /**
   * Сигналы стратегии по возрастанию времени.
   *
   * <p>Момент сигнала — начало бара плюс две секунды: к этому времени котировка и стакан этого бара
   * уже видимы REPLAY-провайдеру.
   */
  public static List<Signal> signals(ReplayDataset dataset) {
    List<Signal> signals = new ArrayList<>();
    signals.addAll(stockSignals(dataset, ReplayDataset.SBER, ReplayDataset.TQBR, STOCK_QUANTITY));
    signals.addAll(stockSignals(dataset, ReplayDataset.GAZP, ReplayDataset.TQBR, STOCK_QUANTITY));
    signals.addAll(futuresRoundTrip(dataset));
    signals.sort(Comparator.comparing(Signal::at).thenComparing(Signal::ticker));
    return List.copyOf(signals);
  }

  private static List<Signal> stockSignals(
      ReplayDataset dataset, String ticker, String classCode, long quantity) {
    List<ReplayDataset.Bar> bars = dataset.bars(ticker);
    List<Signal> signals = new ArrayList<>();
    boolean longPosition = false;
    for (int index = 0; index < bars.size(); index++) {
      BigDecimal fast = average(bars, index, FAST_PERIOD);
      BigDecimal slow = average(bars, index, SLOW_PERIOD);
      if (fast == null || slow == null) {
        continue;
      }
      // Состояние «быстрая выше медленной» вместо строгого пересечения: окно прогрева не съедает
      // начало тренда, и стратегия входит в уже установившийся тренд.
      boolean bullish = fast.compareTo(slow) > 0;
      if (!longPosition && bullish) {
        longPosition = true;
        signals.add(
            new Signal(
                bars.get(index).timestamp().plus(SIGNAL_DELAY),
                ticker,
                classCode,
                Side.BUY,
                quantity));
      } else if (longPosition && !bullish) {
        longPosition = false;
        signals.add(
            new Signal(
                bars.get(index).timestamp().plus(SIGNAL_DELAY),
                ticker,
                classCode,
                Side.SELL,
                quantity));
      }
    }
    return signals;
  }

  private static List<Signal> futuresRoundTrip(ReplayDataset dataset) {
    // Один детерминированный круг: проверка ГО/ВМ и фьючерсной комиссии; к закрытию дня позиция
    // закрыта, поэтому фьючерс не влияет на сравнение NAV.
    LocalDate date = dataset.tradingDates().get(FUTURES_DAY_INDEX);
    Instant entry = date.atTime(12, 2).toInstant(ZoneOffset.UTC);
    Instant exit = date.atTime(14, 2).toInstant(ZoneOffset.UTC);
    return List.of(
        new Signal(entry, ReplayDataset.SI, ReplayDataset.SPBFUT, Side.BUY, FUTURES_QUANTITY),
        new Signal(exit, ReplayDataset.SI, ReplayDataset.SPBFUT, Side.SELL, FUTURES_QUANTITY));
  }

  private static BigDecimal average(List<ReplayDataset.Bar> bars, int index, int period) {
    if (index + 1 < period) {
      return null;
    }
    BigDecimal sum = BigDecimal.ZERO;
    for (int offset = index + 1 - period; offset <= index; offset++) {
      sum = sum.add(bars.get(offset).last());
    }
    return sum.divide(BigDecimal.valueOf(period), MathContext.DECIMAL64);
  }

  /**
   * Сигнал стратегии.
   *
   * @param at момент исполнения (внутри сессии)
   * @param side направление заявки
   * @param quantity объём в units (для фьючерса — в контрактах)
   */
  public record Signal(Instant at, String ticker, String classCode, Side side, long quantity) {

    public Signal {
      // Момент сигнала должен быть целым числом секунд: так сравнимы моменты в разных прогонах.
      at = at.truncatedTo(ChronoUnit.SECONDS);
    }
  }
}
