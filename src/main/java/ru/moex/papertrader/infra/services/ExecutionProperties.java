package ru.moex.papertrader.infra.services;

import java.math.BigDecimal;
import org.springframework.boot.context.properties.ConfigurationProperties;
import ru.moex.papertrader.core.execution.AdvLiquidityPolicy;
import ru.moex.papertrader.core.execution.SpecSlippageModel;

/**
 * Настройки модели исполнения этапа 5: планировщик, глубина стакана, политика ликвидности и
 * параметры проскальзывания. Ключи yml — {@code emulator.execution.*}.
 */
@ConfigurationProperties(prefix = "emulator.execution")
public record ExecutionProperties(
    boolean schedulerEnabled,
    long tickMs,
    int orderBookDepth,
    AdvLiquidityPolicy.Mode liquidityPolicy,
    BigDecimal liquidityThresholdPercent,
    Slippage slippage) {

  /** Параметры модели проскальзывания; доли, не проценты. */
  public record Slippage(long seed, BigDecimal baseMin, BigDecimal baseMax) {}

  public ExecutionProperties {
    if (tickMs <= 0) {
      tickMs = 1000;
    }
    if (orderBookDepth <= 0) {
      orderBookDepth = 10;
    }
    if (liquidityPolicy == null) {
      liquidityPolicy = AdvLiquidityPolicy.Mode.REJECT;
    }
    if (liquidityThresholdPercent == null) {
      liquidityThresholdPercent = AdvLiquidityPolicy.DEFAULT_THRESHOLD_PERCENT;
    }
    if (slippage == null) {
      slippage = new Slippage(42L, SpecSlippageModel.BASE_MIN, SpecSlippageModel.BASE_MAX);
    }
  }
}
