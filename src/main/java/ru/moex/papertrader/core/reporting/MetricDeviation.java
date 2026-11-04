package ru.moex.papertrader.core.reporting;

import java.math.BigDecimal;

/**
 * Отклонение одной метрики от эталонного бэктеста.
 *
 * @param metric имя метрики ({@code cagr}, {@code sharpe}, ...)
 * @param actual значение прогона
 * @param benchmark значение бэктеста
 * @param absoluteDeviation абсолютное отклонение {@code |actual - benchmark|}
 * @param percentDeviation относительное отклонение {@code |actual - benchmark| / |benchmark|};
 *     {@code null}, если эталон равен нулю
 * @param withinThreshold укладывается ли отклонение в порог
 */
public record MetricDeviation(
    String metric,
    BigDecimal actual,
    BigDecimal benchmark,
    BigDecimal absoluteDeviation,
    BigDecimal percentDeviation,
    boolean withinThreshold) {}
