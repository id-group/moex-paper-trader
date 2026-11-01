package ru.moex.papertrader.core.domain;

import java.util.Optional;

/** Тип заявки в кодировке контракта БКС (поле {@code orderType} запроса на создание). */
public enum OrderType {
  MARKET("1"),
  LIMIT("2"),
  ALGORITHMIC_GTD("3"),
  ICEBERG("4"),
  CONDITIONAL("5");

  private final String code;

  OrderType(String code) {
    this.code = code;
  }

  public String code() {
    return code;
  }

  /** Этап 2 поддерживает только рыночные и лимитные заявки; остальные — этап 5. */
  public boolean isSupported() {
    return this == MARKET || this == LIMIT;
  }

  public static Optional<OrderType> fromCode(String code) {
    for (OrderType type : values()) {
      if (type.code.equals(code)) {
        return Optional.of(type);
      }
    }
    return Optional.empty();
  }
}
