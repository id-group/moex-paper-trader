package ru.moex.papertrader.core.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Разделение ГО фьючерсов на ГО заявок и ГО позиций и совместимость конструкторов. */
class PortfolioSnapshotTest {

  private static final Instant NOW = Instant.parse("2026-11-01T10:00:00Z");

  @Test
  void keepsFuturesMarginSplitWithTotalAsSum() {
    PortfolioSnapshot snapshot =
        new PortfolioSnapshot(
            "PAPER-0001",
            "RUB",
            new BigDecimal("1000000.00"),
            new BigDecimal("27000"),
            new BigDecimal("27000"),
            new BigDecimal("18000"),
            new BigDecimal("9000"),
            List.of(),
            NOW);

    assertThat(snapshot.lockedForFuturesOrders()).isEqualByComparingTo("18000");
    assertThat(snapshot.lockedForFuturesPositions()).isEqualByComparingTo("9000");
    assertThat(snapshot.lockedForFutures())
        .isEqualByComparingTo(
            snapshot.lockedForFuturesOrders().add(snapshot.lockedForFuturesPositions()));
  }

  @Test
  void normalizesTotalToSumOfSplitParts() {
    PortfolioSnapshot snapshot =
        new PortfolioSnapshot(
            "PAPER-0001",
            "RUB",
            new BigDecimal("1000000.00"),
            new BigDecimal("27000"),
            null,
            new BigDecimal("18000"),
            new BigDecimal("9000"),
            List.of(),
            NOW);

    assertThat(snapshot.lockedForFutures()).isEqualByComparingTo("27000");
  }

  @Test
  void normalizesMissingSplitPartsToZero() {
    PortfolioSnapshot snapshot =
        new PortfolioSnapshot(
            "PAPER-0001",
            "RUB",
            new BigDecimal("1000000.00"),
            BigDecimal.ZERO,
            null,
            null,
            null,
            List.of(),
            NOW);

    assertThat(snapshot.lockedForFutures()).isEqualByComparingTo("0");
    assertThat(snapshot.lockedForFuturesOrders()).isEqualByComparingTo("0");
    assertThat(snapshot.lockedForFuturesPositions()).isEqualByComparingTo("0");
  }

  @Test
  void legacyConstructorWithoutFuturesGivesZeroSplit() {
    PortfolioSnapshot snapshot =
        new PortfolioSnapshot(
            "PAPER-0001", "RUB", new BigDecimal("1000000.00"), BigDecimal.ZERO, List.of(), NOW);

    assertThat(snapshot.lockedForFutures()).isEqualByComparingTo("0");
    assertThat(snapshot.lockedForFuturesOrders()).isEqualByComparingTo("0");
    assertThat(snapshot.lockedForFuturesPositions()).isEqualByComparingTo("0");
  }

  @Test
  void legacyConstructorWithTotalTreatsItAsPositionsMargin() {
    PortfolioSnapshot snapshot =
        new PortfolioSnapshot(
            "PAPER-0001",
            "RUB",
            new BigDecimal("1000000.00"),
            new BigDecimal("18000"),
            new BigDecimal("18000"),
            List.of(),
            NOW);

    assertThat(snapshot.lockedForFuturesOrders()).isEqualByComparingTo("0");
    assertThat(snapshot.lockedForFuturesPositions()).isEqualByComparingTo("18000");
    assertThat(snapshot.lockedForFutures()).isEqualByComparingTo("18000");
  }
}
