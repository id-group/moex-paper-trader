package ru.moex.papertrader.core.portfolio;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

/**
 * Ставки начального гарантийного обеспечения (ГО): значение по умолчанию и переопределения по
 * тикеру.
 *
 * <p>Ставка задаётся долей от стоимости позиции и должна лежать строго между 0 и 1. Поля в {@code
 * Instrument} не добавляются: инструмент общий для всех слоёв, а ставка — параметр портфеля.
 */
public record MarginRates(BigDecimal defaultRate, Map<String, BigDecimal> overrides) {

  /** Ставка ГО по умолчанию — 10% стоимости позиции. */
  public static final BigDecimal DEFAULT_RATE = new BigDecimal("0.10");

  public MarginRates {
    requireValidRate(defaultRate, "defaultRate");
    if (overrides == null) {
      throw new IllegalArgumentException("overrides не может быть null");
    }
    Map<String, BigDecimal> validated = new HashMap<>();
    for (Map.Entry<String, BigDecimal> entry : overrides.entrySet()) {
      String ticker = entry.getKey();
      if (ticker == null || ticker.isBlank()) {
        throw new IllegalArgumentException("тикер в overrides не может быть пустым");
      }
      requireValidRate(entry.getValue(), "overrides[" + ticker + "]");
      validated.put(ticker, entry.getValue());
    }
    overrides = Map.copyOf(validated);
  }

  /** Ставки с дефолтом 10% и без переопределений. */
  public static MarginRates defaults() {
    return new MarginRates(DEFAULT_RATE, Map.of());
  }

  /** Ставка ГО для тикера: переопределение, если задано, иначе ставка по умолчанию. */
  public BigDecimal rateFor(String ticker) {
    if (ticker == null || ticker.isBlank()) {
      throw new IllegalArgumentException("ticker не может быть пустым");
    }
    return overrides.getOrDefault(ticker, defaultRate);
  }

  private static void requireValidRate(BigDecimal rate, String name) {
    if (rate == null) {
      throw new IllegalArgumentException(name + " не может быть null");
    }
    if (rate.signum() <= 0 || rate.compareTo(BigDecimal.ONE) >= 0) {
      throw new IllegalArgumentException(
          name + " должен быть больше 0 и меньше 1: " + rate.toPlainString());
    }
  }
}
