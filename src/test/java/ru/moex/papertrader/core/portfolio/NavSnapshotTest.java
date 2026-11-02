package ru.moex.papertrader.core.portfolio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class NavSnapshotTest {

  private static final Instant CLOSE = Instant.parse("2026-11-02T15:40:00Z");

  @Test
  void exposesNetCashAfterCommission() {
    NavSnapshot snapshot = snapshot(new BigDecimal("1000.00"), new BigDecimal("2.50"));

    assertThat(snapshot.netCash()).isEqualByComparingTo("997.50");
  }

  @Test
  void holdsImmutableListOfHoldings() {
    List<NavSnapshot.Holding> mutable =
        new ArrayList<>(List.of(new NavSnapshot.Holding("SBER", 1L, new BigDecimal("10.00"))));
    NavSnapshot snapshot =
        new NavSnapshot(
            CLOSE,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            new BigDecimal("10.00"),
            new BigDecimal("10.00"),
            mutable);

    mutable.clear();

    assertThat(snapshot.holdings()).hasSize(1);
    assertThatThrownBy(() -> snapshot.holdings().clear())
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void rejectsNegativeCommission() {
    assertThatThrownBy(() -> snapshot(BigDecimal.ZERO, new BigDecimal("-0.01")))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("commission");
  }

  @Test
  void rejectsNullComponents() {
    assertThatThrownBy(
            () ->
                new NavSnapshot(
                    null,
                    BigDecimal.ZERO,
                    BigDecimal.ZERO,
                    BigDecimal.ZERO,
                    BigDecimal.ZERO,
                    BigDecimal.ZERO,
                    List.of()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("asOf");
    assertThatThrownBy(
            () ->
                new NavSnapshot(
                    CLOSE,
                    null,
                    BigDecimal.ZERO,
                    BigDecimal.ZERO,
                    BigDecimal.ZERO,
                    BigDecimal.ZERO,
                    List.of()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("cash");
    assertThatThrownBy(
            () ->
                new NavSnapshot(
                    CLOSE,
                    BigDecimal.ZERO,
                    BigDecimal.ZERO,
                    BigDecimal.ZERO,
                    BigDecimal.ZERO,
                    null,
                    List.of()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("nav");
    assertThatThrownBy(
            () ->
                new NavSnapshot(
                    CLOSE,
                    BigDecimal.ZERO,
                    BigDecimal.ZERO,
                    null,
                    BigDecimal.ZERO,
                    BigDecimal.ZERO,
                    List.of()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("variationMargin");
    assertThatThrownBy(
            () ->
                new NavSnapshot(
                    CLOSE,
                    BigDecimal.ZERO,
                    BigDecimal.ZERO,
                    BigDecimal.ZERO,
                    null,
                    BigDecimal.ZERO,
                    List.of()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("marketValue");
    assertThatThrownBy(
            () ->
                new NavSnapshot(
                    CLOSE,
                    BigDecimal.ZERO,
                    BigDecimal.ZERO,
                    BigDecimal.ZERO,
                    BigDecimal.ZERO,
                    BigDecimal.ZERO,
                    null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("holdings");
  }

  @Test
  void holdingRequiresTickerAndPositiveClosingPrice() {
    assertThatThrownBy(() -> new NavSnapshot.Holding(null, 1L, BigDecimal.ONE))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("ticker");
    assertThatThrownBy(() -> new NavSnapshot.Holding(" ", 1L, BigDecimal.ONE))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("ticker");
    assertThatThrownBy(() -> new NavSnapshot.Holding("SBER", 1L, null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("closingPrice");
    assertThatThrownBy(() -> new NavSnapshot.Holding("SBER", 1L, BigDecimal.ZERO))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("closingPrice");
  }

  private static NavSnapshot snapshot(BigDecimal cash, BigDecimal commission) {
    return new NavSnapshot(
        CLOSE,
        cash,
        commission,
        BigDecimal.ZERO,
        BigDecimal.ZERO,
        cash.subtract(commission),
        List.of());
  }
}
