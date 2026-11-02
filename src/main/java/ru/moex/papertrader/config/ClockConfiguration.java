package ru.moex.papertrader.config;

import java.time.Instant;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import ru.moex.papertrader.core.marketdata.Clock;
import ru.moex.papertrader.core.marketdata.SystemClock;
import ru.moex.papertrader.core.marketdata.VirtualClock;
import ru.moex.papertrader.infra.services.MarketDataSnapshotStore;
import ru.moex.papertrader.infra.services.ReplayProperties;

/**
 * Порт времени приложения: в обычных режимах — системные часы (UTC), в REPLAY — виртуальные,
 * синхронизированные с проигрывателем таймлайна (продвигает {@code ReplayClockAdvancer}).
 */
@Configuration
public class ClockConfiguration {

  @Bean
  public Clock clock(
      @Value("${emulator.marketdata.mode:STUB}") String mode,
      MarketDataSnapshotStore store,
      ReplayProperties replay) {
    if ("REPLAY".equalsIgnoreCase(mode)) {
      return new VirtualClock(replayStart(replay, store));
    }
    return new SystemClock();
  }

  /**
   * Начало таймлайна REPLAY: явный {@code replay.start} либо первый снапшот записи; без записи —
   * текущий момент через {@link SystemClock} (санкционированный источник времени, прогон без данных
   * эквивалентен пустому календарю).
   */
  private static Instant replayStart(ReplayProperties replay, MarketDataSnapshotStore store) {
    Instant start = replay.start();
    if (start != null) {
      return start;
    }
    Instant earliest = store.earliestTimestamp();
    return earliest != null ? earliest : new SystemClock().instant();
  }
}
