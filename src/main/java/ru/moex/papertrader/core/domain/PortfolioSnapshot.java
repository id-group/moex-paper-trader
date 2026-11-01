package ru.moex.papertrader.core.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** Брокер-независимый срез портфеля (ADR-5). */
public record PortfolioSnapshot(
    String account,
    String currency,
    BigDecimal cash,
    BigDecimal lockedCash,
    List<Position> positions,
    Instant asOf) {

  public PortfolioSnapshot {
    positions = List.copyOf(positions);
  }

  public BigDecimal freeCash() {
    return cash.subtract(lockedCash);
  }
}
