package ru.moex.papertrader.core.portfolio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class MarginRatesTest {

  @Test
  void defaultsToTenPercentForEveryTicker() {
    MarginRates rates = MarginRates.defaults();

    assertThat(rates.defaultRate()).isEqualByComparingTo("0.10");
    assertThat(rates.rateFor("SBER")).isEqualByComparingTo("0.10");
    assertThat(rates.rateFor("RIZ6")).isEqualByComparingTo("0.10");
  }

  @Test
  void returnsPerInstrumentOverrideWhenPresent() {
    MarginRates rates =
        new MarginRates(
            new BigDecimal("0.10"),
            Map.of("RIZ6", new BigDecimal("0.15"), "SiZ6", new BigDecimal("0.08")));

    assertThat(rates.rateFor("RIZ6")).isEqualByComparingTo("0.15");
    assertThat(rates.rateFor("SiZ6")).isEqualByComparingTo("0.08");
    assertThat(rates.rateFor("SBER")).isEqualByComparingTo("0.10");
  }

  @Test
  void rejectsZeroDefaultRate() {
    assertThatThrownBy(() -> new MarginRates(BigDecimal.ZERO, Map.of()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("defaultRate");
  }

  @Test
  void rejectsDefaultRateOfOneOrMore() {
    assertThatThrownBy(() -> new MarginRates(BigDecimal.ONE, Map.of()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("defaultRate");
  }

  @Test
  void rejectsNegativeDefaultRate() {
    assertThatThrownBy(() -> new MarginRates(new BigDecimal("-0.10"), Map.of()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("defaultRate");
  }

  @Test
  void rejectsInvalidOverrideRate() {
    assertThatThrownBy(
            () -> new MarginRates(new BigDecimal("0.10"), Map.of("RIZ6", new BigDecimal("1.50"))))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("RIZ6");
  }

  @Test
  void rejectsBlankTickerLookup() {
    assertThatThrownBy(() -> MarginRates.defaults().rateFor(" "))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("ticker");
  }

  @Test
  void rejectsNullOverridesMap() {
    assertThatThrownBy(() -> new MarginRates(new BigDecimal("0.10"), null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("overrides");
  }

  @Test
  void rejectsNullDefaultRate() {
    assertThatThrownBy(() -> new MarginRates(null, Map.of()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("defaultRate");
  }

  @Test
  void rejectsNullOverrideRateAndNullTicker() {
    Map<String, BigDecimal> nullRate = new HashMap<>();
    nullRate.put("RIZ6", null);
    assertThatThrownBy(() -> new MarginRates(new BigDecimal("0.10"), nullRate))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("RIZ6");

    Map<String, BigDecimal> nullTicker = new HashMap<>();
    nullTicker.put(null, new BigDecimal("0.15"));
    assertThatThrownBy(() -> new MarginRates(new BigDecimal("0.10"), nullTicker))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("тикер");
  }
}
