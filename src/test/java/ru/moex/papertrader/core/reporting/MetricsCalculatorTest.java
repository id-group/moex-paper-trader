package ru.moex.papertrader.core.reporting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Метрики на синтетических рядах с ручным расчётом.
 *
 * <p>Годовая база — 252 дня. Для ряда из четырёх точек доходности считаются по трём интервалам,
 * поэтому CAGR возводится в степень {@code 252 / 3 = 84} и получается большим — это ожидаемое
 * следствие короткого синтетического ряда, а не ошибка.
 */
class MetricsCalculatorTest {

  private static final BigDecimal ZERO_RISK_FREE = BigDecimal.ZERO;

  @Test
  void constantGrowthHasNoRiskAndNoDrawdown() {
    // 1% в день: 100 → 101 → 102.01 → 103.0301 → 104.060401.
    // CAGR = 1.04060401^(252/4) - 1 = 1.04060401^63 - 1 = 11.274002.
    MetricsCalculator calculator = new MetricsCalculator(ZERO_RISK_FREE);

    PerformanceMetrics metrics =
        calculator.calculate(
            points(
                "2026-01-01", "100",
                "2026-01-02", "101",
                "2026-01-03", "102.01",
                "2026-01-04", "103.0301",
                "2026-01-05", "104.060401"));

    assertThat(metrics.observations()).isEqualTo(5);
    assertThat(metrics.cagr()).isEqualByComparingTo("11.274002");
    assertThat(metrics.maxDrawdown()).isEqualByComparingTo("0");
    assertThat(metrics.sharpe())
        .as("стандартное отклонение постоянных доходностей равно нулю")
        .isNull();
    assertThat(metrics.sortino()).as("нет убыточных дней").isNull();
    assertThat(metrics.calmar()).as("нулевая просадка").isNull();
  }

  @Test
  void sawtoothSeriesMatchesManualSharpeSortinoAndDrawdown() {
    // 100 → 110 → 99 → 108.9: доходности 0.1, -0.1, 0.1.
    // mean = 0.0333333; выборочное sd = 0.1154701;
    // Sharpe = 0.0333333 / 0.1154701 * sqrt(252) = 4.582576.
    // downside deviation = sqrt((0 + 0.01 + 0) / 3) = 0.0577350;
    // Sortino = 0.0333333 / 0.0577350 * sqrt(252) = 9.165151.
    // MaxDD = (110 - 99) / 110 = 0.1.
    // CAGR = 1.089^84 - 1 = 1288.264129; Calmar = 1288.26412888836 / 0.1 = 12882.641289.
    MetricsCalculator calculator = new MetricsCalculator(ZERO_RISK_FREE);

    PerformanceMetrics metrics =
        calculator.calculate(
            points(
                "2026-01-01",
                "100",
                "2026-01-02",
                "110",
                "2026-01-03",
                "99",
                "2026-01-04",
                "108.9"));

    assertThat(metrics.sharpe()).isEqualByComparingTo("4.582576");
    assertThat(metrics.sortino()).isEqualByComparingTo("9.165151");
    assertThat(metrics.maxDrawdown()).isEqualByComparingTo("0.1");
    assertThat(metrics.cagr()).isEqualByComparingTo("1288.264129");
    assertThat(metrics.calmar()).isEqualByComparingTo("12882.641289");
  }

  @Test
  void deepDrawdownIsMeasuredFromThePeak() {
    // 100 → 120 → 90 → 110: MaxDD = (120 - 90) / 120 = 0.25.
    MetricsCalculator calculator = new MetricsCalculator(ZERO_RISK_FREE);

    PerformanceMetrics metrics =
        calculator.calculate(
            points(
                "2026-01-01", "100", "2026-01-02", "120", "2026-01-03", "90", "2026-01-04", "110"));

    assertThat(metrics.maxDrawdown()).isEqualByComparingTo("0.25");
  }

  @Test
  void riskFreeRateReducesSharpe() {
    List<NavPoint> series =
        points("2026-01-01", "100", "2026-01-02", "110", "2026-01-03", "99", "2026-01-04", "108.9");

    PerformanceMetrics withoutRiskFree = new MetricsCalculator(BigDecimal.ZERO).calculate(series);
    PerformanceMetrics withRiskFree =
        new MetricsCalculator(new BigDecimal("0.252")).calculate(series);

    assertThat(withRiskFree.sharpe()).isLessThan(withoutRiskFree.sharpe());
  }

  @Test
  void emptyAndSinglePointSeriesAreUndefined() {
    MetricsCalculator calculator = new MetricsCalculator(ZERO_RISK_FREE);

    PerformanceMetrics empty = calculator.calculate(List.of());
    PerformanceMetrics single = calculator.calculate(points("2026-01-01", "100"));

    assertThat(empty.observations()).isZero();
    assertThat(empty.cagr()).isNull();
    assertThat(single.observations()).isEqualTo(1);
    assertThat(single.cagr()).isNull();
    assertThat(single.maxDrawdown()).isNull();
  }

  @Test
  void nonPositiveNavMakesMetricsUndefined() {
    MetricsCalculator calculator = new MetricsCalculator(ZERO_RISK_FREE);

    PerformanceMetrics toZero =
        calculator.calculate(points("2026-01-01", "100", "2026-01-02", "0"));
    PerformanceMetrics negative =
        calculator.calculate(points("2026-01-01", "100", "2026-01-02", "-10"));

    assertThat(toZero.observations()).isEqualTo(2);
    assertThat(toZero.cagr()).isNull();
    assertThat(toZero.sharpe()).isNull();
    assertThat(toZero.maxDrawdown()).isNull();
    assertThat(negative.cagr()).isNull();
  }

  @Test
  void seriesIsSortedByDateRegardlessOfInputOrder() {
    MetricsCalculator calculator = new MetricsCalculator(ZERO_RISK_FREE);

    PerformanceMetrics metrics =
        calculator.calculate(
            points("2026-01-03", "102.01", "2026-01-01", "100", "2026-01-02", "101"));

    assertThat(metrics.cagr()).isEqualByComparingTo("11.274002");
  }

  @Test
  void duplicateDatesAreRejected() {
    MetricsCalculator calculator = new MetricsCalculator(ZERO_RISK_FREE);

    assertThatThrownBy(() -> calculator.calculate(points("2026-01-01", "100", "2026-01-01", "101")))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Дублирующаяся дата");
  }

  private static List<NavPoint> points(String... dateAndNav) {
    List<NavPoint> points = new ArrayList<>(dateAndNav.length / 2);
    for (int i = 0; i < dateAndNav.length; i += 2) {
      points.add(new NavPoint(LocalDate.parse(dateAndNav[i]), new BigDecimal(dateAndNav[i + 1])));
    }
    return points;
  }
}
