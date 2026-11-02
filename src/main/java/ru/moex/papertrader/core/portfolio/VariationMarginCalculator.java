package ru.moex.papertrader.core.portfolio;

import java.math.BigDecimal;

/**
 * Ежедневная переоценка позиции: вариационная маржа (ВМ) как денежная дельта.
 *
 * <p>Формула: {@code количество × (расчётная цена сегодня − расчётная цена вчера)}. Знак количества
 * задаёт направление позиции: положительное — длинная, отрицательное — короткая. Калькулятор ничего
 * не записывает: начисление ВМ на счёт — ответственность вызывающего кода.
 */
public final class VariationMarginCalculator {

  /** ВМ позиции за торговый день. */
  public BigDecimal variationMargin(
      long quantity, BigDecimal todaySettlementPrice, BigDecimal previousSettlementPrice) {
    requirePositivePrice(todaySettlementPrice, "todaySettlementPrice");
    requirePositivePrice(previousSettlementPrice, "previousSettlementPrice");
    BigDecimal priceDelta = todaySettlementPrice.subtract(previousSettlementPrice);
    return Money.round(priceDelta.multiply(BigDecimal.valueOf(quantity)));
  }

  private static void requirePositivePrice(BigDecimal price, String name) {
    if (price == null) {
      throw new IllegalArgumentException(name + " не может быть null");
    }
    if (price.signum() <= 0) {
      throw new IllegalArgumentException(name + " должен быть положительным");
    }
  }
}
