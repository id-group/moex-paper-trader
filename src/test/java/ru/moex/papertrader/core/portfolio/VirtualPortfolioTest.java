package ru.moex.papertrader.core.portfolio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;
import ru.moex.papertrader.TestInstruments;
import ru.moex.papertrader.core.domain.Fill;
import ru.moex.papertrader.core.domain.Instrument;
import ru.moex.papertrader.core.domain.InvalidOrderException;

class VirtualPortfolioTest {

  private static final Instant NOW = Instant.parse("2026-11-02T10:00:00Z");
  private static final BigDecimal ZERO = new BigDecimal("0.00");
  private static final BigDecimal HUNDRED = new BigDecimal("100.00");

  @Test
  void buyingSharesDebitsCashIncludingCommission() {
    VirtualPortfolio portfolio = portfolioWith("100000.00");

    Fill fill = portfolio.buy(TestInstruments.sber(), 10, HUNDRED, new BigDecimal("1.00"), NOW);

    assertThat(portfolio.cash()).isEqualByComparingTo("98999.00");
    assertThat(fill.commission()).isEqualByComparingTo("1.00");
    assertThat(fill.price()).isEqualByComparingTo("100.00");
    assertThat(fill.quantity()).isEqualTo(10);
    assertThat(fill.executedAt()).isEqualTo(NOW);
  }

  @Test
  void buyingSharesOpensPositionAtTradePrice() {
    VirtualPortfolio portfolio = portfolioWith("100000.00");

    portfolio.buy(TestInstruments.sber(), 10, HUNDRED, ZERO, NOW);

    PortfolioPosition position = portfolio.position(TestInstruments.sber()).orElseThrow();
    assertThat(position.instrument()).isEqualTo(TestInstruments.sber());
    assertThat(position.quantity()).isEqualTo(10);
    assertThat(position.averagePrice()).isEqualByComparingTo("100.00");
  }

  @Test
  void secondPurchaseRecomputesWeightedAveragePrice() {
    VirtualPortfolio portfolio = portfolioWith("100000.00");
    portfolio.buy(TestInstruments.sber(), 10, HUNDRED, ZERO, NOW);

    portfolio.buy(TestInstruments.sber(), 10, new BigDecimal("110.00"), ZERO, NOW);

    PortfolioPosition position = portfolio.position(TestInstruments.sber()).orElseThrow();
    assertThat(position.quantity()).isEqualTo(20);
    assertThat(position.averagePrice()).isEqualByComparingTo("105.00");
  }

  @Test
  void partialSaleReducesPositionAndCreditsProceeds() {
    VirtualPortfolio portfolio = portfolioWith("100000.00");
    portfolio.buy(TestInstruments.sber(), 10, HUNDRED, ZERO, NOW);

    portfolio.sell(TestInstruments.sber(), 4, new BigDecimal("120.00"), ZERO, NOW);

    assertThat(portfolio.cash()).isEqualByComparingTo("99480.00");
    PortfolioPosition position = portfolio.position(TestInstruments.sber()).orElseThrow();
    assertThat(position.quantity()).isEqualTo(6);
    assertThat(position.averagePrice()).isEqualByComparingTo("100.00");
  }

  @Test
  void fullSaleClosesPosition() {
    VirtualPortfolio portfolio = portfolioWith("100000.00");
    portfolio.buy(TestInstruments.sber(), 10, HUNDRED, ZERO, NOW);

    portfolio.sell(TestInstruments.sber(), 10, new BigDecimal("120.00"), ZERO, NOW);

    assertThat(portfolio.cash()).isEqualByComparingTo("100200.00");
    assertThat(portfolio.position(TestInstruments.sber())).isEmpty();
  }

  @Test
  void saleAbovePositionIsRejected() {
    VirtualPortfolio portfolio = portfolioWith("100000.00");
    portfolio.buy(TestInstruments.sber(), 10, HUNDRED, ZERO, NOW);

    assertThatThrownBy(
            () -> portfolio.sell(TestInstruments.sber(), 11, new BigDecimal("120.00"), ZERO, NOW))
        .isInstanceOf(InvalidOrderException.class)
        .hasMessageContaining("SBER");

    assertThat(portfolio.position(TestInstruments.sber()).orElseThrow().quantity()).isEqualTo(10);
    assertThat(portfolio.cash()).isEqualByComparingTo("99000.00");
  }

  @Test
  void purchaseWithInsufficientCashIsRejected() {
    VirtualPortfolio portfolio = portfolioWith("1000.00");

    assertThatThrownBy(
            () -> portfolio.buy(TestInstruments.sber(), 10, HUNDRED, new BigDecimal("1.00"), NOW))
        .isInstanceOf(InvalidOrderException.class)
        .hasMessageContaining("Недостаточно средств");

    assertThat(portfolio.cash()).isEqualByComparingTo("1000.00");
    assertThat(portfolio.position(TestInstruments.sber())).isEmpty();
  }

  @Test
  void lockingReducesAvailableCash() {
    VirtualPortfolio portfolio = portfolioWith("1000.00");

    portfolio.lock(new BigDecimal("400.00"));

    assertThat(portfolio.cash()).isEqualByComparingTo("1000.00");
    assertThat(portfolio.lockedCash()).isEqualByComparingTo("400.00");
    assertThat(portfolio.availableCash()).isEqualByComparingTo("600.00");
  }

  @Test
  void releaseReturnsAvailableCash() {
    VirtualPortfolio portfolio = portfolioWith("1000.00");
    portfolio.lock(new BigDecimal("400.00"));

    portfolio.release(new BigDecimal("150.00"));

    assertThat(portfolio.lockedCash()).isEqualByComparingTo("250.00");
    assertThat(portfolio.availableCash()).isEqualByComparingTo("750.00");
  }

  @Test
  void purchaseCannotSpendLockedCash() {
    VirtualPortfolio portfolio = portfolioWith("1000.00");
    portfolio.lock(new BigDecimal("500.00"));

    assertThatThrownBy(() -> portfolio.buy(TestInstruments.sber(), 6, HUNDRED, ZERO, NOW))
        .isInstanceOf(InvalidOrderException.class)
        .hasMessageContaining("Недостаточно средств");

    assertThat(portfolio.cash()).isEqualByComparingTo("1000.00");
    assertThat(portfolio.lockedCash()).isEqualByComparingTo("500.00");
  }

  @Test
  void lockingAboveAvailableCashIsRejected() {
    VirtualPortfolio portfolio = portfolioWith("1000.00");

    assertThatThrownBy(() -> portfolio.lock(new BigDecimal("1000.01")))
        .isInstanceOf(InvalidOrderException.class);

    assertThat(portfolio.lockedCash()).isEqualByComparingTo("0.00");
  }

  @Test
  void releaseAboveLockedCashIsRejected() {
    VirtualPortfolio portfolio = portfolioWith("1000.00");
    portfolio.lock(new BigDecimal("400.00"));

    assertThatThrownBy(() -> portfolio.release(new BigDecimal("500.00")))
        .isInstanceOf(InvalidOrderException.class);

    assertThat(portfolio.lockedCash()).isEqualByComparingTo("400.00");
  }

  @Test
  void openingFuturesDoesNotDebitContractValue() {
    VirtualPortfolio portfolio = portfolioWith("100000.00");
    Instrument futures = futures();

    portfolio.buy(futures, 1, new BigDecimal("90000.00"), new BigDecimal("54.00"), NOW);

    assertThat(portfolio.cash()).isEqualByComparingTo("99946.00");
    assertThat(portfolio.position(futures).orElseThrow().quantity()).isEqualTo(1);
    assertThat(portfolio.position(futures).orElseThrow().averagePrice())
        .isEqualByComparingTo("90000.00");
  }

  @Test
  void closingFuturesDoesNotCreditContractValue() {
    VirtualPortfolio portfolio = portfolioWith("100000.00");
    Instrument futures = futures();
    portfolio.buy(futures, 1, new BigDecimal("90000.00"), new BigDecimal("54.00"), NOW);

    portfolio.sell(futures, 1, new BigDecimal("91000.00"), new BigDecimal("54.00"), NOW);

    assertThat(portfolio.cash()).isEqualByComparingTo("99892.00");
    assertThat(portfolio.position(futures)).isEmpty();
  }

  @Test
  void sellingFuturesOpensShortPosition() {
    VirtualPortfolio portfolio = portfolioWith("100000.00");
    Instrument futures = futures();

    portfolio.sell(futures, 1, new BigDecimal("90000.00"), new BigDecimal("54.00"), NOW);

    PortfolioPosition position = portfolio.position(futures).orElseThrow();
    assertThat(position.quantity()).isEqualTo(-1);
    assertThat(position.averagePrice()).isEqualByComparingTo("90000.00");
    assertThat(portfolio.cash()).isEqualByComparingTo("99946.00");
  }

  @Test
  void cashIsTrackedPerCurrency() {
    VirtualPortfolio portfolio =
        new VirtualPortfolio(
            Map.of("RUB", new BigDecimal("1000.00"), "USD", new BigDecimal("100.00")));

    portfolio.lock("USD", new BigDecimal("10.00"));

    assertThat(portfolio.cash("RUB")).isEqualByComparingTo("1000.00");
    assertThat(portfolio.cash("USD")).isEqualByComparingTo("100.00");
    assertThat(portfolio.availableCash("USD")).isEqualByComparingTo("90.00");
  }

  private static VirtualPortfolio portfolioWith(String rub) {
    return new VirtualPortfolio(new BigDecimal(rub));
  }

  private static Instrument futures() {
    return new Instrument(
        "Si-12.26",
        "SPBFUT",
        "FUTURES",
        "Si",
        "Si-12.26",
        null,
        new BigDecimal("1"),
        new BigDecimal("1"),
        "RUB");
  }
}
