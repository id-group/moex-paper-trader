package ru.moex.papertrader.core.portfolio;

import java.math.BigDecimal;

/**
 * Проверка обеспечения перед сделкой: доступные средства сравниваются с требуемым начальным ГО.
 *
 * <p>Проверка не меняет состояние портфеля и не бросает исключение при нехватке средств —
 * возвращает {@link MarginCheckResult}. Исключения остаются только для ошибок программирования
 * (null, некорректная цена).
 */
public final class MarginCheck {

  private final MarginCalculator marginCalculator;

  public MarginCheck(MarginCalculator marginCalculator) {
    if (marginCalculator == null) {
      throw new IllegalArgumentException("marginCalculator не может быть null");
    }
    this.marginCalculator = marginCalculator;
  }

  /** Проверка со ставкой ГО по умолчанию (10%). */
  public static MarginCheck withDefaults() {
    return new MarginCheck(MarginCalculator.withDefaults());
  }

  /** Проверяет, хватает ли доступных средств на начальное ГО сделки. */
  public MarginCheckResult checkTrade(
      String ticker, long quantity, BigDecimal price, BigDecimal availableCash) {
    return check(availableCash, marginCalculator.initialMargin(ticker, quantity, price));
  }

  /** Сравнивает доступные средства с уже рассчитанным требуемым ГО. */
  public MarginCheckResult check(BigDecimal availableCash, BigDecimal requiredMargin) {
    if (availableCash == null) {
      throw new IllegalArgumentException("availableCash не может быть null");
    }
    if (requiredMargin == null) {
      throw new IllegalArgumentException("requiredMargin не может быть null");
    }
    // Нулевое ГО допустимо (например, для инструмента без блокировки), поэтому отказ только при
    // строгом дефиците.
    if (availableCash.compareTo(requiredMargin) < 0) {
      return MarginCheckResult.reject(
          availableCash,
          requiredMargin,
          "недостаточно обеспечения: требуется "
              + requiredMargin.toPlainString()
              + ", доступно "
              + availableCash.toPlainString());
    }
    return MarginCheckResult.permit(availableCash, requiredMargin);
  }
}
