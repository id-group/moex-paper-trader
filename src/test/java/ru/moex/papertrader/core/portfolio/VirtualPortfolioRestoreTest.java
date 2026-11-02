package ru.moex.papertrader.core.portfolio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import ru.moex.papertrader.TestInstruments;

/** Восстановление состояния {@link VirtualPortfolio} из сущностей БД (задача S3-4). */
class VirtualPortfolioRestoreTest {

  @Test
  void restoresCashLocksAndPositions() {
    VirtualPortfolio portfolio =
        VirtualPortfolio.restore(
            Map.of("RUB", new BigDecimal("1000.00"), "USD", new BigDecimal("50.00")),
            Map.of("RUB", new BigDecimal("100.00")),
            List.of(
                new PortfolioPosition(TestInstruments.sber(), 10, new BigDecimal("100.500000"))));

    assertThat(portfolio.cash("RUB")).isEqualByComparingTo("1000");
    assertThat(portfolio.lockedCash("RUB")).isEqualByComparingTo("100");
    assertThat(portfolio.availableCash("RUB")).isEqualByComparingTo("900");
    assertThat(portfolio.cash("USD")).isEqualByComparingTo("50");
    assertThat(portfolio.lockedCash("USD")).isEqualByComparingTo("0");
    assertThat(portfolio.positions()).hasSize(1);
    assertThat(portfolio.position(TestInstruments.sber()).orElseThrow().quantity()).isEqualTo(10);
  }

  @Test
  void restoresShortPositionWithNegativeQuantity() {
    VirtualPortfolio portfolio =
        VirtualPortfolio.restore(
            Map.of("RUB", new BigDecimal("1000.00")),
            Map.of(),
            List.of(
                new PortfolioPosition(TestInstruments.gazp(), -5, new BigDecimal("200.250000"))));

    assertThat(portfolio.position(TestInstruments.gazp()).orElseThrow().quantity()).isEqualTo(-5);
  }

  @Test
  void rejectsLockedAmountGreaterThanCash() {
    assertThatThrownBy(
            () ->
                VirtualPortfolio.restore(
                    Map.of("RUB", new BigDecimal("100.00")),
                    Map.of("RUB", new BigDecimal("150.00")),
                    List.of()))
        .isInstanceOf(RuntimeException.class);
  }

  @Test
  void rejectsDuplicatePositions() {
    PortfolioPosition position =
        new PortfolioPosition(TestInstruments.sber(), 10, new BigDecimal("100.500000"));

    assertThatThrownBy(
            () ->
                VirtualPortfolio.restore(
                    Map.of("RUB", new BigDecimal("1000.00")),
                    Map.of(),
                    List.of(position, position)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("SBER");
  }

  @Test
  void rejectsNegativeCash() {
    assertThatThrownBy(
            () ->
                VirtualPortfolio.restore(
                    Map.of("RUB", new BigDecimal("-1.00")), Map.of(), List.of()))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void restoredPortfolioKeepsWorkingAfterRestore() {
    VirtualPortfolio portfolio =
        VirtualPortfolio.restore(
            Map.of("RUB", new BigDecimal("1000.00")),
            Map.of(),
            List.of(
                new PortfolioPosition(TestInstruments.sber(), 10, new BigDecimal("100.000000"))));

    portfolio.sell(
        TestInstruments.sber(),
        10,
        new BigDecimal("110.00"),
        BigDecimal.ZERO,
        java.time.Instant.parse("2026-11-02T18:00:00Z"));

    assertThat(portfolio.cash()).isEqualByComparingTo("2100");
    assertThat(portfolio.position(TestInstruments.sber())).isEmpty();
  }
}
