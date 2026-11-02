package ru.moex.papertrader.core.portfolio;

import java.math.BigDecimal;

/**
 * Расчёт начального гарантийного обеспечения (ГО) фьючерсной позиции.
 *
 * <p>Формула: {@code |количество| × цена × ставка ГО}. Цена понимается за единицу инструмента (для
 * фьючерса — за контракт), лот не масштабируется: биржевая цена уже котируется за контракт.
 */
public final class MarginCalculator {

  private final MarginRates rates;

  public MarginCalculator(MarginRates rates) {
    if (rates == null) {
      throw new IllegalArgumentException("rates не может быть null");
    }
    this.rates = rates;
  }

  /** Калькулятор со ставкой по умолчанию (10%) и без переопределений. */
  public static MarginCalculator withDefaults() {
    return new MarginCalculator(MarginRates.defaults());
  }

  /** Начальное ГО позиции; знак количества не важен — ГО блокируется и по короткой позиции. */
  public BigDecimal initialMargin(String ticker, long quantity, BigDecimal price) {
    if (price == null || price.signum() <= 0) {
      throw new IllegalArgumentException("price должен быть положительным");
    }
    BigDecimal positionValue = price.multiply(BigDecimal.valueOf(quantity).abs());
    return Money.round(positionValue.multiply(rates.rateFor(ticker)));
  }
}
