package ru.moex.papertrader.core.execution;

import java.util.Objects;

/** Решение политики ликвидности по объёму заявки. */
public record LiquidityDecision(Action action, long maxPartQuantity, String reason) {

  /** Действие политики. */
  public enum Action {
    /** Объём в пределах порога. */
    PASS,
    /** Заявка отклоняется целиком. */
    REJECT,
    /** Заявка исполняется частями не больше {@link #maxPartQuantity}. */
    SPLIT
  }

  public LiquidityDecision {
    Objects.requireNonNull(action, "action");
    if (maxPartQuantity <= 0) {
      throw new IllegalArgumentException("maxPartQuantity должен быть больше 0");
    }
    if (action == Action.REJECT && (reason == null || reason.isBlank())) {
      throw new IllegalArgumentException("Причина отклонения обязательна");
    }
    if (action == Action.PASS && reason != null) {
      throw new IllegalArgumentException("Причина допустима только для REJECT и SPLIT");
    }
  }

  public static LiquidityDecision pass(long quantity) {
    return new LiquidityDecision(Action.PASS, quantity, null);
  }

  public static LiquidityDecision reject(String reason) {
    return new LiquidityDecision(Action.REJECT, 1, Objects.requireNonNull(reason, "reason"));
  }

  public static LiquidityDecision split(long maxPartQuantity, String reason) {
    return new LiquidityDecision(
        Action.SPLIT, maxPartQuantity, Objects.requireNonNull(reason, "reason"));
  }
}
