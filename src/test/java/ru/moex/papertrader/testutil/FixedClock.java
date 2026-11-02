package ru.moex.papertrader.testutil;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import ru.moex.papertrader.core.marketdata.Clock;

/**
 * Тестовые часы с управляемым моментом времени: реализует порт {@link Clock} и позволяет сдвигать
 * время вперёд, не подменяя системные часы.
 *
 * <p>{@link #advance(Duration)} и {@link #advanceTo(Instant)} меняют текущий момент; экземпляр не
 * потокобезопасен и предназначен для одного теста.
 */
public final class FixedClock implements Clock {

  private Instant now;

  public FixedClock(Instant now) {
    this.now = Objects.requireNonNull(now, "now");
  }

  /** Часы, зафиксированные на заданном моменте. */
  public static FixedClock at(Instant instant) {
    return new FixedClock(instant);
  }

  @Override
  public Instant instant() {
    return now;
  }

  /** Сдвинуть время вперёд на указанный интервал. */
  public void advance(Duration duration) {
    now = now.plus(Objects.requireNonNull(duration, "duration"));
  }

  /** Установить текущий момент времени. */
  public void advanceTo(Instant instant) {
    now = Objects.requireNonNull(instant, "instant");
  }
}
