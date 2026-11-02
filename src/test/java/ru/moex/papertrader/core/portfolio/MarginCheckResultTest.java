package ru.moex.papertrader.core.portfolio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class MarginCheckResultTest {

  @Test
  void permitHasNoReason() {
    MarginCheckResult result =
        MarginCheckResult.permit(new BigDecimal("100.00"), new BigDecimal("20.00"));

    assertThat(result.allowed()).isTrue();
    assertThat(result.reason()).isEmpty();
  }

  @Test
  void rejectCarriesReason() {
    MarginCheckResult result =
        MarginCheckResult.reject(new BigDecimal("10.00"), new BigDecimal("20.00"), "мало денег");

    assertThat(result.allowed()).isFalse();
    assertThat(result.reason()).isEqualTo("мало денег");
  }

  @Test
  void nullReasonBecomesEmpty() {
    MarginCheckResult result = new MarginCheckResult(false, BigDecimal.ONE, BigDecimal.TEN, null);

    assertThat(result.reason()).isEmpty();
  }

  @Test
  void rejectsNullAmounts() {
    assertThatThrownBy(() -> new MarginCheckResult(true, null, BigDecimal.ONE, ""))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("availableCash");
    assertThatThrownBy(() -> new MarginCheckResult(true, BigDecimal.ONE, null, ""))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("requiredMargin");
  }
}
