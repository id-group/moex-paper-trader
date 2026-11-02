package ru.moex.papertrader.core.portfolio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;

class NavCalculatorTest {

  private static final Instant CLOSE = Instant.parse("2026-11-02T15:40:00Z");

  private final NavCalculator calculator = new NavCalculator();

  @Test
  void valuesStockHoldingsAtClosingPrices() {
    NavSnapshot snapshot =
        calculator.calculate(
            CLOSE,
            new BigDecimal("1000.00"),
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            Map.of("SBER", 10L, "GAZP", 5L),
            Map.of("SBER", new BigDecimal("285.50"), "GAZP", new BigDecimal("150.10")));

    assertThat(snapshot.asOf()).isEqualTo(CLOSE);
    assertThat(snapshot.cash()).isEqualByComparingTo("1000.00");
    assertThat(snapshot.commission()).isEqualByComparingTo("0.00");
    assertThat(snapshot.variationMargin()).isEqualByComparingTo("0.00");
    assertThat(snapshot.marketValue()).isEqualByComparingTo("3605.50");
    assertThat(snapshot.nav()).isEqualByComparingTo("4605.50");
  }

  @Test
  void addsAccruedVariationMarginToNav() {
    NavSnapshot snapshot =
        calculator.calculate(
            CLOSE,
            new BigDecimal("1000.00"),
            BigDecimal.ZERO,
            new BigDecimal("-250.00"),
            Map.of("SBER", 10L),
            Map.of("SBER", new BigDecimal("285.50")));

    assertThat(snapshot.nav()).isEqualByComparingTo("3605.00");
  }

  @Test
  void commissionOfPartialFillReducesNav() {
    NavSnapshot snapshot =
        calculator.calculate(
            CLOSE,
            new BigDecimal("2010.50"),
            new BigDecimal("1.25"),
            BigDecimal.ZERO,
            Map.of("SBER", 7L),
            Map.of("SBER", new BigDecimal("290.00")));

    assertThat(snapshot.marketValue()).isEqualByComparingTo("2030.00");
    assertThat(snapshot.nav()).isEqualByComparingTo("4039.25");
    assertThat(snapshot.netCash()).isEqualByComparingTo("2009.25");
  }

  @Test
  void emptyPortfolioNavIsNetCashPlusVariationMargin() {
    NavSnapshot snapshot =
        calculator.calculate(
            CLOSE,
            new BigDecimal("500.00"),
            new BigDecimal("0.50"),
            new BigDecimal("10.00"),
            Map.of(),
            Map.of());

    assertThat(snapshot.marketValue()).isEqualByComparingTo("0.00");
    assertThat(snapshot.nav()).isEqualByComparingTo("509.50");
  }

  @Test
  void shortStockHoldingHasNegativeMarketValue() {
    NavSnapshot snapshot =
        calculator.calculate(
            CLOSE,
            new BigDecimal("1000.00"),
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            Map.of("SBER", -10L),
            Map.of("SBER", new BigDecimal("100.00")));

    assertThat(snapshot.marketValue()).isEqualByComparingTo("-1000.00");
    assertThat(snapshot.nav()).isEqualByComparingTo("0.00");
  }

  @Test
  void roundsMarketValueHalfUpToTwoDecimals() {
    // 3 x 100.005 = 300.015 -> 300.02
    NavSnapshot snapshot =
        calculator.calculate(
            CLOSE,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            Map.of("SBER", 3L),
            Map.of("SBER", new BigDecimal("100.005")));

    assertThat(snapshot.marketValue()).isEqualByComparingTo("300.02");
  }

  @Test
  void rejectsHoldingWithoutClosingPrice() {
    assertThatThrownBy(
            () ->
                calculator.calculate(
                    CLOSE,
                    BigDecimal.ZERO,
                    BigDecimal.ZERO,
                    BigDecimal.ZERO,
                    Map.of("SBER", 1L),
                    Map.of()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("SBER");
  }

  @Test
  void rejectsNonPositiveClosingPrice() {
    assertThatThrownBy(
            () ->
                calculator.calculate(
                    CLOSE,
                    BigDecimal.ZERO,
                    BigDecimal.ZERO,
                    BigDecimal.ZERO,
                    Map.of("SBER", 1L),
                    Map.of("SBER", BigDecimal.ZERO)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("SBER");
  }

  @Test
  void rejectsNegativeCommission() {
    assertThatThrownBy(
            () ->
                calculator.calculate(
                    CLOSE,
                    BigDecimal.ZERO,
                    new BigDecimal("-1.00"),
                    BigDecimal.ZERO,
                    Map.of(),
                    Map.of()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("commission");
  }

  @Test
  void rejectsNullRequiredArguments() {
    assertThatThrownBy(
            () ->
                calculator.calculate(
                    null, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, Map.of(), Map.of()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("asOf");
    assertThatThrownBy(
            () ->
                calculator.calculate(
                    CLOSE, null, BigDecimal.ZERO, BigDecimal.ZERO, Map.of(), Map.of()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("cash");
    assertThatThrownBy(
            () ->
                calculator.calculate(
                    CLOSE, BigDecimal.ZERO, null, BigDecimal.ZERO, Map.of(), Map.of()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("commission");
    assertThatThrownBy(
            () ->
                calculator.calculate(
                    CLOSE, BigDecimal.ZERO, BigDecimal.ZERO, null, Map.of(), Map.of()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("variationMargin");
    assertThatThrownBy(
            () ->
                calculator.calculate(
                    CLOSE, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, null, Map.of()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("stockHoldings");
    assertThatThrownBy(
            () ->
                calculator.calculate(
                    CLOSE, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, Map.of(), null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("closingPrices");
  }

  @Test
  void snapshotListsHoldingsUsedForValuation() {
    NavSnapshot snapshot =
        calculator.calculate(
            CLOSE,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            Map.of("SBER", 10L),
            Map.of("SBER", new BigDecimal("100.00")));

    assertThat(snapshot.holdings())
        .containsExactly(new NavSnapshot.Holding("SBER", 10L, new BigDecimal("100.00")));
  }

  @Test
  void navIsNeverNegativeFromMarginLockOnly() {
    // ГО — блокировка, а не расход: без денег, но с активами NAV положителен.
    NavSnapshot snapshot =
        calculator.calculate(
            CLOSE,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            Map.of("SBER", 10L),
            Map.of("SBER", new BigDecimal("100.00")));

    assertThat(snapshot.nav()).isEqualByComparingTo("1000.00");
  }

  @Test
  void acceptsSnapshotOfManyHoldingsDeterministically() {
    NavSnapshot snapshot =
        calculator.calculate(
            CLOSE,
            new BigDecimal("100.00"),
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            Map.of("SBER", 1L, "GAZP", 2L, "LKOH", 3L),
            Map.of(
                "SBER", new BigDecimal("10.00"),
                "GAZP", new BigDecimal("20.00"),
                "LKOH", new BigDecimal("30.00")));

    assertThat(snapshot.marketValue()).isEqualByComparingTo("140.00");
    assertThat(snapshot.nav()).isEqualByComparingTo("240.00");
  }

  @Test
  void snapshotHoldingsAreImmutableAndOrderedByTicker() {
    NavSnapshot snapshot =
        calculator.calculate(
            CLOSE,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            Map.of("GAZP", 2L, "SBER", 1L),
            Map.of("GAZP", new BigDecimal("20.00"), "SBER", new BigDecimal("10.00")));

    assertThat(snapshot.holdings())
        .extracting(NavSnapshot.Holding::ticker)
        .containsExactly("GAZP", "SBER");
    assertThatThrownBy(
            () -> snapshot.holdings().add(new NavSnapshot.Holding("LKOH", 1L, BigDecimal.ONE)))
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void unusedMapEntriesAreIgnored() {
    NavSnapshot snapshot =
        calculator.calculate(
            CLOSE,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            Map.of("SBER", 1L),
            Map.of("SBER", new BigDecimal("10.00"), "GAZP", new BigDecimal("20.00")));

    assertThat(snapshot.holdings()).extracting(NavSnapshot.Holding::ticker).containsExactly("SBER");
  }
}
