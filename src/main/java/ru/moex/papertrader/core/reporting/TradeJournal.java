package ru.moex.papertrader.core.reporting;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * Журнал сделок отчётного периода.
 *
 * <p>Хранит сделки в порядке поступления и вычисляет производные показатели: суммарное
 * проскальзывание, комиссии, FIFO-пары, win rate и разбивку по стратегиям. CSV-выгрузка — UTF-8 без
 * BOM, разделитель — запятая, десятичные числа — с точкой; поля с запятой, кавычками или переводом
 * строки экранируются кавычками.
 */
public final class TradeJournal {

  /** Группа сделок без метки стратегии. */
  public static final String UNASSIGNED_STRATEGY = "(none)";

  private static final int RATE_SCALE = 6;
  private static final int MONEY_SCALE = 2;

  private static final String CSV_HEADER =
      "executedAt,orderId,clientOrderId,ticker,classCode,side,quantity,referencePrice,price,"
          + "slippagePerUnit,slippageTotal,commission,strategyId";

  private final List<TradeJournalEntry> entries;
  private final List<JournalEntryView> views;
  private final List<ClosedTrade> closedTrades;

  public TradeJournal(List<TradeJournalEntry> entries) {
    Objects.requireNonNull(entries, "entries");
    List<TradeJournalEntry> copy = new ArrayList<>(entries.size());
    for (TradeJournalEntry entry : entries) {
      copy.add(Objects.requireNonNull(entry, "entry"));
    }
    this.entries = List.copyOf(copy);
    this.views = this.entries.stream().map(TradeJournalEntry::view).toList();
    this.closedTrades = new FifoMatcher().match(this.entries);
  }

  public List<TradeJournalEntry> entries() {
    return entries;
  }

  public List<JournalEntryView> views() {
    return views;
  }

  public List<ClosedTrade> closedTrades() {
    return closedTrades;
  }

  /** Суммарное проскальзывание всех сделок в деньгах. */
  public BigDecimal slippageTotal() {
    return views.stream()
        .map(JournalEntryView::slippageTotal)
        .reduce(BigDecimal.ZERO, BigDecimal::add)
        .setScale(MONEY_SCALE, RoundingMode.HALF_UP);
  }

  /** Суммарная комиссия всех сделок. */
  public BigDecimal commissionTotal() {
    return views.stream()
        .map(JournalEntryView::commission)
        .reduce(BigDecimal.ZERO, BigDecimal::add)
        .setScale(MONEY_SCALE, RoundingMode.HALF_UP);
  }

  /** Доля прибыльных закрытых FIFO-пар; {@code null}, если закрытых пар нет. */
  public BigDecimal winRate() {
    if (closedTrades.isEmpty()) {
      return null;
    }
    long wins = closedTrades.stream().filter(ClosedTrade::profitable).count();
    return BigDecimal.valueOf(wins)
        .divide(BigDecimal.valueOf(closedTrades.size()), RATE_SCALE, RoundingMode.HALF_UP);
  }

  /** Агрегаты по стратегиям; группы отсортированы по идентификатору, {@code (none)} — первая. */
  public List<StrategyBreakdownEntry> strategyBreakdown() {
    Map<String, StrategyAccumulator> groups = new TreeMap<>();
    for (JournalEntryView view : views) {
      groups.computeIfAbsent(strategyKey(view.strategyId()), ignored -> new StrategyAccumulator())
          .tradeCount++;
    }
    for (ClosedTrade trade : closedTrades) {
      StrategyAccumulator group =
          groups.computeIfAbsent(
              strategyKey(trade.strategyId()), ignored -> new StrategyAccumulator());
      group.realizedPnl = group.realizedPnl.add(trade.pnl());
      group.closedTradeCount++;
      if (trade.profitable()) {
        group.wins++;
      }
    }
    return groups.entrySet().stream()
        .map(
            entry ->
                new StrategyBreakdownEntry(
                    entry.getKey(),
                    entry.getValue().realizedPnl.setScale(MONEY_SCALE, RoundingMode.HALF_UP),
                    entry.getValue().tradeCount,
                    entry.getValue().closedTradeCount,
                    winRate(entry.getValue())))
        .toList();
  }

  /** Журнал сделок в CSV; заголовок всегда присутствует, даже для пустого периода. */
  public String toCsv() {
    StringBuilder csv = new StringBuilder(CSV_HEADER).append('\n');
    for (JournalEntryView view : views) {
      csv.append(csv(view.executedAt().toString()))
          .append(',')
          .append(csv(view.orderId()))
          .append(',')
          .append(csv(view.clientOrderId().toString()))
          .append(',')
          .append(csv(view.ticker()))
          .append(',')
          .append(csv(view.classCode()))
          .append(',')
          .append(csv(view.side().name()))
          .append(',')
          .append(view.quantity())
          .append(',')
          .append(view.referencePrice().toPlainString())
          .append(',')
          .append(view.price().toPlainString())
          .append(',')
          .append(view.slippagePerUnit().toPlainString())
          .append(',')
          .append(view.slippageTotal().toPlainString())
          .append(',')
          .append(view.commission().toPlainString())
          .append(',')
          .append(csv(view.strategyId()))
          .append('\n');
    }
    return csv.toString();
  }

  private static BigDecimal winRate(StrategyAccumulator group) {
    if (group.closedTradeCount == 0) {
      return null;
    }
    return BigDecimal.valueOf(group.wins)
        .divide(BigDecimal.valueOf(group.closedTradeCount), RATE_SCALE, RoundingMode.HALF_UP);
  }

  private static String strategyKey(String strategyId) {
    return strategyId == null ? UNASSIGNED_STRATEGY : strategyId;
  }

  private static String csv(String value) {
    if (value == null) {
      return "";
    }
    if (value.indexOf(',') >= 0
        || value.indexOf('"') >= 0
        || value.indexOf('\n') >= 0
        || value.indexOf('\r') >= 0) {
      return '"' + value.replace("\"", "\"\"") + '"';
    }
    return value;
  }

  private static final class StrategyAccumulator {

    private BigDecimal realizedPnl = BigDecimal.ZERO;
    private int tradeCount;
    private int closedTradeCount;
    private int wins;
  }
}
