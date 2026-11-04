package ru.moex.papertrader.core.reporting;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/**
 * Канонический отчёт прогона: кривая NAV, метрики, журнал сделок, разбивка по стратегиям и
 * сравнение с бэктестом. Используется одновременно для JSON-ответа, файлового экспорта и хранения в
 * БД.
 *
 * @param label метка прогона (например, {@code REPLAY-2026-11-02}); может быть {@code null}
 * @param backtest результат сравнения; {@code null}, если сравнение не запрашивалось
 */
public record ReportPayload(
    String accountId,
    String label,
    Instant generatedAt,
    LocalDate periodFrom,
    LocalDate periodTo,
    List<NavPoint> navSeries,
    PerformanceMetrics metrics,
    List<JournalEntryView> journal,
    BigDecimal slippageTotal,
    BigDecimal commissionTotal,
    List<ClosedTrade> closedTrades,
    BigDecimal winRate,
    List<StrategyBreakdownEntry> strategies,
    BacktestComparison backtest) {

  public ReportPayload {
    requireText(accountId, "accountId");
    Objects.requireNonNull(generatedAt, "generatedAt");
    Objects.requireNonNull(periodFrom, "periodFrom");
    Objects.requireNonNull(periodTo, "periodTo");
    if (periodTo.isBefore(periodFrom)) {
      throw new IllegalArgumentException("periodTo не может быть раньше periodFrom");
    }
    Objects.requireNonNull(navSeries, "navSeries");
    navSeries = List.copyOf(navSeries);
    Objects.requireNonNull(metrics, "metrics");
    Objects.requireNonNull(journal, "journal");
    journal = List.copyOf(journal);
    Objects.requireNonNull(slippageTotal, "slippageTotal");
    Objects.requireNonNull(commissionTotal, "commissionTotal");
    Objects.requireNonNull(closedTrades, "closedTrades");
    closedTrades = List.copyOf(closedTrades);
    Objects.requireNonNull(strategies, "strategies");
    strategies = List.copyOf(strategies);
  }

  private static void requireText(String value, String name) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(name + " не может быть пустым");
    }
  }
}
