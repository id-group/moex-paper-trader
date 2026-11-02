package ru.moex.papertrader.core.portfolio;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Денежные константы портфельного блока: единая точка округления сумм.
 *
 * <p>Все денежные величины в портфеле хранятся в {@link BigDecimal} с масштабом {@link #SCALE} и
 * округлением {@link #ROUNDING_MODE}. Выбор 2 знаков и HALF_UP соответствует учёту в рублях и
 * копейках.
 */
public final class Money {

  /** Масштаб денежных величин (рубли и копейки). */
  public static final int SCALE = 2;

  /** Правило округления денежных величин. */
  public static final RoundingMode ROUNDING_MODE = RoundingMode.HALF_UP;

  /** Ноль в денежном масштабе. */
  public static final BigDecimal ZERO = BigDecimal.ZERO.setScale(SCALE, ROUNDING_MODE);

  private Money() {}

  /** Приводит сумму к денежному масштабу; {@code null} считается ошибкой программирования. */
  public static BigDecimal round(BigDecimal amount) {
    if (amount == null) {
      throw new IllegalArgumentException("amount не может быть null");
    }
    return amount.setScale(SCALE, ROUNDING_MODE);
  }
}
