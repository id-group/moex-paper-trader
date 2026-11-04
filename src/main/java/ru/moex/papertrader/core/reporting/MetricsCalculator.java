package ru.moex.papertrader.core.reporting;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Расчёт метрик по дневному ряду NAV.
 *
 * <p>Годовая база — 252 торговых дня. Доходность дня: {@code r_t = NAV_t / NAV_(t-1) - 1}.
 * Используются простые (арифметические) дневные доходности, а не внутридневные.
 *
 * <p>Формулы:
 *
 * <ul>
 *   <li>CAGR = {@code (NAV_last / NAV_first)^(252 / (N - 1)) - 1};
 *   <li>Sharpe = {@code (mean(r) - rf_daily) / stdev(r) * sqrt(252)}, {@code rf_daily = rf / 252},
 *       выборочное стандартное отклонение ({@code N - 2} в знаменателе);
 *   <li>Sortino = {@code (mean(r) - rf_daily) / downside * sqrt(252)}, downside deviation — {@code
 *       sqrt(mean(min(r - rf_daily, 0)^2))} по всем дневным доходностям;
 *   <li>Max Drawdown = {@code max((peak - NAV_t) / peak)} по всей истории;
 *   <li>Calmar = {@code CAGR / Max Drawdown}.
 * </ul>
 *
 * <p>Неопределённые значения возвращаются как {@code null}: пустой или одноточечный ряд,
 * неположительный NAV (деление на ноль/отрицательная база), нулевое стандартное отклонение
 * (Sharpe), отсутствие убыточных дней (Sortino), нулевая просадка (Calmar). Значения округляются до
 * шести знаков по {@link RoundingMode#HALF_UP}.
 */
public final class MetricsCalculator {

  /** Годовая база MOEX: 252 торговых дня. */
  public static final int TRADING_DAYS_PER_YEAR = 252;

  private static final int SCALE = 6;
  private static final double EPSILON = 1e-12;

  private final BigDecimal riskFreeRate;

  /**
   * @param riskFreeRate годовая безрисковая ставка долей ({@code 0.16} — 16%), не {@code null}
   */
  public MetricsCalculator(BigDecimal riskFreeRate) {
    this.riskFreeRate = Objects.requireNonNull(riskFreeRate, "riskFreeRate");
  }

  /** Метрики по ряду NAV; порядок точек значения не имеет, дубли дат запрещены. */
  public PerformanceMetrics calculate(List<NavPoint> series) {
    List<NavPoint> points = normalize(series);
    int observations = points.size();
    if (observations < 2) {
      return undefined(observations);
    }
    for (NavPoint point : points) {
      if (point.nav().signum() <= 0) {
        return undefined(observations);
      }
    }

    double first = points.get(0).nav().doubleValue();
    double last = points.get(observations - 1).nav().doubleValue();
    double cagr = Math.pow(last / first, (double) TRADING_DAYS_PER_YEAR / (observations - 1)) - 1;

    double[] returns = new double[observations - 1];
    double sum = 0;
    for (int i = 1; i < observations; i++) {
      double previous = points.get(i - 1).nav().doubleValue();
      double current = points.get(i).nav().doubleValue();
      returns[i - 1] = current / previous - 1;
      sum += returns[i - 1];
    }
    double mean = sum / returns.length;
    double dailyRiskFree = riskFreeRate.doubleValue() / TRADING_DAYS_PER_YEAR;
    double excessMean = mean - dailyRiskFree;

    BigDecimal sharpe = nullIfDegenerate(excessMean, sampleStandardDeviation(returns, mean));
    BigDecimal sortino = nullIfDegenerate(excessMean, downsideDeviation(returns, dailyRiskFree));
    BigDecimal maxDrawdown = maxDrawdown(points);
    BigDecimal cagrValue = round(cagr);
    // Calmar считается от неокруглённого CAGR: иначе повторное округление сдвигало бы результат.
    BigDecimal calmar =
        maxDrawdown != null && maxDrawdown.signum() > 0
            ? round(cagr / maxDrawdown.doubleValue())
            : null;

    return new PerformanceMetrics(cagrValue, sharpe, sortino, maxDrawdown, calmar, observations);
  }

  private static PerformanceMetrics undefined(int observations) {
    return new PerformanceMetrics(null, null, null, null, null, observations);
  }

  /** Выборочное стандартное отклонение дневных доходностей. */
  private static double sampleStandardDeviation(double[] returns, double mean) {
    if (returns.length < 2) {
      return 0;
    }
    double sum = 0;
    for (double value : returns) {
      double deviation = value - mean;
      sum += deviation * deviation;
    }
    return Math.sqrt(sum / (returns.length - 1));
  }

  /** Downside deviation относительно дневной безрисковой ставки. */
  private static double downsideDeviation(double[] returns, double dailyRiskFree) {
    double sum = 0;
    for (double value : returns) {
      double shortfall = Math.min(value - dailyRiskFree, 0);
      sum += shortfall * shortfall;
    }
    return Math.sqrt(sum / returns.length);
  }

  private static BigDecimal nullIfDegenerate(double numerator, double denominator) {
    if (!Double.isFinite(denominator) || Math.abs(denominator) < EPSILON) {
      return null;
    }
    double value = numerator / denominator * Math.sqrt(TRADING_DAYS_PER_YEAR);
    return round(value);
  }

  private static BigDecimal maxDrawdown(List<NavPoint> points) {
    double peak = points.get(0).nav().doubleValue();
    double max = 0;
    for (NavPoint point : points) {
      double nav = point.nav().doubleValue();
      if (nav > peak) {
        peak = nav;
      }
      double drawdown = (peak - nav) / peak;
      if (drawdown > max) {
        max = drawdown;
      }
    }
    return round(Math.abs(max) < EPSILON ? 0 : max);
  }

  private static BigDecimal round(double value) {
    if (!Double.isFinite(value)) {
      return null;
    }
    return BigDecimal.valueOf(value).setScale(SCALE, RoundingMode.HALF_UP);
  }

  private static List<NavPoint> normalize(List<NavPoint> series) {
    Objects.requireNonNull(series, "series");
    List<NavPoint> points = new ArrayList<>(series.size());
    Set<java.time.LocalDate> dates = new HashSet<>();
    for (NavPoint point : series) {
      Objects.requireNonNull(point, "point");
      if (!dates.add(point.date())) {
        throw new IllegalArgumentException("Дублирующаяся дата NAV: " + point.date());
      }
      points.add(point);
    }
    points.sort(Comparator.comparing(NavPoint::date));
    return List.copyOf(points);
  }
}
