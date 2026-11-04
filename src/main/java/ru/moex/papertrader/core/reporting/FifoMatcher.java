package ru.moex.papertrader.core.reporting;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import ru.moex.papertrader.core.domain.Side;

/**
 * FIFO-сопоставление входов и выходов по каждому инструменту.
 *
 * <p>Сделки обрабатываются в хронологическом порядке (при равных метках — в порядке входа).
 * Входящая сделка сначала закрывает противоположные открытые лоты, остаток открывает новый лот.
 * Частичное закрытие уменьшает лот и создаёт отдельную закрытую пару, поэтому результат виден по
 * каждому куску. Незакрытые лоты в результат не попадают: это нереализованный результат.
 *
 * <p>Комиссия каждой сделки распределяется по количеству ({@code commission / quantity}) и
 * вычитается из результата закрытой пары вместе с комиссией входа и выхода. Метка стратегии пары —
 * метка сделки входа.
 */
public final class FifoMatcher {

  private static final int RATE_SCALE = 10;
  private static final int MONEY_SCALE = 2;

  /** Закрытые пары по журналу сделок. */
  public List<ClosedTrade> match(List<TradeJournalEntry> entries) {
    Objects.requireNonNull(entries, "entries");
    List<TradeJournalEntry> ordered = new ArrayList<>(entries.size());
    for (TradeJournalEntry entry : entries) {
      ordered.add(Objects.requireNonNull(entry, "entry"));
    }
    // List.sort устойчив: при равных метках времени сохраняется порядок исходного журнала.
    ordered.sort(Comparator.comparing(TradeJournalEntry::executedAt));

    Map<String, Deque<OpenLot>> openLots = new LinkedHashMap<>();
    List<ClosedTrade> closed = new ArrayList<>();
    for (TradeJournalEntry entry : ordered) {
      Deque<OpenLot> lots = openLots.computeIfAbsent(key(entry), ignored -> new ArrayDeque<>());
      long remaining = entry.quantity();
      BigDecimal exitCommissionPerUnit = commissionPerUnit(entry.commission(), entry.quantity());

      while (remaining > 0 && !lots.isEmpty()) {
        OpenLot lot = lots.peekFirst();
        if (lot.side == entry.side()) {
          break;
        }
        long matched = Math.min(remaining, lot.quantity);
        closed.add(close(lot, entry, matched, exitCommissionPerUnit));
        remaining -= matched;
        lot.quantity -= matched;
        if (lot.quantity == 0) {
          lots.removeFirst();
        }
      }

      if (remaining > 0) {
        lots.addLast(
            new OpenLot(
                entry.side(),
                entry.price(),
                remaining,
                commissionPerUnit(entry.commission(), entry.quantity()),
                entry.strategyId(),
                entry.executedAt(),
                entry.orderId()));
      }
    }
    return List.copyOf(closed);
  }

  private static ClosedTrade close(
      OpenLot lot, TradeJournalEntry exit, long quantity, BigDecimal exitCommissionPerUnit) {
    BigDecimal gross =
        lot.side == Side.BUY ? exit.price().subtract(lot.price) : lot.price.subtract(exit.price());
    BigDecimal pnl =
        gross
            .multiply(BigDecimal.valueOf(quantity))
            .subtract(lot.commissionPerUnit.multiply(BigDecimal.valueOf(quantity)))
            .subtract(exitCommissionPerUnit.multiply(BigDecimal.valueOf(quantity)))
            .setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    return new ClosedTrade(
        exit.ticker(),
        exit.classCode(),
        lot.strategyId,
        lot.side,
        quantity,
        lot.price,
        exit.price(),
        pnl,
        lot.entryAt,
        exit.executedAt(),
        lot.orderId,
        exit.orderId());
  }

  private static BigDecimal commissionPerUnit(BigDecimal commission, long quantity) {
    return commission.divide(BigDecimal.valueOf(quantity), RATE_SCALE, RoundingMode.HALF_UP);
  }

  private static String key(TradeJournalEntry entry) {
    return entry.ticker() + "|" + entry.classCode();
  }

  /** Открытый лот: сторона входа, цена, остаток и распределённая комиссия на единицу. */
  private static final class OpenLot {

    private final Side side;
    private final BigDecimal price;
    private final BigDecimal commissionPerUnit;
    private final String strategyId;
    private final Instant entryAt;
    private final String orderId;
    private long quantity;

    private OpenLot(
        Side side,
        BigDecimal price,
        long quantity,
        BigDecimal commissionPerUnit,
        String strategyId,
        Instant entryAt,
        String orderId) {
      this.side = side;
      this.price = price;
      this.quantity = quantity;
      this.commissionPerUnit = commissionPerUnit;
      this.strategyId = strategyId;
      this.entryAt = entryAt;
      this.orderId = orderId;
    }
  }
}
