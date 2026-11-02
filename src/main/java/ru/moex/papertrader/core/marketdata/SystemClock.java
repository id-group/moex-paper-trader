package ru.moex.papertrader.core.marketdata;

import java.time.Instant;

/**
 * Системные часы (UTC). Единственное место в core, которому разрешено опираться на системное время;
 * остальные классы core получают время только через порт {@link Clock}.
 */
public final class SystemClock implements Clock {

  private final java.time.Clock delegate;

  public SystemClock() {
    this(java.time.Clock.systemUTC());
  }

  /** Для тестов: фиксированный источник времени. */
  SystemClock(java.time.Clock delegate) {
    this.delegate = delegate;
  }

  @Override
  public Instant instant() {
    return delegate.instant();
  }
}
