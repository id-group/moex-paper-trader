package ru.moex.papertrader.core.commission;

import java.math.BigDecimal;

/**
 * Тариф комиссии: процент от оборота, минимальная комиссия за сделку и биржевой сбор за контракт.
 *
 * <p>Допущения (в ТЗ этапа 3 не заданы, вынесены владельцу как вопросы): минимальная комиссия акций
 * — 0.01 RUB, биржевой сбор фьючерса — 1.00 RUB за контракт. Значения — дефолты тарифов {@link
 * #stock()} и {@link #futures()}; профиль брокера может подставить свои.
 *
 * @param percent процент от оборота в долях (0.001 = 0.1%), не может быть отрицательным
 * @param minimumCommission минимальная комиссия за сделку, не может быть отрицательной
 * @param exchangeFeePerContract биржевой сбор за контракт (только для фьючерсов), не может быть
 *     отрицательным
 */
public record CommissionTariff(
    BigDecimal percent, BigDecimal minimumCommission, BigDecimal exchangeFeePerContract) {

  private static final BigDecimal STOCK_PERCENT = new BigDecimal("0.001");
  private static final BigDecimal STOCK_MINIMUM_COMMISSION = new BigDecimal("0.01");
  private static final BigDecimal FUTURES_EXCHANGE_FEE = new BigDecimal("1.00");

  public CommissionTariff {
    percent = requireNonNegative(percent, "percent");
    minimumCommission = requireNonNegative(minimumCommission, "minimumCommission");
    exchangeFeePerContract = requireNonNegative(exchangeFeePerContract, "exchangeFeePerContract");
  }

  /** Тариф акций по умолчанию: 0.1% от оборота, минимум 0.01 RUB за сделку. */
  public static CommissionTariff stock() {
    return new CommissionTariff(STOCK_PERCENT, STOCK_MINIMUM_COMMISSION, BigDecimal.ZERO);
  }

  /**
   * Тариф фьючерсов по умолчанию: только фиксированный сбор 1.00 RUB за контракт.
   *
   * <p>Процент и минимум обнулены: по спецификации этапа 3 комиссия фьючерса — сбор за контракт.
   */
  public static CommissionTariff futures() {
    return new CommissionTariff(BigDecimal.ZERO, BigDecimal.ZERO, FUTURES_EXCHANGE_FEE);
  }

  private static BigDecimal requireNonNegative(BigDecimal value, String name) {
    if (value == null) {
      throw new IllegalArgumentException(name + " не может быть null");
    }
    if (value.signum() < 0) {
      throw new IllegalArgumentException(name + " не может быть отрицательным: " + value);
    }
    return value;
  }
}
