package ru.moex.papertrader.config;

import java.security.SecureRandom;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import ru.moex.papertrader.core.execution.AdvLiquidityPolicy;
import ru.moex.papertrader.core.execution.LiquidityPolicy;
import ru.moex.papertrader.core.execution.MatchingEngine;
import ru.moex.papertrader.core.execution.SlippageModel;
import ru.moex.papertrader.core.execution.SpecSlippageModel;
import ru.moex.papertrader.infra.services.ExecutionProperties;
import ru.moex.papertrader.infra.services.ReplayProperties;

/**
 * Собирает модель исполнения из настроек {@code emulator.execution.*}.
 *
 * <p>Seed проскальзывания: REPLAY берёт {@code emulator.marketdata.replay.seed} (одинаковый прогон
 * даёт одинаковые цены), STUB — фиксированный seed конфигурации, LIVE/RECORD — системный случайный
 * seed, потому что детерминизм там не требуется.
 */
@Configuration
@EnableConfigurationProperties(ExecutionProperties.class)
public class ExecutionConfiguration {

  @Bean
  public SlippageModel slippageModel(
      ExecutionProperties properties,
      ReplayProperties replay,
      @Value("${emulator.marketdata.mode:STUB}") String mode) {
    ExecutionProperties.Slippage slippage = properties.slippage();
    return new SpecSlippageModel(
        seed(mode, slippage.seed(), replay.seed()), slippage.baseMin(), slippage.baseMax());
  }

  @Bean
  public LiquidityPolicy liquidityPolicy(ExecutionProperties properties) {
    return new AdvLiquidityPolicy(
        properties.liquidityPolicy(), properties.liquidityThresholdPercent());
  }

  @Bean
  public MatchingEngine matchingEngine(
      SlippageModel slippageModel, LiquidityPolicy liquidityPolicy) {
    return new MatchingEngine(slippageModel, liquidityPolicy);
  }

  private static long seed(String mode, long configuredSeed, long replaySeed) {
    return switch (mode.trim().toUpperCase()) {
      case "REPLAY" -> replaySeed;
      case "STUB" -> configuredSeed;
      default -> new SecureRandom().nextLong();
    };
  }
}
