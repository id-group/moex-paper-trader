package ru.moex.papertrader.core.execution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class AdvLiquidityPolicyTest {

  private static final BigDecimal ADV = new BigDecimal("1000");

  @Test
  void passesOrderAtExactlyFivePercentOfAdv() {
    AdvLiquidityPolicy policy =
        new AdvLiquidityPolicy(
            AdvLiquidityPolicy.Mode.REJECT, AdvLiquidityPolicy.DEFAULT_THRESHOLD_PERCENT);

    LiquidityDecision decision = policy.evaluate(50, ADV);

    assertThat(decision.action()).isEqualTo(LiquidityDecision.Action.PASS);
    assertThat(decision.maxPartQuantity()).isEqualTo(50);
  }

  @Test
  void rejectsSixPercentOfAdvByDefault() {
    LiquidityDecision decision = AdvLiquidityPolicy.rejectAtDefaultThreshold().evaluate(60, ADV);

    assertThat(decision.action()).isEqualTo(LiquidityDecision.Action.REJECT);
    assertThat(decision.reason()).contains("5%").contains("60");
  }

  @Test
  void splitsSixPercentOfAdvIntoParts() {
    AdvLiquidityPolicy policy =
        new AdvLiquidityPolicy(
            AdvLiquidityPolicy.Mode.SPLIT, AdvLiquidityPolicy.DEFAULT_THRESHOLD_PERCENT);

    LiquidityDecision decision = policy.evaluate(60, ADV);

    assertThat(decision.action()).isEqualTo(LiquidityDecision.Action.SPLIT);
    assertThat(decision.maxPartQuantity()).isEqualTo(50);
    assertThat(decision.reason()).contains("5%");
  }

  @Test
  void passesWhenAdvIsUnknown() {
    LiquidityDecision decision =
        AdvLiquidityPolicy.rejectAtDefaultThreshold().evaluate(1_000_000, null);
    assertThat(decision.action()).isEqualTo(LiquidityDecision.Action.PASS);

    decision = AdvLiquidityPolicy.rejectAtDefaultThreshold().evaluate(1_000_000, BigDecimal.ZERO);
    assertThat(decision.action()).isEqualTo(LiquidityDecision.Action.PASS);
  }

  @Test
  void floorsPartSizeAndKeepsItPositive() {
    AdvLiquidityPolicy split =
        new AdvLiquidityPolicy(
            AdvLiquidityPolicy.Mode.SPLIT, AdvLiquidityPolicy.DEFAULT_THRESHOLD_PERCENT);

    assertThat(split.evaluate(5, new BigDecimal("99")).maxPartQuantity()).isEqualTo(4);
    assertThat(split.evaluate(2, new BigDecimal("1")).maxPartQuantity()).isEqualTo(1);
  }

  @Test
  void validatesThreshold() {
    assertThatThrownBy(
            () -> new AdvLiquidityPolicy(AdvLiquidityPolicy.Mode.REJECT, BigDecimal.ZERO))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () -> new AdvLiquidityPolicy(AdvLiquidityPolicy.Mode.REJECT, new BigDecimal("101")))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void rejectsNonPositiveQuantity() {
    assertThatThrownBy(() -> AdvLiquidityPolicy.rejectAtDefaultThreshold().evaluate(0, ADV))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
