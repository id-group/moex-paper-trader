package ru.moex.papertrader.core.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Брокер-независимый срез портфеля (ADR-5).
 *
 * <p>{@code lockedForFutures} — часть {@code lockedCash}, заблокированная под гарантийное
 * обеспечение фьючерсов; для контракта БКС она выделяется отдельным полем. Блокировка разделена на
 * ГО активных заявок ({@code lockedForFuturesOrders}) и ГО открытых позиций ({@code
 * lockedForFuturesPositions}); инвариант {@code lockedForFutures = orders + positions}
 * нормализуется в конструкторе. {@code null} нормализуется в ноль: снапшот этапа 2, где фьючерсов
 * ещё не было, остаётся валидным.
 */
public record PortfolioSnapshot(
    String account,
    String currency,
    BigDecimal cash,
    BigDecimal lockedCash,
    BigDecimal lockedForFutures,
    BigDecimal lockedForFuturesOrders,
    BigDecimal lockedForFuturesPositions,
    List<Position> positions,
    Instant asOf) {

  public PortfolioSnapshot {
    boolean splitMissing = lockedForFuturesOrders == null && lockedForFuturesPositions == null;
    lockedForFutures = lockedForFutures == null ? BigDecimal.ZERO : lockedForFutures;
    lockedForFuturesOrders =
        lockedForFuturesOrders == null ? BigDecimal.ZERO : lockedForFuturesOrders;
    if (lockedForFuturesPositions == null) {
      // Совместимость со снапшотом без разделения: вся блокировка считалась ГО позиций.
      lockedForFuturesPositions =
          splitMissing ? lockedForFutures : lockedForFutures.subtract(lockedForFuturesOrders);
    }
    // Инвариант: общая блокировка под фьючерсы — ровно сумма ГО заявок и ГО позиций.
    lockedForFutures = lockedForFuturesOrders.add(lockedForFuturesPositions);
    positions = List.copyOf(positions);
  }

  /** Снапшот без отдельного учёта ГО фьючерсов (этап 2): {@code lockedForFutures = 0}. */
  public PortfolioSnapshot(
      String account,
      String currency,
      BigDecimal cash,
      BigDecimal lockedCash,
      List<Position> positions,
      Instant asOf) {
    this(account, currency, cash, lockedCash, BigDecimal.ZERO, null, null, positions, asOf);
  }

  /**
   * Снапшот с общей блокировкой под фьючерсы. Разделение на ГО заявок и ГО позиций неизвестно: вся
   * сумма считается ГО позиций ({@code lockedForFuturesOrders = 0}).
   */
  public PortfolioSnapshot(
      String account,
      String currency,
      BigDecimal cash,
      BigDecimal lockedCash,
      BigDecimal lockedForFutures,
      List<Position> positions,
      Instant asOf) {
    this(account, currency, cash, lockedCash, lockedForFutures, null, null, positions, asOf);
  }

  public BigDecimal freeCash() {
    return cash.subtract(lockedCash);
  }
}
