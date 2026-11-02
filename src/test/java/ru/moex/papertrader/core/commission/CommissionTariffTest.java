package ru.moex.papertrader.core.commission;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class CommissionTariffTest {

  @Test
  void defaultStockTariffIsTenthOfPercent() {
    CommissionTariff tariff = CommissionTariff.stock();

    assertThat(tariff.percent()).isEqualByComparingTo("0.001");
    assertThat(tariff.minimumCommission()).isPositive();
    assertThat(tariff.exchangeFeePerContract()).isEqualByComparingTo("0");
  }

  @Test
  void defaultFuturesTariffIsFeePerContractOnly() {
    CommissionTariff tariff = CommissionTariff.futures();

    assertThat(tariff.percent()).isEqualByComparingTo("0");
    assertThat(tariff.minimumCommission()).isEqualByComparingTo("0");
    assertThat(tariff.exchangeFeePerContract()).isPositive();
  }

  @Test
  void rejectsNegativeParameters() {
    assertThatThrownBy(
            () -> new CommissionTariff(new BigDecimal("-0.001"), BigDecimal.ZERO, BigDecimal.ZERO))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("percent");

    assertThatThrownBy(
            () -> new CommissionTariff(BigDecimal.ZERO, new BigDecimal("-1"), BigDecimal.ZERO))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("minimumCommission");

    assertThatThrownBy(
            () -> new CommissionTariff(BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("-1")))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("exchangeFeePerContract");
  }

  @Test
  void rejectsNullParameters() {
    assertThatThrownBy(() -> new CommissionTariff(null, BigDecimal.ZERO, BigDecimal.ZERO))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("percent");
  }
}
