package ru.moex.papertrader.core.reporting;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;

/**
 * Точка кривой NAV: торговый день и стоимость портфеля на закрытие.
 *
 * <p>Отрицательный NAV допустим: убыток может превысить вложенный капитал. Метрики, требующие
 * положительного знаменателя, в этом случае не определены (см. {@link MetricsCalculator}).
 */
public record NavPoint(LocalDate date, BigDecimal nav) {

  public NavPoint {
    Objects.requireNonNull(date, "date");
    Objects.requireNonNull(nav, "nav");
  }
}
