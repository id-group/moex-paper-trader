package ru.moex.papertrader.core.portfolio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class MarginCheckTest {

  private final MarginCheck marginCheck = MarginCheck.withDefaults();

  @Test
  void permitsTradeWhenAvailableCashCoversRequiredMargin() {
    MarginCheckResult result =
        marginCheck.checkTrade("RIZ6", 2, new BigDecimal("100.00"), new BigDecimal("1000.00"));

    assertThat(result.allowed()).isTrue();
    assertThat(result.availableCash()).isEqualByComparingTo("1000.00");
    assertThat(result.requiredMargin()).isEqualByComparingTo("20.00");
    assertThat(result.reason()).isEmpty();
  }

  @Test
  void permitsTradeWhenAvailableCashEqualsRequiredMargin() {
    MarginCheckResult result =
        marginCheck.checkTrade("RIZ6", 2, new BigDecimal("100.00"), new BigDecimal("20.00"));

    assertThat(result.allowed()).isTrue();
  }

  @Test
  void rejectsTradeWhenAvailableCashIsBelowRequiredMargin() {
    MarginCheckResult result =
        marginCheck.checkTrade("RIZ6", 3, new BigDecimal("100.00"), new BigDecimal("29.99"));

    assertThat(result.allowed()).isFalse();
    assertThat(result.requiredMargin()).isEqualByComparingTo("30.00");
    assertThat(result.reason()).contains("обеспечен").contains("30.00").contains("29.99");
  }

  @Test
  void rejectsWhenAvailableCashIsNegative() {
    MarginCheckResult result = marginCheck.check(new BigDecimal("-1.00"), new BigDecimal("20.00"));

    assertThat(result.allowed()).isFalse();
  }

  @Test
  void rejectsNullAvailableCashAsProgrammingError() {
    assertThatThrownBy(() -> marginCheck.check(null, BigDecimal.ONE))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("availableCash");
  }

  @Test
  void rejectsNullRequiredMarginAsProgrammingError() {
    assertThatThrownBy(() -> marginCheck.check(BigDecimal.ONE, null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("requiredMargin");
  }
}
