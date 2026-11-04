package ru.moex.papertrader.core.reporting;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * Входные данные внешнего бэктеста для сравнения с прогоном.
 *
 * <p>Допускается либо ряд NAV (метрики рассчитываются тем же {@link MetricsCalculator}), либо
 * готовый набор метрик, либо оба варианта: готовые метрики имеют приоритет. Пустой вход отклоняется
 * — сравнивать нечего.
 *
 * @param label метка бэктеста; может быть {@code null}
 * @param nav кривая NAV бэктеста; пустой список, если не передана
 * @param metrics готовые метрики в долях; пустая карта, если не переданы
 */
public record BacktestInput(String label, List<NavPoint> nav, Map<String, BigDecimal> metrics) {

  public BacktestInput {
    label = normalizeLabel(label);
    nav = nav == null ? List.of() : List.copyOf(nav);
    metrics = metrics == null ? Map.of() : Map.copyOf(metrics);
    if (nav.isEmpty() && metrics.isEmpty()) {
      throw new IllegalArgumentException("Бэктест должен содержать ряд NAV или набор метрик");
    }
  }

  /** Признак наличия готовых метрик. */
  public boolean hasMetrics() {
    return !metrics.isEmpty();
  }

  /** Признак наличия ряда NAV. */
  public boolean hasNav() {
    return !nav.isEmpty();
  }

  private static String normalizeLabel(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    String trimmed = value.trim();
    if (trimmed.length() > 128) {
      throw new IllegalArgumentException("label не может быть длиннее 128 символов");
    }
    return trimmed;
  }
}
