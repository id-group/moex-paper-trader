package ru.moex.papertrader.core.commission;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CommissionTariffRegistryTest {

  private static final CommissionTariff CUSTOM =
      new CommissionTariff(new BigDecimal("0.0005"), BigDecimal.ZERO, BigDecimal.ZERO);

  @Test
  void defaultsProvideTariffForStocksAndFutures() {
    CommissionTariffRegistry registry = CommissionTariffRegistry.defaults();

    assertThat(registry.tariffFor(InstrumentType.STOCK, "SBER").percent())
        .isEqualByComparingTo("0.001");
    assertThat(registry.tariffFor(InstrumentType.FUTURES, "SRZ6").exchangeFeePerContract())
        .isPositive();
  }

  @Test
  void unknownTickerFallsBackToTypeTariff() {
    CommissionTariffRegistry registry = CommissionTariffRegistry.defaults();

    assertThat(registry.tariffFor(InstrumentType.STOCK, null))
        .isEqualTo(registry.tariffFor(InstrumentType.STOCK, "GAZP"));
  }

  @Test
  void tickerOverrideIsCaseInsensitiveAndDoesNotAffectOtherTickers() {
    CommissionTariffRegistry registry =
        CommissionTariffRegistry.defaults().withOverride("sber", CUSTOM);

    assertThat(registry.tariffFor(InstrumentType.STOCK, "SBER")).isEqualTo(CUSTOM);
    assertThat(registry.tariffFor(InstrumentType.STOCK, "GAZP").percent())
        .isEqualByComparingTo("0.001");
  }

  @Test
  void overrideKeepsOriginalRegistryUnchanged() {
    CommissionTariffRegistry original = CommissionTariffRegistry.defaults();
    CommissionTariffRegistry customized = original.withOverride("SBER", CUSTOM);

    assertThat(original.tariffFor(InstrumentType.STOCK, "SBER").percent())
        .isEqualByComparingTo("0.001");
    assertThat(customized).isNotSameAs(original);
  }

  @Test
  void rejectsMissingTariffForType() {
    CommissionTariffRegistry registry =
        new CommissionTariffRegistry(
            Map.of(InstrumentType.STOCK, CommissionTariff.stock()), Map.of());

    assertThatThrownBy(() -> registry.tariffFor(InstrumentType.FUTURES, null))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("FUTURES");
  }

  @Test
  void rejectsBlankOverrideTicker() {
    CommissionTariffRegistry registry = CommissionTariffRegistry.defaults();

    assertThatThrownBy(() -> registry.withOverride("  ", CUSTOM))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("ticker");
  }
}
