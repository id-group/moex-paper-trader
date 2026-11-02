package ru.moex.papertrader.core.marketdata;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class VirtualClockTest {

  private static final Instant START = Instant.parse("2026-11-02T07:00:00Z");

  @Test
  void startsAtInitialInstant() {
    VirtualClock clock = new VirtualClock(START);

    assertThat(clock.instant()).isEqualTo(START);
    assertThat(clock.start()).isEqualTo(START);
  }

  @Test
  void advanceMovesTimeForward() {
    VirtualClock clock = new VirtualClock(START);

    clock.advance(Duration.ofMinutes(5));

    assertThat(clock.instant()).isEqualTo(START.plus(Duration.ofMinutes(5)));
  }

  @Test
  void advanceByZeroIsAllowed() {
    VirtualClock clock = new VirtualClock(START);

    clock.advance(Duration.ZERO);

    assertThat(clock.instant()).isEqualTo(START);
  }

  @Test
  void negativeAdvanceIsRejected() {
    VirtualClock clock = new VirtualClock(START);

    assertThatThrownBy(() -> clock.advance(Duration.ofSeconds(-1)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("назад");

    assertThat(clock.instant()).isEqualTo(START);
  }

  @Test
  void advanceToSetsExactInstant() {
    VirtualClock clock = new VirtualClock(START);
    Instant target = START.plus(Duration.ofHours(2));

    clock.advanceTo(target);

    assertThat(clock.instant()).isEqualTo(target);
  }

  @Test
  void advanceToSameInstantIsAllowed() {
    VirtualClock clock = new VirtualClock(START);

    clock.advanceTo(START);

    assertThat(clock.instant()).isEqualTo(START);
  }

  @Test
  void advanceToPastIsRejected() {
    VirtualClock clock = new VirtualClock(START);
    clock.advance(Duration.ofHours(1));

    assertThatThrownBy(() -> clock.advanceTo(START))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("назад");

    assertThat(clock.instant()).isEqualTo(START.plus(Duration.ofHours(1)));
  }

  @Test
  void startDoesNotChangeAfterAdvances() {
    VirtualClock clock = new VirtualClock(START);

    clock.advance(Duration.ofDays(3));

    assertThat(clock.start()).isEqualTo(START);
    assertThat(clock.instant()).isEqualTo(START.plus(Duration.ofDays(3)));
  }
}
