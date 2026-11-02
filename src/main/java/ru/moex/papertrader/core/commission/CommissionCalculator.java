package ru.moex.papertrader.core.commission;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Расчёт комиссии сделки по тарифу профиля (чистая Java, без Spring).
 *
 * <p>Правило: для акций — процент от оборота, но не меньше минимальной комиссии за сделку; для
 * фьючерсов — фиксированный биржевой сбор за контракт × количество. Схема выбирается по типу
 * инструмента, а не по тарифу. Нулевой оборот (нулевая цена или количество) даёт нулевую комиссию.
 */
public final class CommissionCalculator {

  /**
   * Денежный масштаб комиссии. Все суммы в проекте — деньги в RUB с двумя знаками; HALF_UP выбран
   * как привычное коммерческое округление (HALF_EVEN дал бы 1.005 → 1.00).
   */
  public static final int COMMISSION_SCALE = 2;

  /** Режим округления комиссии — единственное место в расчёте комиссий. */
  public static final RoundingMode COMMISSION_ROUNDING = RoundingMode.HALF_UP;

  private final CommissionTariffRegistry tariffs;

  public CommissionCalculator(CommissionTariffRegistry tariffs) {
    if (tariffs == null) {
      throw new IllegalArgumentException("tariffs не может быть null");
    }
    this.tariffs = tariffs;
  }

  /** Комиссия без переопределения по тикеру. */
  public BigDecimal calculate(InstrumentType type, BigDecimal price, long quantity) {
    return calculate(type, null, price, quantity);
  }

  /**
   * Комиссия сделки.
   *
   * @param type тип инструмента (определяет схему расчёта)
   * @param ticker тикер для поиска переопределения тарифа; {@code null} — только тариф по типу
   * @param price цена за единицу инструмента, не может быть отрицательной
   * @param quantity количество инструмента, не может быть отрицательным
   * @return комиссия в RUB с масштабом {@value #COMMISSION_SCALE}
   */
  public BigDecimal calculate(InstrumentType type, String ticker, BigDecimal price, long quantity) {
    if (type == null) {
      throw new IllegalArgumentException("type не может быть null");
    }
    if (price == null) {
      throw new IllegalArgumentException("price не может быть null");
    }
    if (price.signum() < 0) {
      throw new IllegalArgumentException("price не может быть отрицательным: " + price);
    }
    if (quantity < 0) {
      throw new IllegalArgumentException("quantity не может быть отрицательным: " + quantity);
    }
    if (price.signum() == 0 || quantity == 0) {
      return money(BigDecimal.ZERO);
    }

    CommissionTariff tariff = tariffs.tariffFor(type, ticker);
    BigDecimal tradeQuantity = BigDecimal.valueOf(quantity);
    return switch (type) {
      case STOCK -> stockCommission(tariff, price.multiply(tradeQuantity));
      case FUTURES -> money(tariff.exchangeFeePerContract().multiply(tradeQuantity));
    };
  }

  private static BigDecimal stockCommission(CommissionTariff tariff, BigDecimal turnover) {
    BigDecimal percentCommission = turnover.multiply(tariff.percent());
    BigDecimal commission =
        percentCommission.compareTo(tariff.minimumCommission()) < 0
            ? tariff.minimumCommission()
            : percentCommission;
    return money(commission);
  }

  private static BigDecimal money(BigDecimal value) {
    return value.setScale(COMMISSION_SCALE, COMMISSION_ROUNDING);
  }
}
