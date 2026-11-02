package ru.moex.papertrader.core.portfolio;

import java.math.BigDecimal;
import java.util.Objects;
import ru.moex.papertrader.core.domain.Instrument;

/**
 * Позиция виртуального портфеля: инструмент, количество со знаком (long — положительное, short —
 * отрицательное) и средняя цена.
 *
 * <p>Средняя цена всегда неотрицательна; направление позиции задаётся только знаком количества.
 */
public record PortfolioPosition(Instrument instrument, long quantity, BigDecimal averagePrice) {

  public PortfolioPosition {
    Objects.requireNonNull(instrument, "instrument");
    if (quantity == 0) {
      throw new IllegalArgumentException("quantity не может быть 0");
    }
    if (averagePrice == null || averagePrice.signum() < 0) {
      throw new IllegalArgumentException("averagePrice не может быть отрицательной");
    }
  }
}
