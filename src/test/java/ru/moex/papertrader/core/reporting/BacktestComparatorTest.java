package ru.moex.papertrader.core.reporting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class BacktestComparatorTest {

  private static final BigDecimal THRESHOLD_30 = new BigDecimal("0.30");

  private final BacktestComparator comparator = new BacktestComparator();

  @Test
  void twentyFivePercentDeviationPasses() {
    // |0.125 − 0.100| / 0.100 = 0.25 ≤ 0.30.
    BacktestComparison comparison =
        comparator.compare(
            Map.of("cagr", new BigDecimal("0.125")),
            Map.of("cagr", new BigDecimal("0.100")),
            THRESHOLD_30);

    assertThat(comparison.passed()).isTrue();
    assertThat(comparison.deviations()).hasSize(1);
    MetricDeviation deviation = comparison.deviations().get(0);
    assertThat(deviation.percentDeviation()).isEqualByComparingTo("0.250000");
    assertThat(deviation.absoluteDeviation()).isEqualByComparingTo("0.025000");
    assertThat(deviation.withinThreshold()).isTrue();
  }

  @Test
  void thirtyFivePercentDeviationFails() {
    // |0.135 − 0.100| / 0.100 = 0.35 > 0.30.
    BacktestComparison comparison =
        comparator.compare(
            Map.of("cagr", new BigDecimal("0.135")),
            Map.of("cagr", new BigDecimal("0.100")),
            THRESHOLD_30);

    assertThat(comparison.passed()).isFalse();
    assertThat(comparison.deviations().get(0).percentDeviation()).isEqualByComparingTo("0.350000");
    assertThat(comparison.deviations().get(0).withinThreshold()).isFalse();
  }

  @Test
  void allComparableMetricsMustPass() {
    Map<String, BigDecimal> actual = new LinkedHashMap<>();
    actual.put("cagr", new BigDecimal("0.125"));
    actual.put("sharpe", new BigDecimal("1.5"));
    Map<String, BigDecimal> benchmark = new LinkedHashMap<>();
    benchmark.put("cagr", new BigDecimal("0.100"));
    benchmark.put("sharpe", new BigDecimal("1.0"));

    BacktestComparison comparison = comparator.compare(actual, benchmark, THRESHOLD_30);

    assertThat(comparison.passed()).isFalse();
    assertThat(comparison.deviations()).hasSize(2);
    assertThat(comparison.deviations().get(0).metric()).isEqualTo("cagr");
    assertThat(comparison.deviations().get(1).metric()).isEqualTo("sharpe");
  }

  @Test
  void metricsAbsentInEitherSetAreSkipped() {
    Map<String, BigDecimal> actual = new LinkedHashMap<>();
    actual.put("cagr", new BigDecimal("0.125"));
    actual.put("sortino", new BigDecimal("9"));
    Map<String, BigDecimal> benchmark = new LinkedHashMap<>();
    benchmark.put("cagr", new BigDecimal("0.100"));
    benchmark.put("sharpe", new BigDecimal("1.0"));

    BacktestComparison comparison = comparator.compare(actual, benchmark, THRESHOLD_30);

    assertThat(comparison.deviations()).extracting(MetricDeviation::metric).containsExactly("cagr");
    assertThat(comparison.passed()).isTrue();
  }

  @Test
  void zeroBenchmarkRequiresExactZeroActual() {
    BacktestComparison exact =
        comparator.compare(
            Map.of("winRate", BigDecimal.ZERO), Map.of("winRate", BigDecimal.ZERO), THRESHOLD_30);
    BacktestComparison nonZero =
        comparator.compare(
            Map.of("winRate", new BigDecimal("0.01")),
            Map.of("winRate", BigDecimal.ZERO),
            THRESHOLD_30);

    assertThat(exact.deviations().get(0).percentDeviation()).isNull();
    assertThat(exact.passed()).isTrue();
    assertThat(nonZero.deviations().get(0).percentDeviation()).isNull();
    assertThat(nonZero.passed()).isFalse();
  }

  @Test
  void emptyComparisonCannotPass() {
    BacktestComparison comparison =
        comparator.compare(Map.of(), Map.of("cagr", new BigDecimal("0.1")), THRESHOLD_30);

    assertThat(comparison.deviations()).isEmpty();
    assertThat(comparison.passed()).isFalse();
  }

  @Test
  void comparesPerformanceMetricsObjects() {
    PerformanceMetrics actual =
        new PerformanceMetrics(
            new BigDecimal("0.125"),
            new BigDecimal("1.2"),
            new BigDecimal("1.8"),
            new BigDecimal("0.05"),
            new BigDecimal("2.5"),
            10);
    PerformanceMetrics benchmark =
        new PerformanceMetrics(
            new BigDecimal("0.100"),
            new BigDecimal("1.1"),
            new BigDecimal("1.7"),
            new BigDecimal("0.04"),
            new BigDecimal("2.4"),
            10);

    BacktestComparison comparison = comparator.compare(actual, benchmark, THRESHOLD_30);

    assertThat(comparison.deviations())
        .extracting(MetricDeviation::metric)
        .containsExactly("cagr", "sharpe", "sortino", "maxDrawdown", "calmar");
    assertThat(comparison.passed()).isTrue();
  }

  @Test
  void negativeThresholdIsRejected() {
    assertThatThrownBy(
            () ->
                comparator.compare(
                    Map.of("cagr", BigDecimal.ONE),
                    Map.of("cagr", BigDecimal.ONE),
                    new BigDecimal("-0.1")))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void metricOrderIsStableForReport() {
    assertThat(BacktestComparator.METRIC_ORDER)
        .containsExactly("cagr", "sharpe", "sortino", "maxDrawdown", "calmar", "winRate");
  }
}
