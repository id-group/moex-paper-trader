package ru.moex.papertrader.core.reporting;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import ru.moex.papertrader.core.domain.Side;

class TradeJournalTest {

  private static final Instant T0 = Instant.parse("2026-11-02T10:00:00Z");

  @Test
  void slippageAndCommissionAreReportedSeparately() {
    TradeJournal journal =
        new TradeJournal(
            List.of(
                entry(T0, "BUY-1", Side.BUY, 10, "100", "100.5", "1.00", "momentum"),
                entry(
                    T0.plusSeconds(60),
                    "SELL-1",
                    Side.SELL,
                    10,
                    "101",
                    "100.5",
                    "1.01",
                    "momentum")));

    assertThat(journal.views()).hasSize(2);
    assertThat(journal.views().get(0).slippagePerUnit()).isEqualByComparingTo("0.5");
    assertThat(journal.views().get(0).slippageTotal()).isEqualByComparingTo("5.00");
    assertThat(journal.views().get(1).slippagePerUnit()).isEqualByComparingTo("0.5");
    assertThat(journal.views().get(1).slippageTotal()).isEqualByComparingTo("5.00");
    assertThat(journal.slippageTotal()).isEqualByComparingTo("10.00");
    assertThat(journal.commissionTotal()).isEqualByComparingTo("2.01");
    assertThat(journal.views()).extracting(JournalEntryView::strategyId).containsOnly("momentum");
  }

  @Test
  void fifoWinRateCountsPartialClosuresSeparately() {
    // Покупка 10, продажа 4 в плюс и 6 в минус: две закрытые пары, win rate = 1/2.
    TradeJournal journal =
        new TradeJournal(
            List.of(
                entry(T0, "BUY-1", Side.BUY, 10, "100", "100", "0", "s"),
                entry(T0.plusSeconds(60), "SELL-1", Side.SELL, 4, "110", "110", "0", "s"),
                entry(T0.plusSeconds(120), "SELL-2", Side.SELL, 6, "90", "90", "0", "s")));

    assertThat(journal.closedTrades()).hasSize(2);
    assertThat(journal.closedTrades())
        .extracting(ClosedTrade::pnl)
        .containsExactly(new BigDecimal("40.00"), new BigDecimal("-60.00"));
    assertThat(journal.winRate()).isEqualByComparingTo("0.500000");
  }

  @Test
  void commissionIsAllocatedAcrossPartialClosures() {
    // Вход 10 @100 с комиссией 10 (1 на единицу), два выхода по 4 и 6 с комиссией 4 и 6.
    // Первая пара: 4 × 10 − 4 − 4 = 32; вторая: 6 × (−10) − 6 − 6 = −72.
    TradeJournal journal =
        new TradeJournal(
            List.of(
                entry(T0, "BUY-1", Side.BUY, 10, "100", "100", "10", "s"),
                entry(T0.plusSeconds(60), "SELL-1", Side.SELL, 4, "110", "110", "4", "s"),
                entry(T0.plusSeconds(120), "SELL-2", Side.SELL, 6, "90", "90", "6", "s")));

    assertThat(journal.closedTrades())
        .extracting(ClosedTrade::pnl)
        .containsExactly(new BigDecimal("32.00"), new BigDecimal("-72.00"));
  }

  @Test
  void fifoMatchesOldestLotsFirstAndLeavesRemainderUnrealized() {
    // Два входа по 10, продажа 15: закрываются 10 старого лота и 5 нового, остаток 5 не закрыт.
    TradeJournal journal =
        new TradeJournal(
            List.of(
                entry(T0, "BUY-A", Side.BUY, 10, "100", "100", "0", "A"),
                entry(T0.plusSeconds(30), "BUY-B", Side.BUY, 10, "120", "120", "0", "B"),
                entry(T0.plusSeconds(60), "SELL-1", Side.SELL, 15, "130", "130", "0", "B")));

    assertThat(journal.closedTrades()).hasSize(2);
    assertThat(journal.closedTrades().get(0).entryOrderId()).isEqualTo("BUY-A");
    assertThat(journal.closedTrades().get(0).strategyId()).isEqualTo("A");
    assertThat(journal.closedTrades().get(0).quantity()).isEqualTo(10);
    assertThat(journal.closedTrades().get(0).pnl()).isEqualByComparingTo("300.00");
    assertThat(journal.closedTrades().get(1).entryOrderId()).isEqualTo("BUY-B");
    assertThat(journal.closedTrades().get(1).strategyId()).isEqualTo("B");
    assertThat(journal.closedTrades().get(1).quantity()).isEqualTo(5);
    assertThat(journal.closedTrades().get(1).pnl()).isEqualByComparingTo("50.00");
    assertThat(journal.winRate()).isEqualByComparingTo("1.000000");
  }

  @Test
  void shortPositionIsClosedByBuyWithSameFifoRules() {
    TradeJournal journal =
        new TradeJournal(
            List.of(
                entry(T0, "SELL-1", Side.SELL, 10, "100", "100", "0", "short"),
                entry(T0.plusSeconds(60), "BUY-1", Side.BUY, 4, "90", "90", "0", "short")));

    assertThat(journal.closedTrades()).hasSize(1);
    ClosedTrade trade = journal.closedTrades().get(0);
    assertThat(trade.entrySide()).isEqualTo(Side.SELL);
    assertThat(trade.quantity()).isEqualTo(4);
    assertThat(trade.pnl()).isEqualByComparingTo("40.00");
  }

  @Test
  void strategyBreakdownUsesEntryStrategyAndCountsAllFills() {
    TradeJournal journal =
        new TradeJournal(
            List.of(
                entry(T0, "BUY-A", Side.BUY, 10, "100", "100", "0", "A"),
                entry(T0.plusSeconds(30), "BUY-B", Side.BUY, 5, "100", "100", "0", "B"),
                entry(T0.plusSeconds(60), "SELL-A", Side.SELL, 10, "110", "110", "0", "A")));

    List<StrategyBreakdownEntry> breakdown = journal.strategyBreakdown();

    assertThat(breakdown).extracting(StrategyBreakdownEntry::strategyId).containsExactly("A", "B");
    StrategyBreakdownEntry a = breakdown.get(0);
    assertThat(a.tradeCount()).isEqualTo(2);
    assertThat(a.closedTradeCount()).isEqualTo(1);
    assertThat(a.realizedPnl()).isEqualByComparingTo("100.00");
    assertThat(a.winRate()).isEqualByComparingTo("1.000000");
    StrategyBreakdownEntry b = breakdown.get(1);
    assertThat(b.tradeCount()).isEqualTo(1);
    assertThat(b.closedTradeCount()).isZero();
    assertThat(b.realizedPnl()).isEqualByComparingTo("0.00");
    assertThat(b.winRate()).isNull();
  }

  @Test
  void tradesWithoutStrategyFallIntoUnassignedGroup() {
    TradeJournal journal =
        new TradeJournal(
            List.of(
                entry(T0, "BUY-1", Side.BUY, 10, "100", "100", "0", null),
                entry(T0.plusSeconds(60), "SELL-1", Side.SELL, 10, "110", "110", "0", null)));

    assertThat(journal.strategyBreakdown())
        .singleElement()
        .satisfies(
            entry -> {
              assertThat(entry.strategyId()).isEqualTo(TradeJournal.UNASSIGNED_STRATEGY);
              assertThat(entry.realizedPnl()).isEqualByComparingTo("100.00");
              assertThat(entry.winRate()).isEqualByComparingTo("1.000000");
            });
  }

  @Test
  void csvContainsHeaderAndEscapesCommaInStrategy() {
    TradeJournal journal =
        new TradeJournal(List.of(entry(T0, "BUY-1", Side.BUY, 10, "100", "100.5", "1.00", "a,b")));

    String csv = journal.toCsv();

    assertThat(csv).startsWith("executedAt,orderId,clientOrderId,ticker,classCode,side");
    assertThat(csv).contains("BUY-1");
    assertThat(csv).contains("\"a,b\"");
    assertThat(csv).contains("100.5");
  }

  @Test
  void emptyJournalHasZeroTotalsAndNoWinRate() {
    TradeJournal journal = new TradeJournal(List.of());

    assertThat(journal.slippageTotal()).isEqualByComparingTo("0.00");
    assertThat(journal.commissionTotal()).isEqualByComparingTo("0.00");
    assertThat(journal.winRate()).isNull();
    assertThat(journal.strategyBreakdown()).isEmpty();
    assertThat(journal.toCsv())
        .isEqualTo(
            "executedAt,orderId,clientOrderId,ticker,classCode,side,quantity,referencePrice,"
                + "price,slippagePerUnit,slippageTotal,commission,strategyId\n");
  }

  private static TradeJournalEntry entry(
      Instant at,
      String orderId,
      Side side,
      long quantity,
      String referencePrice,
      String price,
      String commission,
      String strategyId) {
    return new TradeJournalEntry(
        at,
        orderId,
        UUID.nameUUIDFromBytes(orderId.getBytes(java.nio.charset.StandardCharsets.UTF_8)),
        "SBER",
        "TQBR",
        side,
        quantity,
        new BigDecimal(referencePrice),
        new BigDecimal(price),
        new BigDecimal(commission),
        strategyId);
  }
}
