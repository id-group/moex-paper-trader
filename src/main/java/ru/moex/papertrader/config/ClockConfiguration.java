package ru.moex.papertrader.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Общие бины приложения (виртуальный Clock появится на этапе 4 вместе с режимами REPLAY). */
@Configuration
public class ClockConfiguration {

  @Bean
  public Clock clock() {
    return Clock.systemUTC();
  }
}
