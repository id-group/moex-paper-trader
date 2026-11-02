package ru.moex.papertrader.core.commission;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CommissionCalculatorTest {

  private static final BigDecimal SBER_PRICE = new BigDecimal("250.00");

  private final CommissionCalculator calculator =
      new CommissionCalculator(CommissionTariffRegistry.defaults());

  @Test
  void chargesSharePercentOfTurnover() {
    // 250.00 * 10 = 2 500.00; 0.1% = 2.50
    BigDecimal commission = calculator.calculate(InstrumentType.STOCK, SBER_PRICE, 10);

    assertThat(commission).isEqualByComparingTo("2.50");
  }

  @Test
  void chargesSharePercentForTickerWithMinimalTurnover() {
    // 100.00 * 10 = 1 000.00; 0.1% = 1.00
    BigDecimal commission =
        calculator.calculate(InstrumentType.STOCK, "SBER", new BigDecimal("100.00"), 10);

    assertThat(commission).isEqualByComparingTo("1.00");
  }

  @Test
  void fallsBackToMinimumCommissionWhenPercentIsLower() {
    CommissionTariffRegistry tariffs =
        new CommissionTariffRegistry(
            Map.of(
                InstrumentType.STOCK,
                new CommissionTariff(
                    new BigDecimal("0.001"), new BigDecimal("5.00"), BigDecimal.ZERO),
                InstrumentType.FUTURES,
                CommissionTariff.futures()),
            Map.of());
    CommissionCalculator minimumCalculator = new CommissionCalculator(tariffs);

    // 100.00 * 10 = 1 000.00; 0.1% = 1.00 < 5.00
    BigDecimal commission =
        minimumCalculator.calculate(InstrumentType.STOCK, new BigDecimal("100.00"), 10);

    assertThat(commission).isEqualByComparingTo("5.00");
  }

  @Test
  void chargesFuturesExchangeFeePerContractIndependentOfPrice() {
    BigDecimal cheap = calculator.calculate(InstrumentType.FUTURES, new BigDecimal("100"), 4);
    BigDecimal expensive = calculator.calculate(InstrumentType.FUTURES, new BigDecimal("90000"), 4);

    assertThat(cheap).isEqualByComparingTo("4.00");
    assertThat(expensive).isEqualByComparingTo("4.00");
  }

  @Test
  void usesTickerOverrideInsteadOfTypeTariff() {
    CommissionCalculator overrideCalculator =
        new CommissionCalculator(
            CommissionTariffRegistry.defaults()
                .withOverride(
                    "SBER",
                    new CommissionTariff(
                        new BigDecimal("0.0005"), BigDecimal.ZERO, BigDecimal.ZERO)));

    // 100.00 * 10 = 1 000.00; 0.05% = 0.50 вместо 1.00 по тарифу акций
    BigDecimal commission =
        overrideCalculator.calculate(InstrumentType.STOCK, "SBER", new BigDecimal("100.00"), 10);

    assertThat(commission).isEqualByComparingTo("0.50");
  }

  @Test
  void roundsMoneyHalfUpToTwoDecimals() {
    // 1 005.00 * 0.1% = 1.005 -> HALF_UP -> 1.01
    BigDecimal commission =
        calculator.calculate(InstrumentType.STOCK, new BigDecimal("1005.00"), 1);

    assertThat(commission).isEqualByComparingTo("1.01");
    assertThat(commission.scale()).isEqualTo(2);
  }

  @Test
  void returnsZeroForZeroTurnover() {
    BigDecimal zeroPrice = calculator.calculate(InstrumentType.STOCK, BigDecimal.ZERO, 10);
    BigDecimal zeroQuantity = calculator.calculate(InstrumentType.STOCK, SBER_PRICE, 0);
    BigDecimal zeroFuture = calculator.calculate(InstrumentType.FUTURES, new BigDecimal("100"), 0);

    assertThat(zeroPrice).isEqualByComparingTo("0");
    assertThat(zeroQuantity).isEqualByComparingTo("0");
    assertThat(zeroFuture).isEqualByComparingTo("0");
  }

  @Test
  void rejectsNegativePrice() {
    assertThatThrownBy(
            () -> calculator.calculate(InstrumentType.STOCK, new BigDecimal("-1.00"), 10))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("price");
  }

  @Test
  void rejectsNegativeQuantity() {
    assertThatThrownBy(() -> calculator.calculate(InstrumentType.STOCK, SBER_PRICE, -1))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("quantity");
  }

  @Test
  void rejectsNullPriceAndType() {
    assertThatThrownBy(() -> calculator.calculate(InstrumentType.STOCK, null, 10))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("price");

    assertThatThrownBy(() -> calculator.calculate(null, SBER_PRICE, 10))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("type");
  }
}
