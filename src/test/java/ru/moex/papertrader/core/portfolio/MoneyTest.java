package ru.moex.papertrader.core.portfolio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.math.RoundingMode;
import org.junit.jupiter.api.Test;

class MoneyTest {

  @Test
  void roundsHalfUpToTwoDecimals() {
    assertThat(Money.round(new BigDecimal("10.005"))).isEqualByComparingTo("10.01");
    assertThat(Money.round(new BigDecimal("10.004"))).isEqualByComparingTo("10.00");
  }

  @Test
  void exposesSingleScaleAndRoundingMode() {
    assertThat(Money.SCALE).isEqualTo(2);
    assertThat(Money.ROUNDING_MODE).isEqualTo(RoundingMode.HALF_UP);
    assertThat(Money.ZERO.scale()).isEqualTo(Money.SCALE);
  }

  @Test
  void rejectsNullAmount() {
    assertThatThrownBy(() -> Money.round(null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("amount");
  }
}
