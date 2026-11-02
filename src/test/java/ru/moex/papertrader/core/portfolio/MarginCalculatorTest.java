package ru.moex.papertrader.core.portfolio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.Map;
import org.junit.jupiter.api.Test;

class MarginCalculatorTest {

  private final MarginCalculator calculator = MarginCalculator.withDefaults();

  @Test
  void usesTenPercentOfPositionValueByDefault() {
    BigDecimal margin = calculator.initialMargin("SBER", 2, new BigDecimal("100.00"));

    assertThat(margin).isEqualByComparingTo("20.00");
    assertThat(margin.scale()).isEqualTo(2);
  }

  @Test
  void appliesPerInstrumentRateOverride() {
    MarginCalculator custom =
        new MarginCalculator(
            new MarginRates(new BigDecimal("0.10"), Map.of("RIZ6", new BigDecimal("0.20"))));

    assertThat(custom.initialMargin("RIZ6", 2, new BigDecimal("100.00")))
        .isEqualByComparingTo("40.00");
    assertThat(custom.initialMargin("SBER", 2, new BigDecimal("100.00")))
        .isEqualByComparingTo("20.00");
  }

  @Test
  void ignoresQuantitySignForShortPosition() {
    BigDecimal longMargin = calculator.initialMargin("RIZ6", 3, new BigDecimal("100.00"));
    BigDecimal shortMargin = calculator.initialMargin("RIZ6", -3, new BigDecimal("100.00"));

    assertThat(shortMargin).isEqualByComparingTo(longMargin);
  }

  @Test
  void roundsMoneyHalfUpToTwoDecimals() {
    // 1 x 100.05 x 0.10 = 10.005 -> 10.01
    assertThat(calculator.initialMargin("RIZ6", 1, new BigDecimal("100.05")))
        .isEqualByComparingTo("10.01");
  }

  @Test
  void rejectsNonPositivePrice() {
    assertThatThrownBy(() -> calculator.initialMargin("SBER", 1, BigDecimal.ZERO))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("price");
    assertThatThrownBy(() -> calculator.initialMargin("SBER", 1, null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("price");
  }
}
