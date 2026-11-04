package ru.moex.papertrader.core.reporting;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/**
 * Сравнение метрик прогона с эталонным бэктестом.
 *
 * <p>Сравниваются только метрики, присутствующие в обоих наборах и не равные {@code null}.
 * Относительное отклонение — {@code |actual - benchmark| / |benchmark|}; оно сравнивается с порогом
 * (по умолчанию 30%). Если эталонная метрика равна нулю, относительное отклонение не определено:
 * такая метрика проходит только при точном нуле прогона.
 *
 * <p>Итоговый флаг {@code PASS} требует хотя бы одной сопоставимой метрики: пустое сравнение не
 * считается успешным.
 */
public final class BacktestComparator {

  /** Порог расхождения по умолчанию: 30%. */
  public static final BigDecimal DEFAULT_THRESHOLD = new BigDecimal("0.30");

  /** Стабильный порядок метрик в отчёте. */
  public static final List<String> METRIC_ORDER =
      List.of("cagr", "sharpe", "sortino", "maxDrawdown", "calmar", "winRate");

  private static final int SCALE = 6;

  public BacktestComparison compare(
      PerformanceMetrics actual, PerformanceMetrics benchmark, BigDecimal threshold) {
    Objects.requireNonNull(actual, "actual");
    Objects.requireNonNull(benchmark, "benchmark");
    return compare(toMap(actual), toMap(benchmark), threshold);
  }

  /** Сравнение произвольного набора метрик; ключи вне {@link #METRIC_ORDER} идут после них. */
  public BacktestComparison compare(
      Map<String, BigDecimal> actual, Map<String, BigDecimal> benchmark, BigDecimal threshold) {
    Objects.requireNonNull(actual, "actual");
    Objects.requireNonNull(benchmark, "benchmark");
    Objects.requireNonNull(threshold, "threshold");
    if (threshold.signum() < 0) {
      throw new IllegalArgumentException("threshold не может быть отрицательным");
    }

    List<MetricDeviation> deviations = new ArrayList<>();
    for (String metric : orderedMetrics(benchmark.keySet())) {
      BigDecimal expected = benchmark.get(metric);
      BigDecimal received = actual.get(metric);
      if (expected == null || received == null) {
        continue;
      }
      BigDecimal absolute = received.subtract(expected).abs().setScale(SCALE, RoundingMode.HALF_UP);
      BigDecimal percent =
          expected.signum() == 0
              ? null
              : absolute.divide(expected.abs(), SCALE, RoundingMode.HALF_UP);
      boolean within = percent == null ? received.signum() == 0 : percent.compareTo(threshold) <= 0;
      deviations.add(new MetricDeviation(metric, received, expected, absolute, percent, within));
    }
    boolean passed =
        !deviations.isEmpty() && deviations.stream().allMatch(MetricDeviation::withinThreshold);
    return new BacktestComparison(deviations, threshold, passed);
  }

  /** Представление метрик в виде карты; {@code null}-значения сохраняются как «не определено». */
  public static Map<String, BigDecimal> toMap(PerformanceMetrics metrics) {
    Objects.requireNonNull(metrics, "metrics");
    Map<String, BigDecimal> values = new LinkedHashMap<>();
    values.put("cagr", metrics.cagr());
    values.put("sharpe", metrics.sharpe());
    values.put("sortino", metrics.sortino());
    values.put("maxDrawdown", metrics.maxDrawdown());
    values.put("calmar", metrics.calmar());
    return values;
  }

  private static List<String> orderedMetrics(Set<String> benchmarkKeys) {
    Set<String> ordered = new LinkedHashSet<>(METRIC_ORDER);
    ordered.addAll(new TreeSet<>(benchmarkKeys));
    List<String> result = new ArrayList<>();
    for (String metric : ordered) {
      if (benchmarkKeys.contains(metric)) {
        result.add(metric);
      }
    }
    return result;
  }
}
