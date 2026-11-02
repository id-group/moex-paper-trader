package ru.moex.papertrader.core.portfolio;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import ru.moex.papertrader.TestInstruments;
import ru.moex.papertrader.core.domain.Instrument;

/**
 * Сделки по фьючерсам при отрицательном кэше: закрытие и уменьшение позиции не должны требовать
 * свободных средств, иначе убыточную позицию после вариационной маржи нельзя закрыть. Комиссия при
 * этом списывается.
 */
class VirtualPortfolioFuturesReductionTest {

  private static final Instant NOW = Instant.parse("2026-11-02T10:00:00Z");

  @Test
  void buyingBackShortFuturesWithNegativeCashClosesPositionAndChargesCommission() {
    Instrument futures = TestInstruments.siu6();
    VirtualPortfolio portfolio = negativeCashPortfolio(futures, -2);

    portfolio.buy(futures, 2, new BigDecimal("91000"), new BigDecimal("2.00"), NOW);

    assertThat(portfolio.cash()).isEqualByComparingTo("-1002.00");
    assertThat(portfolio.position(futures)).isEmpty();
  }

  @Test
  void sellingPartOfLongFuturesWithNegativeCashReducesPositionAndChargesCommission() {
    Instrument futures = TestInstruments.siu6();
    VirtualPortfolio portfolio = negativeCashPortfolio(futures, 2);

    portfolio.sell(futures, 1, new BigDecimal("91000"), new BigDecimal("1.00"), NOW);

    assertThat(portfolio.cash()).isEqualByComparingTo("-1001.00");
    assertThat(portfolio.position(futures).orElseThrow().quantity()).isEqualTo(1);
  }

  /** Снимок после убыточной ВМ: кэш −1000, ГО 18200, открытая фьючерсная позиция. */
  private static VirtualPortfolio negativeCashPortfolio(Instrument futures, long quantity) {
    return VirtualPortfolio.restoreForSettlement(
        Map.of(VirtualPortfolio.BASE_CURRENCY, new BigDecimal("-1000.00")),
        Map.of(VirtualPortfolio.BASE_CURRENCY, new BigDecimal("18200.00")),
        List.of(new PortfolioPosition(futures, quantity, new BigDecimal("91000"))));
  }
}
