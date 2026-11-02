package ru.moex.papertrader.infra.services;

import java.time.Instant;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import ru.moex.papertrader.core.marketdata.Clock;
import ru.moex.papertrader.testutil.FixedClock;

/**
 * Детерминированное время интеграционных тестов: фиксированный {@link Clock} вместо системного.
 *
 * <p>Конфигурация подключается через {@code @Import} и не попадает в сканирование компонентов.
 */
@TestConfiguration
public class FixedClockTestConfiguration {

  /** Точность до микросекунд: PostgreSQL не хранит наносекунды. */
  public static final Instant NOW = Instant.parse("2026-11-02T10:15:30.123456Z");

  @Bean
  @Primary
  public Clock fixedClock() {
    return FixedClock.at(NOW);
  }
}
