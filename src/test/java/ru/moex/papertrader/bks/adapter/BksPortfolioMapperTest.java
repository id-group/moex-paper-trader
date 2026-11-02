package ru.moex.papertrader.bks.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import ru.moex.papertrader.TestInstruments;
import ru.moex.papertrader.TestJson;
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
  void mapsLockedForFuturesToMoneyEntry() {
    PortfolioSnapshot snapshot =
        new PortfolioSnapshot(
            "PAPER-0001",
            "RUB",
            new BigDecimal("1000000.00"),
            new BigDecimal("19000"),
            new BigDecimal("18000"),
            List.of(),
            NOW);

    var money = mapper.toPortfolio(snapshot).get(0);

    assertThat(money.type()).isEqualTo("moneyLimit");
    assertThat(money.locked()).isEqualByComparingTo("19000");
    assertThat(money.lockedForFutures()).isEqualByComparingTo("18000");
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

  @Test
  void mapsFuturesPositionAndMarginToFutureRecords() {
    PortfolioSnapshot snapshot = futuresSnapshot();

    var limits = mapper.toLimits(snapshot);

    assertThat(limits.depoLimit()).isEmpty();
    assertThat(limits.futureHolding()).hasSize(1);
    var holding = limits.futureHolding().get(0);
    assertThat(holding.ticker()).isEqualTo("SiU6");
    assertThat(holding.classCode()).isEqualTo("SPBFUT");
    assertThat(holding.exchange()).isEqualTo("MOEX");
    assertThat(holding.averagePrice()).isEqualByComparingTo("90000");
    assertThat(holding.instrumentType()).isEqualTo("FUTURES");
    // Полей количества, текущей цены и вариационной маржи в схеме FutureHoldingResponse нет.
    assertThat(holding.varMargin()).isNull();
    assertThat(holding.positionValue()).isNull();
    assertThat(holding.tradeDate()).isNull();

    assertThat(limits.futuresLimits()).hasSize(1);
    var futuresLimit = limits.futuresLimits().get(0);
    assertThat(futuresLimit.currencyCode()).isEqualTo("RUB");
    assertThat(futuresLimit.exchange()).isEqualTo("MOEX");
    assertThat(futuresLimit.instrumentType()).isEqualTo("FUTURES");
    assertThat(futuresLimit.cbplUsed()).isEqualByComparingTo("18000");
    assertThat(futuresLimit.cbplUsedForPositions()).isEqualByComparingTo("18000");
    assertThat(futuresLimit.loadDate()).isEqualTo(OffsetDateTime.parse("2026-11-01T10:00:00Z"));
    // Лимит ГО в снапшоте не хранится — значение не выдумываем.
    assertThat(futuresLimit.cbpLimit()).isNull();

    assertThat(limits.moneyLimits()).hasSize(1);
    assertThat(limits.moneyLimits().get(0).locked()).isEqualByComparingTo("18000");

    var entries = mapper.toPortfolio(snapshot);
    assertThat(entries).hasSize(2);
    var money = entries.get(0);
    assertThat(money.type()).isEqualTo("moneyLimit");
    assertThat(money.lockedForFutures()).isEqualByComparingTo("18000");
    var holdingEntry = entries.get(1);
    assertThat(holdingEntry.type()).isEqualTo("futuresHolding");
    assertThat(holdingEntry.ticker()).isEqualTo("SiU6");
    assertThat(holdingEntry.displayName()).isEqualTo("Фьючерс на доллар США");
    assertThat(holdingEntry.instrumentType()).isEqualTo("FUTURES");
    assertThat(holdingEntry.quantity()).isEqualByComparingTo("2");
    assertThat(holdingEntry.balancePrice()).isEqualByComparingTo("90000");
    assertThat(holdingEntry.currentPrice()).isEqualByComparingTo("91000");
    assertThat(holdingEntry.board()).isEqualTo("SPBFUT");
  }

  @Test
  void mapsSplitFuturesMarginToCbplFields() {
    PortfolioSnapshot snapshot =
        new PortfolioSnapshot(
            "PAPER-0001",
            "RUB",
            new BigDecimal("1000000.00"),
            new BigDecimal("27000"),
            new BigDecimal("27000"),
            new BigDecimal("18000"),
            new BigDecimal("9000"),
            List.of(
                new Position(
                    TestInstruments.siu6(), 2, new BigDecimal("90000"), new BigDecimal("91000"))),
            NOW);

    var futuresLimit = mapper.toLimits(snapshot).futuresLimits().get(0);

    assertThat(futuresLimit.cbplUsedForOrders()).isEqualByComparingTo("18000");
    assertThat(futuresLimit.cbplUsedForPositions()).isEqualByComparingTo("9000");
    // Описание cbplUsed в снапшоте неоднозначно — отдаём сумму ГО заявок и позиций.
    assertThat(futuresLimit.cbplUsed()).isEqualByComparingTo("27000");
  }

  @Test
  void mapsStockAndFuturesPositionsToSeparateGroups() {
    PortfolioSnapshot snapshot =
        new PortfolioSnapshot(
            "PAPER-0001",
            "RUB",
            new BigDecimal("1000000.00"),
            new BigDecimal("19000"),
            new BigDecimal("18000"),
            List.of(
                new Position(
                    TestInstruments.sber(), 20, new BigDecimal("280"), new BigDecimal("285.50")),
                new Position(
                    TestInstruments.siu6(), 2, new BigDecimal("90000"), new BigDecimal("91000"))),
            NOW);

    var limits = mapper.toLimits(snapshot);

    assertThat(limits.depoLimit()).hasSize(1);
    assertThat(limits.depoLimit().get(0).ticker()).isEqualTo("SBER");
    assertThat(limits.depoLimit().get(0).averagePrice()).isEqualByComparingTo("280");
    assertThat(limits.depoLimit().get(0).quantity().value()).isEqualByComparingTo("20");
    assertThat(limits.depoLimit().get(0).quantityBatch().value()).isEqualByComparingTo("2");
    assertThat(limits.futureHolding()).hasSize(1);
    assertThat(limits.futureHolding().get(0).ticker()).isEqualTo("SiU6");
    assertThat(limits.futuresLimits()).hasSize(1);

    var entries = mapper.toPortfolio(snapshot);
    assertThat(entries).hasSize(3);
    assertThat(entries.get(1).type()).isEqualTo("depoLimit");
    assertThat(entries.get(1).ticker()).isEqualTo("SBER");
    assertThat(entries.get(1).balanceValue()).isEqualByComparingTo("5600");
    assertThat(entries.get(1).currentValue()).isEqualByComparingTo("5710");
    assertThat(entries.get(2).type()).isEqualTo("futuresHolding");
    assertThat(entries.get(2).ticker()).isEqualTo("SiU6");
  }

  @Test
  void serializesFuturesRecordsPerContractSchema() {
    var json = TestJson.MAPPER.valueToTree(mapper.toLimits(futuresSnapshot()));

    var holding = json.path("futureHolding").get(0);
    assertThat(holding.path("ticker").asText()).isEqualTo("SiU6");
    assertThat(holding.path("classCode").asText()).isEqualTo("SPBFUT");
    assertThat(holding.path("exchange").asText()).isEqualTo("MOEX");
    assertThat(holding.path("averagePrice").asDouble()).isEqualTo(90_000.0);
    assertThat(holding.path("instrumentType").asText()).isEqualTo("FUTURES");
    assertThat(holding.path("varMargin").isMissingNode()).isTrue();
    assertThat(holding.path("positionValue").isMissingNode()).isTrue();
    assertThat(holding.path("executionDate").isMissingNode()).isTrue();
    assertThat(holding.path("tradeDate").isMissingNode()).isTrue();

    var futuresLimit = json.path("futuresLimits").get(0);
    assertThat(futuresLimit.path("currencyCode").asText()).isEqualTo("RUB");
    assertThat(futuresLimit.path("exchange").asText()).isEqualTo("MOEX");
    assertThat(futuresLimit.path("instrumentType").asText()).isEqualTo("FUTURES");
    assertThat(futuresLimit.path("cbplUsed").asDouble()).isEqualTo(18_000.0);
    assertThat(futuresLimit.path("cbplUsedForPositions").asDouble()).isEqualTo(18_000.0);
    assertThat(futuresLimit.path("loadDate").asText()).isEqualTo("2026-11-01T10:00:00Z");
    assertThat(futuresLimit.path("cbpLimit").isMissingNode()).isTrue();
    assertThat(futuresLimit.path("varMargin").isMissingNode()).isTrue();
    assertThat(futuresLimit.path("realVarMargin").isMissingNode()).isTrue();
  }

  @Test
  void mapsEmptySnapshotToEmptyFuturesLists() {
    PortfolioSnapshot snapshot =
        new PortfolioSnapshot(
            "PAPER-0001", "RUB", BigDecimal.ZERO, BigDecimal.ZERO, List.of(), NOW);

    var limits = mapper.toLimits(snapshot);

    assertThat(limits.depoLimit()).isNotNull().isEmpty();
    assertThat(limits.futureHolding()).isNotNull().isEmpty();
    assertThat(limits.futuresLimits()).isNotNull().isEmpty();
    assertThat(limits.moneyLimits()).hasSize(1);

    assertThat(mapper.toPortfolio(snapshot)).hasSize(1);
  }

  private static PortfolioSnapshot futuresSnapshot() {
    return new PortfolioSnapshot(
        "PAPER-0001",
        "RUB",
        new BigDecimal("1000000.00"),
        new BigDecimal("18000"),
        new BigDecimal("18000"),
        List.of(
            new Position(
                TestInstruments.siu6(), 2, new BigDecimal("90000"), new BigDecimal("91000"))),
        NOW);
  }
}
