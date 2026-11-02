package ru.moex.papertrader.core.marketdata;

import java.time.Instant;

/**
 * Порт времени — единственный источник времени в core. Системное время — {@link SystemClock},
 * виртуальное (таймлайн REPLAY) — {@code VirtualClock} (добавляется на этапе 4).
 */
public interface Clock {

  /** Текущий момент времени. */
  Instant instant();
}
