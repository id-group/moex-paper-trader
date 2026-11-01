package ru.moex.papertrader.core.domain;

import java.util.Optional;

/** Время жизни заявки в кодировке контракта БКС. */
public enum TimeInForce {
  DAY("1"),
  FILL_OR_KILL("2"),
  GOOD_TILL_DATE("3");

  private final String code;

  TimeInForce(String code) {
    this.code = code;
  }

  public String code() {
    return code;
  }

  /** GTD применяется только к алгоритмическим заявкам, которые появятся на этапе 5. */
  public boolean isSupported() {
    return this != GOOD_TILL_DATE;
  }

  public static Optional<TimeInForce> fromCode(String code) {
    for (TimeInForce value : values()) {
      if (value.code.equals(code)) {
        return Optional.of(value);
      }
    }
    return Optional.empty();
  }
}
