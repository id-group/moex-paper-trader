package ru.moex.papertrader.core.execution;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import ru.moex.papertrader.TestInstruments;
import ru.moex.papertrader.core.domain.Fill;
import ru.moex.papertrader.core.domain.NewOrder;
import ru.moex.papertrader.core.domain.Order;
import ru.moex.papertrader.core.domain.OrderType;
import ru.moex.papertrader.core.domain.Side;
import ru.moex.papertrader.core.domain.TimeInForce;
import ru.moex.papertrader.core.marketdata.OrderBookSnapshot;

class MatchingEngineTest {

  private static final Instant NOW = Instant.parse("2026-11-05T10:00:00Z");
  private static final BigDecimal ADV = new BigDecimal("10000");
  private static final SlippageModel FIXED_SLIPPAGE =
      (side, price, quantity, adv) -> new BigDecimal("0.001");

  private final MatchingEngine engine =
      new MatchingEngine(FIXED_SLIPPAGE, AdvLiquidityPolicy.rejectAtDefaultThreshold());

  @Test
  void marketBuyCrossesAskWithAdverseSlippage() {
    Order order = order(Side.BUY, OrderType.MARKET, null, 10, TimeInForce.DAY);

    MatchResult result = engine.match(order, book(List.of(), List.of(level("100.00", 10))), ADV);

    assertThat(result.fills()).hasSize(1);
    assertThat(result.fills().get(0).price()).isEqualByComparingTo("100.10");
    assertThat(result.fills().get(0).quantity()).isEqualTo(10);
    assertThat(result.remainderAction()).isEqualTo(MatchResult.RemainderAction.KEEP);
  }

  @Test
  void marketSellCrossesBidWithAdverseSlippage() {
    Order order = order(Side.SELL, OrderType.MARKET, null, 10, TimeInForce.DAY);

    MatchResult result = engine.match(order, book(List.of(level("99.00", 10)), List.of()), ADV);

    assertThat(result.fills()).hasSize(1);
    assertThat(result.fills().get(0).price()).isEqualByComparingTo("98.901");
  }

  @Test
  void marketBuyWalksSeveralLevels() {
    Order order = order(Side.BUY, OrderType.MARKET, null, 50, TimeInForce.DAY);

    MatchResult result =
        engine.match(
            order, book(List.of(), List.of(level("100.00", 30), level("101.00", 100))), ADV);

    assertThat(result.fills()).extracting(MatchFill::quantity).containsExactly(30L, 20L);
    assertThat(result.fills().get(0).price()).isEqualByComparingTo("100.10");
    assertThat(result.fills().get(1).price()).isEqualByComparingTo("101.101");
  }

  @Test
  void marketPartialFillCancelsRemainder() {
    Order order = order(Side.BUY, OrderType.MARKET, null, 50, TimeInForce.DAY);

    MatchResult result = engine.match(order, book(List.of(), List.of(level("100.00", 30))), ADV);

    assertThat(result.quantity()).isEqualTo(30);
    assertThat(result.remainderAction()).isEqualTo(MatchResult.RemainderAction.CANCEL);
  }

  @Test
  void marketWithoutCounterpartyIsRejected() {
    Order order = order(Side.BUY, OrderType.MARKET, null, 50, TimeInForce.DAY);

    MatchResult result = engine.match(order, book(List.of(), List.of()), ADV);

    assertThat(result.rejected()).isTrue();
    assertThat(result.rejectionReason()).get().asString().contains("Нет встречных");
  }

  @Test
  void limitBuyDoesNotCrossWhenAskIsAboveLimit() {
    Order order = order(Side.BUY, OrderType.LIMIT, "99.00", 10, TimeInForce.DAY);

    MatchResult result = engine.match(order, book(List.of(), List.of(level("100.00", 10))), ADV);

    assertThat(result.fills()).isEmpty();
    assertThat(result.remainderAction()).isEqualTo(MatchResult.RemainderAction.KEEP);
  }

  @Test
  void limitBuyExecutesAtAskWhenCrossed() {
    Order order = order(Side.BUY, OrderType.LIMIT, "105.00", 10, TimeInForce.DAY);

    MatchResult result = engine.match(order, book(List.of(), List.of(level("100.00", 10))), ADV);

    assertThat(result.fills()).hasSize(1);
    assertThat(result.fills().get(0).price()).isEqualByComparingTo("100.00");
  }

  @Test
  void limitSellExecutesAtBidWhenCrossed() {
    Order order = order(Side.SELL, OrderType.LIMIT, "95.00", 10, TimeInForce.DAY);

    MatchResult result = engine.match(order, book(List.of(level("99.00", 10)), List.of()), ADV);

    assertThat(result.fills()).hasSize(1);
    assertThat(result.fills().get(0).price()).isEqualByComparingTo("99.00");
  }

  @Test
  void limitPartialFillKeepsRemainderActive() {
    Order order = order(Side.BUY, OrderType.LIMIT, "100.00", 50, TimeInForce.DAY);

    MatchResult result = engine.match(order, book(List.of(), List.of(level("100.00", 30))), ADV);

    assertThat(result.quantity()).isEqualTo(30);
    assertThat(result.remainderAction()).isEqualTo(MatchResult.RemainderAction.KEEP);
  }

  @Test
  void fokRejectsWhenVolumeIsInsufficient() {
    Order order = order(Side.BUY, OrderType.LIMIT, "100.00", 50, TimeInForce.FILL_OR_KILL);

    MatchResult result = engine.match(order, book(List.of(), List.of(level("100.00", 30))), ADV);

    assertThat(result.rejected()).isTrue();
    assertThat(result.fills()).isEmpty();
  }

  @Test
  void fokFillsWhenFullVolumeIsAvailable() {
    Order order = order(Side.BUY, OrderType.LIMIT, "100.00", 50, TimeInForce.FILL_OR_KILL);

    MatchResult result = engine.match(order, book(List.of(), List.of(level("100.00", 50))), ADV);

    assertThat(result.rejected()).isFalse();
    assertThat(result.quantity()).isEqualTo(50);
  }

  @Test
  void rejectsOrderAboveFivePercentOfAdv() {
    Order order = order(Side.BUY, OrderType.LIMIT, "200.00", 60, TimeInForce.DAY);

    MatchResult result =
        engine.match(order, book(List.of(), List.of(level("100.00", 100))), new BigDecimal("1000"));

    assertThat(result.rejected()).isTrue();
    assertThat(result.rejectionReason()).get().asString().contains("5%");
  }

  @Test
  void splitPolicyProducesSeriesOfParts() {
    MatchingEngine splitEngine =
        new MatchingEngine(
            FIXED_SLIPPAGE,
            new AdvLiquidityPolicy(
                AdvLiquidityPolicy.Mode.SPLIT, AdvLiquidityPolicy.DEFAULT_THRESHOLD_PERCENT));
    Order order = order(Side.BUY, OrderType.MARKET, null, 60, TimeInForce.DAY);

    MatchResult result =
        splitEngine.match(
            order, book(List.of(), List.of(level("100.00", 100))), new BigDecimal("1000"));

    assertThat(result.fills()).extracting(MatchFill::quantity).containsExactly(50L, 10L);
    assertThat(result.remainderAction()).isEqualTo(MatchResult.RemainderAction.KEEP);
  }

  @Test
  void slippageTierUsesWholeOrderSizeNotBookChunk() {
    List<Long> ratedQuantities = new ArrayList<>();
    SlippageModel recording =
        (side, price, quantity, adv) -> {
          ratedQuantities.add(quantity);
          return BigDecimal.ZERO;
        };
    MatchingEngine splitEngine =
        new MatchingEngine(
            recording,
            new AdvLiquidityPolicy(
                AdvLiquidityPolicy.Mode.SPLIT, AdvLiquidityPolicy.DEFAULT_THRESHOLD_PERCENT));
    Order order = order(Side.BUY, OrderType.MARKET, null, 600, TimeInForce.DAY);

    MatchResult result =
        splitEngine.match(
            order,
            book(List.of(), List.of(level("100.00", 300), level("101.00", 400))),
            new BigDecimal("10000"));

    assertThat(result.fills()).extracting(MatchFill::quantity).containsExactly(300L, 300L);
    assertThat(ratedQuantities).containsOnly(600L);
  }

  @Test
  void splitPolicyExecutesOnePartPerPassForLimitOrder() {
    MatchingEngine splitEngine =
        new MatchingEngine(
            FIXED_SLIPPAGE,
            new AdvLiquidityPolicy(
                AdvLiquidityPolicy.Mode.SPLIT, AdvLiquidityPolicy.DEFAULT_THRESHOLD_PERCENT));
    Order order = order(Side.BUY, OrderType.LIMIT, "200.00", 60, TimeInForce.DAY);

    MatchResult first =
        splitEngine.match(
            order, book(List.of(), List.of(level("100.00", 100))), new BigDecimal("1000"));
    assertThat(first.fills()).extracting(MatchFill::quantity).containsExactly(50L);
    assertThat(first.remainderAction()).isEqualTo(MatchResult.RemainderAction.KEEP);

    MatchFill fill = first.fills().get(0);
    order.applyFill(new Fill(fill.price(), fill.quantity(), BigDecimal.ZERO, NOW));
    MatchResult second =
        splitEngine.match(
            order, book(List.of(), List.of(level("100.00", 100))), new BigDecimal("1000"));
    assertThat(second.fills()).extracting(MatchFill::quantity).containsExactly(10L);
  }

  @Test
  void ignoresBookLevelsWithNonPositiveVolume() {
    Order order = order(Side.BUY, OrderType.LIMIT, "105.00", 10, TimeInForce.DAY);

    MatchResult result =
        engine.match(order, book(List.of(), List.of(level("100.00", 0), level("101.00", 10))), ADV);

    assertThat(result.fills())
        .extracting(MatchFill::price)
        .containsExactly(new BigDecimal("101.000000"));
  }

  @Test
  void replayWithSameSeedProducesIdenticalFills() {
    MatchingEngine firstReplay =
        new MatchingEngine(
            new SpecSlippageModel(42), AdvLiquidityPolicy.rejectAtDefaultThreshold());
    MatchingEngine secondReplay =
        new MatchingEngine(
            new SpecSlippageModel(42), AdvLiquidityPolicy.rejectAtDefaultThreshold());
    OrderBookSnapshot book = book(List.of(), List.of(level("100.00", 30), level("101.00", 100)));

    MatchResult first =
        firstReplay.match(order(Side.BUY, OrderType.MARKET, null, 50, TimeInForce.DAY), book, ADV);
    MatchResult second =
        secondReplay.match(order(Side.BUY, OrderType.MARKET, null, 50, TimeInForce.DAY), book, ADV);

    assertThat(first.fills()).isEqualTo(second.fills());
  }

  @Test
  void fillsOnlyRemainingQuantityOfPartiallyExecutedOrder() {
    Order order = order(Side.BUY, OrderType.MARKET, null, 10, TimeInForce.DAY);
    order.applyFill(new Fill(new BigDecimal("100"), 4, BigDecimal.ZERO, NOW));

    MatchResult result = engine.match(order, book(List.of(), List.of(level("100.00", 100))), ADV);

    assertThat(result.quantity()).isEqualTo(6);
  }

  private static Order order(
      Side side, OrderType type, String price, long quantity, TimeInForce timeInForce) {
    NewOrder request =
        new NewOrder(
            UUID.randomUUID(),
            "SBER",
            "TQBR",
            side,
            type,
            timeInForce,
            quantity,
            price == null ? null : new BigDecimal(price));
    return Order.accept(request, TestInstruments.sber(), "ORD-1", 1, "PAPER-0001", NOW);
  }

  private static OrderBookSnapshot book(
      List<OrderBookSnapshot.Level> bids, List<OrderBookSnapshot.Level> asks) {
    return new OrderBookSnapshot("SBER", "TQBR", 5, NOW, bids, asks);
  }

  private static OrderBookSnapshot.Level level(String price, long quantity) {
    return new OrderBookSnapshot.Level(new BigDecimal(price), quantity);
  }
}
