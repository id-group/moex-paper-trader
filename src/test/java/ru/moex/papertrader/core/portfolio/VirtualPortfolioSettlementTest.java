package ru.moex.papertrader.core.portfolio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import ru.moex.papertrader.TestInstruments;
import ru.moex.papertrader.core.domain.Instrument;
import ru.moex.papertrader.core.domain.InvalidOrderException;

/**
 * Аддитивные операции клиринга: начисление вариационной маржи в кэш, перевод ГО на дельту без
 * проверки свободных средств и фиксация расчётной цены в средней цене позиции.
 */
class VirtualPortfolioSettlementTest {

  @Test
  void variationMarginIsCreditedToCash() {
    VirtualPortfolio portfolio = new VirtualPortfolio(new BigDecimal("1000.00"));

    portfolio.applyVariationMargin(new BigDecimal("2000.00"));

    assertThat(portfolio.cash()).isEqualByComparingTo("3000.00");
  }

  @Test
  void negativeVariationMarginMayPushCashBelowZero() {
    VirtualPortfolio portfolio = new VirtualPortfolio(new BigDecimal("1000.00"));

    portfolio.applyVariationMargin(new BigDecimal("-2500.00"));

    assertThat(portfolio.cash()).isEqualByComparingTo("-1500.00");
  }

  @Test
  void marginChangeLocksEvenWithoutFreeCash() {
    VirtualPortfolio portfolio = new VirtualPortfolio(new BigDecimal("100.00"));

    portfolio.applyMarginChange(new BigDecimal("200.00"));

    assertThat(portfolio.lockedCash()).isEqualByComparingTo("200.00");
    assertThat(portfolio.availableCash()).isEqualByComparingTo("-100.00");
  }

  @Test
  void negativeMarginChangeReleasesLockedCash() {
    VirtualPortfolio portfolio =
        VirtualPortfolio.restoreForSettlement(
            Map.of("RUB", new BigDecimal("1000.00")),
            Map.of("RUB", new BigDecimal("500.00")),
            List.of());

    portfolio.applyMarginChange(new BigDecimal("-200.00"));

    assertThat(portfolio.lockedCash()).isEqualByComparingTo("300.00");
  }

  @Test
  void marginChangeCannotReleaseMoreThanLocked() {
    VirtualPortfolio portfolio =
        VirtualPortfolio.restoreForSettlement(
            Map.of("RUB", new BigDecimal("1000.00")),
            Map.of("RUB", new BigDecimal("500.00")),
            List.of());

    assertThatThrownBy(() -> portfolio.applyMarginChange(new BigDecimal("-600.00")))
        .isInstanceOf(InvalidOrderException.class);
  }

  @Test
  void settlementPriceReplacesAveragePriceKeepingQuantitySign() {
    Instrument futures = TestInstruments.siu6();
    VirtualPortfolio portfolio =
        VirtualPortfolio.restoreForSettlement(
            Map.of("RUB", new BigDecimal("1000.00")),
            Map.of("RUB", new BigDecimal("18000.00")),
            List.of(new PortfolioPosition(futures, -2, new BigDecimal("90000"))));

    portfolio.settlePosition(futures, new BigDecimal("91000"));

    PortfolioPosition position = portfolio.position(futures).orElseThrow();
    assertThat(position.quantity()).isEqualTo(-2);
    assertThat(position.averagePrice()).isEqualByComparingTo("91000");
  }

  @Test
  void settlementRejectsMissingPositionAndNonPositivePrice() {
    Instrument futures = TestInstruments.siu6();
    VirtualPortfolio portfolio =
        VirtualPortfolio.restoreForSettlement(
            Map.of("RUB", new BigDecimal("1000.00")),
            Map.of(),
            List.of(new PortfolioPosition(futures, 2, new BigDecimal("90000"))));

    assertThatThrownBy(() -> portfolio.settlePosition(futures, BigDecimal.ZERO))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> portfolio.settlePosition(TestInstruments.sber(), new BigDecimal("1")))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void restoreForSettlementAllowsNegativeCashAndLockAboveCash() {
    VirtualPortfolio portfolio =
        VirtualPortfolio.restoreForSettlement(
            Map.of("RUB", new BigDecimal("-1000.00")),
            Map.of("RUB", new BigDecimal("18200.00")),
            List.of());

    assertThat(portfolio.cash()).isEqualByComparingTo("-1000.00");
    assertThat(portfolio.lockedCash()).isEqualByComparingTo("18200.00");
    assertThat(portfolio.availableCash()).isEqualByComparingTo("-19200.00");
  }
}
