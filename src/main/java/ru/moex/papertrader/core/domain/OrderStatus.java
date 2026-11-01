package ru.moex.papertrader.core.domain;

import java.util.Optional;

/** Статус жизненного цикла заявки (коды из {@code get-order-by-id}). */
public enum OrderStatus {
  NEW("0"),
  PARTIALLY_FILLED("1"),
  FILLED("2"),
  CANCELED("4"),
  REPLACED("5"),
  CANCELING("6"),
  REJECTED("8"),
  REPLACING("9"),
  PENDING_NEW("10");

  private final String code;

  OrderStatus(String code) {
    this.code = code;
  }

  public String code() {
    return code;
  }

  /** Заявка ещё не завершена и занимает средства/активы. */
  public boolean isActive() {
    return this == NEW
        || this == PARTIALLY_FILLED
        || this == PENDING_NEW
        || this == CANCELING
        || this == REPLACING;
  }

  /** Контракт БКС разрешает отмену статусов 0, 1, 5 и 10. */
  public boolean canBeCancelled() {
    return this == NEW || this == PARTIALLY_FILLED || this == REPLACED || this == PENDING_NEW;
  }

  public static Optional<OrderStatus> fromCode(String code) {
    for (OrderStatus status : values()) {
      if (status.code.equals(code)) {
        return Optional.of(status);
      }
    }
    return Optional.empty();
  }
}
