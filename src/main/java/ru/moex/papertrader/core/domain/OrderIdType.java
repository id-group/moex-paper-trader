package ru.moex.papertrader.core.domain;

import java.util.Optional;

/** Тип идентификатора заявки в запросах статуса и отмены. */
public enum OrderIdType {
  CLIENT("1"),
  EXCHANGE("2");

  private final String code;

  OrderIdType(String code) {
    this.code = code;
  }

  public String code() {
    return code;
  }

  public static Optional<OrderIdType> fromCode(String code) {
    for (OrderIdType type : values()) {
      if (type.code.equals(code)) {
        return Optional.of(type);
      }
    }
    return Optional.empty();
  }
}
