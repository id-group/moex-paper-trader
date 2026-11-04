package ru.moex.papertrader.core.execution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.Random;
import org.junit.jupiter.api.Test;
import ru.moex.papertrader.core.domain.Side;

class SpecSlippageModelTest {

  private static final BigDecimal PRICE = new BigDecimal("100");
  private static final BigDecimal ADV = new BigDecimal("10000");

  @Test
  void baseComponentStaysWithinSpecifiedRange() {
    SpecSlippageModel model =
        new SpecSlippageModel(
            new Random(7), SpecSlippageModel.BASE_MIN, SpecSlippageModel.BASE_MAX);

    for (int i = 0; i < 1_000; i++) {
      BigDecimal rate = model.rate(Side.BUY, PRICE, 1, null);
      assertThat(rate).isBetween(SpecSlippageModel.BASE_MIN, SpecSlippageModel.BASE_MAX);
    }
  }

  @Test
  void appliesLowTierBelowOnePercentOfAdv() {
    SpecSlippageModel model =
        new SpecSlippageModel(
            new Random(11), SpecSlippageModel.BASE_MIN, SpecSlippageModel.BASE_MAX);

    BigDecimal rate = model.rate(Side.BUY, PRICE, 99, ADV);

    assertThat(rate).isBetween(new BigDecimal("0.0010"), new BigDecimal("0.0020"));
  }

  @Test
  void appliesMidTierFromOneToFivePercentOfAdv() {
    SpecSlippageModel model =
        new SpecSlippageModel(
            new Random(11), SpecSlippageModel.BASE_MIN, SpecSlippageModel.BASE_MAX);

    assertThat(model.rate(Side.BUY, PRICE, 100, ADV))
        .isBetween(new BigDecimal("0.0015"), new BigDecimal("0.0025"));
    assertThat(model.rate(Side.BUY, PRICE, 500, ADV))
        .isBetween(new BigDecimal("0.0015"), new BigDecimal("0.0025"));
  }

  @Test
  void appliesHighTierAboveFivePercentOfAdv() {
    SpecSlippageModel model =
        new SpecSlippageModel(
            new Random(11), SpecSlippageModel.BASE_MIN, SpecSlippageModel.BASE_MAX);

    BigDecimal rate = model.rate(Side.BUY, PRICE, 501, ADV);

    assertThat(rate).isBetween(new BigDecimal("0.0025"), new BigDecimal("0.0035"));
  }

  @Test
  void unknownAdvAppliesOnlyBaseComponent() {
    SpecSlippageModel model =
        new SpecSlippageModel(
            new Random(11), SpecSlippageModel.BASE_MIN, SpecSlippageModel.BASE_MAX);

    assertThat(model.rate(Side.BUY, PRICE, 1_000, BigDecimal.ZERO))
        .isBetween(SpecSlippageModel.BASE_MIN, SpecSlippageModel.BASE_MAX);
    assertThat(model.rate(Side.SELL, PRICE, 1_000, null))
        .isBetween(SpecSlippageModel.BASE_MIN, SpecSlippageModel.BASE_MAX);
  }

  @Test
  void sameSeedProducesSameSequence() {
    SpecSlippageModel first = new SpecSlippageModel(42);
    SpecSlippageModel second = new SpecSlippageModel(42);

    for (int i = 0; i < 100; i++) {
      assertThat(first.rate(Side.BUY, PRICE, 10, ADV))
          .isEqualTo(second.rate(Side.BUY, PRICE, 10, ADV));
    }
  }

  @Test
  void rejectsInvalidBaseRange() {
    assertThatThrownBy(
            () ->
                new SpecSlippageModel(
                    new Random(1), new BigDecimal("0.002"), new BigDecimal("0.001")))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () -> new SpecSlippageModel(new Random(1), new BigDecimal("-0.001"), BigDecimal.ONE))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void rejectsNonPositiveQuantity() {
    SpecSlippageModel model = new SpecSlippageModel(1);

    assertThatThrownBy(() -> model.rate(Side.BUY, PRICE, 0, ADV))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
