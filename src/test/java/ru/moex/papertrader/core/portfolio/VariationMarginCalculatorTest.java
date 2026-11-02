package ru.moex.papertrader.core.portfolio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class VariationMarginCalculatorTest {

  private final VariationMarginCalculator calculator = new VariationMarginCalculator();

  @Test
  void longPositionGainsWhenSettlementPriceRises() {
    BigDecimal margin =
        calculator.variationMargin(10, new BigDecimal("110.00"), new BigDecimal("100.00"));

    assertThat(margin).isEqualByComparingTo("100.00");
  }

  @Test
  void longPositionLosesWhenSettlementPriceFalls() {
    BigDecimal margin =
        calculator.variationMargin(10, new BigDecimal("100.00"), new BigDecimal("110.00"));

    assertThat(margin).isEqualByComparingTo("-100.00");
  }

  @Test
  void shortPositionLosesWhenSettlementPriceRises() {
    BigDecimal margin =
        calculator.variationMargin(-10, new BigDecimal("110.00"), new BigDecimal("100.00"));

    assertThat(margin).isEqualByComparingTo("-100.00");
  }

  @Test
  void shortPositionGainsWhenSettlementPriceFalls() {
    BigDecimal margin =
        calculator.variationMargin(-10, new BigDecimal("100.00"), new BigDecimal("110.00"));

    assertThat(margin).isEqualByComparingTo("100.00");
  }

  @Test
  void returnsZeroWhenSettlementPriceIsUnchanged() {
    BigDecimal margin =
        calculator.variationMargin(5, new BigDecimal("100.00"), new BigDecimal("100.00"));

    assertThat(margin).isEqualByComparingTo("0.00");
    assertThat(margin.scale()).isEqualTo(2);
  }

  @Test
  void roundsVariationMarginHalfUpToTwoDecimals() {
    // 3 x 0.005 = 0.015 -> 0.02
    BigDecimal margin =
        calculator.variationMargin(3, new BigDecimal("100.005"), new BigDecimal("100.000"));

    assertThat(margin).isEqualByComparingTo("0.02");
  }

  @Test
  void rejectsNullPrices() {
    assertThatThrownBy(() -> calculator.variationMargin(1, null, BigDecimal.ONE))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("todaySettlementPrice");
    assertThatThrownBy(() -> calculator.variationMargin(1, BigDecimal.ONE, null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("previousSettlementPrice");
  }
}
