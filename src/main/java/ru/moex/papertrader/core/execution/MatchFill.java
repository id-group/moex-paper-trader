package ru.moex.papertrader.core.execution;

import java.math.BigDecimal;
import java.util.Objects;

/** Запланированное исполнение: цена после проскальзывания и объём в штуках. */
public record MatchFill(BigDecimal price, long quantity) {

  public MatchFill {
    Objects.requireNonNull(price, "price");
    if (price.signum() <= 0) {
      throw new IllegalArgumentException("price должен быть положительным");
    }
    if (quantity <= 0) {
      throw new IllegalArgumentException("quantity должен быть больше 0");
    }
  }
}
