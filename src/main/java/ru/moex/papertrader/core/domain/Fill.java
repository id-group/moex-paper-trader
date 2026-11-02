package ru.moex.papertrader.core.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

/**
 * Исполненная сделка: фактическая цена (после проскальзывания на этапе 5), объём, комиссия
 * отдельным полем и момент исполнения.
 *
 * <p>Момент исполнения передаётся вызывающим кодом из порта {@code Clock}; собственными часами
 * запись не пользуется.
 */
public record Fill(BigDecimal price, long quantity, BigDecimal commission, Instant executedAt) {

  public Fill {
    if (price == null || price.signum() <= 0) {
      throw new IllegalArgumentException("price должен быть положительным");
    }
    if (quantity <= 0) {
      throw new IllegalArgumentException("quantity должен быть больше 0");
    }
    if (commission == null || commission.signum() < 0) {
      throw new IllegalArgumentException("commission не может быть отрицательной");
    }
    Objects.requireNonNull(executedAt, "executedAt");
  }
}
