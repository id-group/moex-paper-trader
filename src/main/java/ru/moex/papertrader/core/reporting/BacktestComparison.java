package ru.moex.papertrader.core.reporting;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

/**
 * Результат сравнения прогона с эталонным бэктестом.
 *
 * @param deviations отклонения по сопоставимым метрикам
 * @param threshold порог относительного отклонения долей ({@code 0.30} — 30%)
 * @param passed {@code true}, если есть хотя бы одна сопоставимая метрика и все уложились в порог
 */
public record BacktestComparison(
    List<MetricDeviation> deviations, BigDecimal threshold, boolean passed) {

  public BacktestComparison {
    Objects.requireNonNull(deviations, "deviations");
    deviations = List.copyOf(deviations);
    Objects.requireNonNull(threshold, "threshold");
  }
}
