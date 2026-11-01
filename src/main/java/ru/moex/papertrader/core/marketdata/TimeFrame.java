package ru.moex.papertrader.core.marketdata;

import java.time.Duration;
import java.util.Optional;

/** Таймфрейм свечей в кодировке контракта БКС. */
public enum TimeFrame {
  M1(Duration.ofMinutes(1)),
  M5(Duration.ofMinutes(5)),
  M15(Duration.ofMinutes(15)),
  M30(Duration.ofMinutes(30)),
  H1(Duration.ofHours(1)),
  H4(Duration.ofHours(4)),
  D(Duration.ofDays(1)),
  W(Duration.ofDays(7)),
  MN(Duration.ofDays(30));

  private final Duration step;

  TimeFrame(Duration step) {
    this.step = step;
  }

  public Duration step() {
    return step;
  }

  public static Optional<TimeFrame> fromCode(String code) {
    for (TimeFrame value : values()) {
      if (value.name().equals(code)) {
        return Optional.of(value);
      }
    }
    return Optional.empty();
  }
}
