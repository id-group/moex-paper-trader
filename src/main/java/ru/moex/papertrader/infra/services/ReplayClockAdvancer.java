package ru.moex.papertrader.infra.services;

import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import ru.moex.papertrader.core.marketdata.Clock;
import ru.moex.papertrader.core.marketdata.VirtualClock;

/**
 * Продвигает виртуальный таймлайн REPLAY: каждые {@code replay.tick-ms} реальных миллисекунд часы
 * сдвигаются на {@code tick × replay.speed}. Активен только в режиме REPLAY — виртуальный {@link
 * Clock} других режимов не трогает.
 */
@Component
@ConditionalOnProperty(prefix = "emulator.marketdata", name = "mode", havingValue = "REPLAY")
public class ReplayClockAdvancer {

  private static final Logger log = LoggerFactory.getLogger(ReplayClockAdvancer.class);

  private final Clock clock;
  private final double speed;
  private boolean warned;

  public ReplayClockAdvancer(Clock clock, ReplayProperties replay) {
    this.clock = clock;
    this.speed = replay.speed();
  }

  @Scheduled(fixedDelayString = "${emulator.marketdata.replay.tick-ms:100}")
  public void tick() {
    if (!(clock instanceof VirtualClock virtualClock)) {
      if (!warned) {
        log.warn("REPLAY: порт Clock не является VirtualClock — таймлайн не продвигается");
        warned = true;
      }
      return;
    }
    virtualClock.advance(Duration.ofMillis((long) (100 * speed)));
  }
}
