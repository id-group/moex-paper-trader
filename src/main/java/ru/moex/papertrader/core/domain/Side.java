package ru.moex.papertrader.core.domain;

import java.util.Optional;

/** Направление заявки/сделки в кодировке контракта БКС. */
public enum Side {
  BUY("1"),
  SELL("2");

  private final String code;

  Side(String code) {
    this.code = code;
  }

  public String code() {
    return code;
  }

  public static Optional<Side> fromCode(String code) {
    for (Side side : values()) {
      if (side.code.equals(code)) {
        return Optional.of(side);
      }
    }
    return Optional.empty();
  }
}
