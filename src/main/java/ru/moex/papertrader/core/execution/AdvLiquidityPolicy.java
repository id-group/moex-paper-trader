package ru.moex.papertrader.core.execution;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * Политика ликвидности по порогу ADV: заявка больше {@code thresholdPercent}% среднего дневного
 * объёма отклоняется ({@link Mode#REJECT}) или дробится на части ({@link Mode#SPLIT}).
 *
 * <p>Часть при SPLIT — не больше {@code floor(ADV × threshold / 100)} штук, минимум 1. Если ADV
 * неизвестен, ограничение не применяется.
 */
public final class AdvLiquidityPolicy implements LiquidityPolicy {

  /** Порог по умолчанию — 5% ADV (спецификация владельца). */
  public static final BigDecimal DEFAULT_THRESHOLD_PERCENT = new BigDecimal("5");

  private static final BigDecimal ONE_HUNDRED = BigDecimal.valueOf(100);

  /** Режим превышения порога. */
  public enum Mode {
    REJECT,
    SPLIT
  }

  private final Mode mode;
  private final BigDecimal thresholdPercent;

  public AdvLiquidityPolicy(Mode mode, BigDecimal thresholdPercent) {
    this.mode = Objects.requireNonNull(mode, "mode");
    this.thresholdPercent = Objects.requireNonNull(thresholdPercent, "thresholdPercent");
    if (thresholdPercent.signum() <= 0 || thresholdPercent.compareTo(ONE_HUNDRED) > 0) {
      throw new IllegalArgumentException("thresholdPercent должен быть в диапазоне (0, 100]");
    }
  }

  /** Политика по умолчанию: REJECT при превышении 5% ADV. */
  public static AdvLiquidityPolicy rejectAtDefaultThreshold() {
    return new AdvLiquidityPolicy(Mode.REJECT, DEFAULT_THRESHOLD_PERCENT);
  }

  @Override
  public LiquidityDecision evaluate(long quantity, BigDecimal adv) {
    if (quantity <= 0) {
      throw new IllegalArgumentException("quantity должен быть больше 0");
    }
    if (adv == null || adv.signum() <= 0) {
      return LiquidityDecision.pass(quantity);
    }
    BigDecimal maxPart = adv.multiply(thresholdPercent).divide(ONE_HUNDRED, 0, RoundingMode.DOWN);
    if (maxPart.signum() <= 0) {
      maxPart = BigDecimal.ONE;
    }
    if (BigDecimal.valueOf(quantity).compareTo(maxPart) <= 0) {
      return LiquidityDecision.pass(quantity);
    }
    String reason =
        "Объём "
            + quantity
            + " превышает "
            + thresholdPercent.stripTrailingZeros().toPlainString()
            + "% ADV ("
            + adv.stripTrailingZeros().toPlainString()
            + ")";
    if (mode == Mode.REJECT) {
      return LiquidityDecision.reject(reason);
    }
    return LiquidityDecision.split(maxPart.longValueExact(), reason);
  }
}
