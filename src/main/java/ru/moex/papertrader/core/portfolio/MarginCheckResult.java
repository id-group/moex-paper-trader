package ru.moex.papertrader.core.portfolio;

import java.math.BigDecimal;

/**
 * Результат проверки обеспечения перед сделкой.
 *
 * <p>Отказ — штатный исход проверки, поэтому возвращается значением, а не исключением: вызывающий
 * код сам решает, отклонить заявку или показать причину пользователю.
 */
public record MarginCheckResult(
    boolean allowed, BigDecimal availableCash, BigDecimal requiredMargin, String reason) {

  public MarginCheckResult {
    if (availableCash == null) {
      throw new IllegalArgumentException("availableCash не может быть null");
    }
    if (requiredMargin == null) {
      throw new IllegalArgumentException("requiredMargin не может быть null");
    }
    reason = reason == null ? "" : reason;
  }

  /** Разрешающий результат. */
  public static MarginCheckResult permit(BigDecimal availableCash, BigDecimal requiredMargin) {
    return new MarginCheckResult(true, availableCash, requiredMargin, "");
  }

  /** Отказ с человекочитаемой причиной. */
  public static MarginCheckResult reject(
      BigDecimal availableCash, BigDecimal requiredMargin, String reason) {
    return new MarginCheckResult(false, availableCash, requiredMargin, reason);
  }
}
