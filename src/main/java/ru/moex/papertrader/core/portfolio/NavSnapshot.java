package ru.moex.papertrader.core.portfolio;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Срез NAV портфеля на момент закрытия основной сессии.
 *
 * <p>NAV = денежные средства − комиссия + начисленная вариационная маржа + рыночная стоимость акций
 * по цене закрытия. Начальное ГО в NAV не входит: это блокировка средств, а не расход. Фьючерсные
 * позиции не добавляют рыночную стоимость — их результат уже учтён вариационной маржой (иначе
 * двойной счёт).
 */
public record NavSnapshot(
    Instant asOf,
    BigDecimal cash,
    BigDecimal commission,
    BigDecimal variationMargin,
    BigDecimal marketValue,
    BigDecimal nav,
    List<Holding> holdings) {

  public NavSnapshot {
    requireNonNull(asOf, "asOf");
    requireNonNull(cash, "cash");
    requireNonNull(commission, "commission");
    requireNonNull(variationMargin, "variationMargin");
    requireNonNull(marketValue, "marketValue");
    requireNonNull(nav, "nav");
    if (commission.signum() < 0) {
      throw new IllegalArgumentException("commission не может быть отрицательной");
    }
    if (holdings == null) {
      throw new IllegalArgumentException("holdings не может быть null");
    }
    holdings = List.copyOf(holdings);
  }

  /** Денежные средства за вычетом комиссии (до начисления вариационной маржи). */
  public BigDecimal netCash() {
    return cash.subtract(commission);
  }

  /** Позиция на закрытие, учтённая в рыночной стоимости. */
  public record Holding(String ticker, long quantity, BigDecimal closingPrice) {

    public Holding {
      if (ticker == null || ticker.isBlank()) {
        throw new IllegalArgumentException("ticker не может быть пустым");
      }
      if (closingPrice == null || closingPrice.signum() <= 0) {
        throw new IllegalArgumentException(
            "closingPrice для " + ticker + " должен быть положительным");
      }
    }
  }

  private static void requireNonNull(Object value, String name) {
    if (value == null) {
      throw new IllegalArgumentException(name + " не может быть null");
    }
  }
}
