package ru.moex.papertrader.core.marketdata;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * Виртуальные часы таймлайна REPLAY. Текущий момент двигается только явными вызовами {@link
 * #advance(Duration)} и {@link #advanceTo(Instant)}, поэтому один и тот же таймлайн и одна и та же
 * последовательность сдвигов дают одинаковый результат в любом прогоне.
 *
 * <p>Класс потокобезопасен: чтение и сдвиг времени синхронизированы.
 */
public final class VirtualClock implements Clock {

  private final Instant start;
  private Instant current;

  /**
   * Создаёт часы, установленные на начальный момент таймлайна.
   *
   * @param start начальный момент, не {@code null}
   */
  public VirtualClock(Instant start) {
    this.start = Objects.requireNonNull(start, "start");
    this.current = start;
  }

  /** Текущий момент виртуального таймлайна. */
  @Override
  public synchronized Instant instant() {
    return current;
  }

  /**
   * Сдвигает часы вперёд на указанную длительность.
   *
   * @param duration неотрицательная длительность; ноль допустим
   * @throws IllegalArgumentException если длительность отрицательна
   */
  public synchronized void advance(Duration duration) {
    Objects.requireNonNull(duration, "duration");
    if (duration.isNegative()) {
      throw new IllegalArgumentException("Виртуальные часы нельзя перевести назад: " + duration);
    }
    current = current.plus(duration);
  }

  /**
   * Переводит часы на указанный момент.
   *
   * @param instant момент не раньше текущего
   * @throws IllegalArgumentException если момент раньше текущего
   */
  public synchronized void advanceTo(Instant instant) {
    Objects.requireNonNull(instant, "instant");
    if (instant.isBefore(current)) {
      throw new IllegalArgumentException(
          "Виртуальные часы нельзя перевести назад: " + instant + " < " + current);
    }
    current = instant;
  }

  /** Начальный момент таймлайна; не меняется за время жизни часов. */
  public Instant start() {
    return start;
  }
}
