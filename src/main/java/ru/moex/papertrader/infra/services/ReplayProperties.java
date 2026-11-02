package ru.moex.papertrader.infra.services;

import java.time.Instant;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Настройки режима REPLAY: начало таймлайна, скорость воспроизведения и seed стохастических моделей
 * (проскальзывание — этап 5). Ключи yml — {@code emulator.marketdata.replay.*}.
 *
 * <p>{@code start} может быть пустым: тогда таймлайн начинается с первого снапшота записи.
 *
 * @param start начало таймлайна (ISO-8601, UTC); {@code null} — с первого снапшота записи
 * @param speed множитель скорости воспроизведения (1.0 — реальное время)
 * @param seed seed генераторов детерминированных моделей
 */
@ConfigurationProperties(prefix = "emulator.marketdata.replay")
public record ReplayProperties(Instant start, double speed, long seed) {

  public ReplayProperties {
    speed = speed > 0 ? speed : 1.0;
  }

  /** Настройки по умолчанию — для тестов. */
  public static ReplayProperties defaults() {
    return new ReplayProperties(null, 1.0, 42);
  }
}
