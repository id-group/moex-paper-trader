package ru.moex.papertrader.bks.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import ru.moex.papertrader.TestInstruments;
import ru.moex.papertrader.core.domain.PortfolioSnapshot;
import ru.moex.papertrader.core.domain.Position;

class BksPortfolioMapperTest {

  private static final Instant NOW = Instant.parse("2026-11-01T10:00:00Z");

  private final BksPortfolioMapper mapper = new BksPortfolioMapper();

  @Test
  void mapsCashAndPositionsToPortfolioEntries() {
    PortfolioSnapshot snapshot =
        new PortfolioSnapshot(
            "PAPER-0001",
            "RUB",
            new BigDecimal("1000000.00"),
            new BigDecimal("1000"),
            List.of(
                new Position(
                    TestInstruments.sber(), 20, new BigDecimal("280"), new BigDecimal("285.50"))),
            NOW);

    var entries = mapper.toPortfolio(snapshot);

    assertThat(entries).hasSize(2);
    var money = entries.get(0);
    assertThat(money.type()).isEqualTo("moneyLimit");
    assertThat(money.quantity()).isEqualByComparingTo("1000000.00");
    assertThat(money.locked()).isEqualByComparingTo("1000");
    assertThat(money.portfolioShare()).isEqualByComparingTo("100");

    var depo = entries.get(1);
    assertThat(depo.type()).isEqualTo("depoLimit");
    assertThat(depo.ticker()).isEqualTo("SBER");
    assertThat(depo.displayName()).isEqualTo("Сбербанк");
    assertThat(depo.quantity()).isEqualByComparingTo("20");
    assertThat(depo.balanceValue()).isEqualByComparingTo("5600");
    assertThat(depo.currentValue()).isEqualByComparingTo("5710");
    assertThat(depo.unrealizedPL()).isEqualByComparingTo("110");
    assertThat(depo.scale()).isEqualTo(2);
    assertThat(depo.board()).isEqualTo("TQBR");
  }

  @Test
  void mapsCashAndPositionsToLimits() {
    PortfolioSnapshot snapshot =
        new PortfolioSnapshot(
            "PAPER-0001",
            "RUB",
            new BigDecimal("1000000.00"),
            new BigDecimal("1000"),
            List.of(
                new Position(
                    TestInstruments.sber(), 20, new BigDecimal("280"), new BigDecimal("285.50"))),
            NOW);

    var limits = mapper.toLimits(snapshot);

    assertThat(limits.moneyLimits()).hasSize(1);
    assertThat(limits.moneyLimits().get(0).quantity().type()).isEqualTo("T0");
    assertThat(limits.moneyLimits().get(0).quantity().value()).isEqualByComparingTo("1000000.00");
    assertThat(limits.moneyLimits().get(0).locked()).isEqualByComparingTo("1000");
    assertThat(limits.depoLimit()).hasSize(1);
    assertThat(limits.depoLimit().get(0).quantity().value()).isEqualByComparingTo("20");
    assertThat(limits.depoLimit().get(0).quantityBatch().value()).isEqualByComparingTo("2");
    assertThat(limits.futureHolding()).isEmpty();
    assertThat(limits.futuresLimits()).isEmpty();
  }
}
