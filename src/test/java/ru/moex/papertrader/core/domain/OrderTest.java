package ru.moex.papertrader.core.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import ru.moex.papertrader.TestInstruments;

class OrderTest {

  private static final Instant NOW = Instant.parse("2026-11-01T10:00:00Z");
  private static final Instant UPDATED = Instant.parse("2026-11-01T10:00:30Z");
  private static final Instant FILL_TIME = Instant.parse("2026-11-01T10:01:00Z");
  private static final UUID CLIENT_ORDER_ID =
      UUID.fromString("11111111-1111-1111-1111-111111111111");
  private static final String ORDER_ID = "ORD-1";
  private static final String CLIENT_CODE = "PAPER-0001";

  @Test
  void acceptsLimitOrderInNewStatus() {
    Order order =
        Order.accept(
            newOrder(OrderType.LIMIT, new BigDecimal("285.50"), 10),
            TestInstruments.sber(),
            "20261101-TQBR-1",
            1,
            "PAPER-0001",
            NOW);

    assertThat(order.status()).isEqualTo(OrderStatus.NEW);
    assertThat(order.side()).isEqualTo(Side.BUY);
    assertThat(order.instrument().ticker()).isEqualTo("SBER");
    assertThat(order.price()).isEqualByComparingTo("285.50");
    assertThat(order.quantity()).isEqualTo(10);
    assertThat(order.executedQuantity()).isZero();
    assertThat(order.createdAt()).isEqualTo(NOW);
    assertThat(order.updatedAt()).isEqualTo(NOW);
  }

  @Test
  void marketOrderIgnoresPrice() {
    Order order =
        Order.accept(
            newOrder(OrderType.MARKET, new BigDecimal("999"), 1),
            TestInstruments.sber(),
            "id",
            1,
            "PAPER-0001",
            NOW);

    assertThat(order.price()).isNull();
  }

  @Test
  void rejectsNonPositiveQuantity() {
    assertThatThrownBy(
            () ->
                Order.accept(
                    newOrder(OrderType.LIMIT, BigDecimal.ONE, 0),
                    TestInstruments.sber(),
                    "id",
                    1,
                    "PAPER-0001",
                    NOW))
        .isInstanceOf(InvalidOrderException.class)
        .hasMessageContaining("orderQuantity");
  }

  @Test
  void rejectsUnsupportedOrderType() {
    assertThatThrownBy(
            () ->
                Order.accept(
                    newOrder(OrderType.ICEBERG, BigDecimal.ONE, 1),
                    TestInstruments.sber(),
                    "id",
                    1,
                    "PAPER-0001",
                    NOW))
        .isInstanceOf(InvalidOrderException.class)
        .hasMessageContaining("orderType");
  }

  @Test
  void rejectsGoodTillDate() {
    NewOrder request =
        new NewOrder(
            UUID.randomUUID(),
            "SBER",
            "TQBR",
            Side.BUY,
            OrderType.LIMIT,
            TimeInForce.GOOD_TILL_DATE,
            1,
            BigDecimal.ONE);
    assertThatThrownBy(
            () -> Order.accept(request, TestInstruments.sber(), "id", 1, "PAPER-0001", NOW))
        .isInstanceOf(InvalidOrderException.class)
        .hasMessageContaining("timeInForce");
  }

  @Test
  void limitOrderRequiresPositivePrice() {
    assertThatThrownBy(
            () ->
                Order.accept(
                    newOrder(OrderType.LIMIT, null, 1),
                    TestInstruments.sber(),
                    "id",
                    1,
                    "PAPER-0001",
                    NOW))
        .isInstanceOf(InvalidOrderException.class)
        .hasMessageContaining("price");
  }

  @Test
  void cancelsActiveOrderAndForbidsSecondCancellation() {
    Order order =
        Order.accept(
            newOrder(OrderType.LIMIT, BigDecimal.TEN, 1),
            TestInstruments.sber(),
            "id",
            1,
            "PAPER-0001",
            NOW);
    Instant cancelledAt = NOW.plusSeconds(60);

    order.cancel(cancelledAt);

    assertThat(order.status()).isEqualTo(OrderStatus.CANCELED);
    assertThat(order.updatedAt()).isEqualTo(cancelledAt);
    assertThat(order.status().isActive()).isFalse();
    assertThatThrownBy(() -> order.cancel(cancelledAt)).isInstanceOf(InvalidOrderException.class);
  }

  @Test
  void orderStatusCodesFollowContract() {
    assertThat(OrderStatus.NEW.code()).isEqualTo("0");
    assertThat(OrderStatus.PARTIALLY_FILLED.code()).isEqualTo("1");
    assertThat(OrderStatus.FILLED.code()).isEqualTo("2");
    assertThat(OrderStatus.CANCELED.code()).isEqualTo("4");
    assertThat(OrderStatus.REJECTED.code()).isEqualTo("8");
    assertThat(OrderStatus.PENDING_NEW.code()).isEqualTo("10");
    assertThat(OrderStatus.fromCode("4")).contains(OrderStatus.CANCELED);
    assertThat(OrderStatus.fromCode("99")).isEmpty();
  }

  @ParameterizedTest
  @MethodSource("restorableStatuses")
  void restoresOrderWithEveryStatus(
      OrderStatus status, long executedQuantity, BigDecimal averagePrice) {
    Order order = restore(10, new BigDecimal("100.500000"), status, executedQuantity, averagePrice);

    assertThat(order.orderNum()).isEqualTo(7);
    assertThat(order.clientOrderId()).isEqualTo(CLIENT_ORDER_ID);
    assertThat(order.orderId()).isEqualTo(ORDER_ID);
    assertThat(order.instrument()).isEqualTo(TestInstruments.sber());
    assertThat(order.side()).isEqualTo(Side.BUY);
    assertThat(order.orderType()).isEqualTo(OrderType.LIMIT);
    assertThat(order.timeInForce()).isEqualTo(TimeInForce.DAY);
    assertThat(order.quantity()).isEqualTo(10);
    assertThat(order.price()).isEqualByComparingTo("100.500000");
    assertThat(order.clientCode()).isEqualTo(CLIENT_CODE);
    assertThat(order.createdAt()).isEqualTo(NOW);
    assertThat(order.updatedAt()).isEqualTo(UPDATED);
    assertThat(order.status()).isEqualTo(status);
    assertThat(order.executedQuantity()).isEqualTo(executedQuantity);
    if (averagePrice == null) {
      assertThat(order.averagePrice()).isNull();
    } else {
      assertThat(order.averagePrice()).isEqualByComparingTo(averagePrice);
    }
  }

  static Stream<Arguments> restorableStatuses() {
    BigDecimal partialAverage = new BigDecimal("101.250000");
    BigDecimal fullAverage = new BigDecimal("102.500000");
    return Stream.of(
        Arguments.of(OrderStatus.NEW, 0L, null),
        Arguments.of(OrderStatus.PENDING_NEW, 0L, null),
        Arguments.of(OrderStatus.CANCELING, 0L, null),
        Arguments.of(OrderStatus.REPLACING, 0L, null),
        Arguments.of(OrderStatus.REPLACED, 0L, null),
        Arguments.of(OrderStatus.REJECTED, 0L, null),
        Arguments.of(OrderStatus.CANCELED, 0L, null),
        Arguments.of(OrderStatus.PARTIALLY_FILLED, 4L, partialAverage),
        Arguments.of(OrderStatus.FILLED, 10L, fullAverage),
        Arguments.of(OrderStatus.CANCELED, 4L, partialAverage));
  }

  @Test
  void restoresMarketOrderWithoutPrice() {
    Order order =
        Order.restore(
            7,
            CLIENT_ORDER_ID,
            ORDER_ID,
            TestInstruments.sber(),
            Side.SELL,
            OrderType.MARKET,
            TimeInForce.DAY,
            5,
            null,
            CLIENT_CODE,
            NOW,
            UPDATED,
            OrderStatus.NEW,
            0,
            null);

    assertThat(order.price()).isNull();
    assertThat(order.orderType()).isEqualTo(OrderType.MARKET);
    assertThat(order.side()).isEqualTo(Side.SELL);
  }

  @Test
  void restoreRejectsNonPositiveQuantity() {
    assertThatThrownBy(() -> restore(0, new BigDecimal("100.500000"), OrderStatus.NEW, 0, null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("quantity");
  }

  @Test
  void restoreRejectsLimitOrderWithoutPrice() {
    assertThatThrownBy(() -> restore(10, null, OrderStatus.NEW, 0, null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("price");
  }

  @Test
  void restoreRejectsLimitOrderWithNonPositivePrice() {
    assertThatThrownBy(() -> restore(10, BigDecimal.ZERO, OrderStatus.NEW, 0, null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("price");
  }

  @Test
  void restoreRejectsPriceOnMarketOrder() {
    assertThatThrownBy(
            () ->
                Order.restore(
                    7,
                    CLIENT_ORDER_ID,
                    ORDER_ID,
                    TestInstruments.sber(),
                    Side.BUY,
                    OrderType.MARKET,
                    TimeInForce.DAY,
                    10,
                    new BigDecimal("100.5"),
                    CLIENT_CODE,
                    NOW,
                    UPDATED,
                    OrderStatus.NEW,
                    0,
                    null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("price");
  }

  @Test
  void restoreRejectsNegativeExecutedQuantity() {
    assertThatThrownBy(() -> restore(10, new BigDecimal("100.5"), OrderStatus.NEW, -1, null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("executedQuantity");
  }

  @Test
  void restoreRejectsExecutedQuantityAboveQuantity() {
    assertThatThrownBy(
            () ->
                restore(
                    10,
                    new BigDecimal("100.5"),
                    OrderStatus.PARTIALLY_FILLED,
                    11,
                    new BigDecimal("100.500000")))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("executedQuantity");
  }

  @Test
  void restoreRejectsAveragePriceWithoutExecution() {
    assertThatThrownBy(
            () ->
                restore(
                    10, new BigDecimal("100.5"), OrderStatus.NEW, 0, new BigDecimal("100.500000")))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("averagePrice");
  }

  @Test
  void restoreRejectsExecutionWithoutAveragePrice() {
    assertThatThrownBy(
            () -> restore(10, new BigDecimal("100.5"), OrderStatus.PARTIALLY_FILLED, 4, null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("averagePrice");
  }

  @Test
  void restoreRejectsFilledStatusWhenNotFullyExecuted() {
    assertThatThrownBy(
            () ->
                restore(
                    10,
                    new BigDecimal("100.5"),
                    OrderStatus.FILLED,
                    9,
                    new BigDecimal("100.500000")))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("FILLED");
  }

  @Test
  void restoreRejectsFullyExecutedOrderThatIsNotFilled() {
    assertThatThrownBy(
            () ->
                restore(
                    10,
                    new BigDecimal("100.5"),
                    OrderStatus.CANCELED,
                    10,
                    new BigDecimal("100.500000")))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("FILLED");
  }

  @Test
  void restoreRejectsNewStatusWithExecution() {
    assertThatThrownBy(
            () ->
                restore(
                    10, new BigDecimal("100.5"), OrderStatus.NEW, 5, new BigDecimal("100.500000")))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("NEW");
  }

  @Test
  void restoreRejectsPartiallyFilledStatusWithoutExecution() {
    assertThatThrownBy(
            () -> restore(10, new BigDecimal("100.5"), OrderStatus.PARTIALLY_FILLED, 0, null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("PARTIALLY_FILLED");
  }

  @Test
  void restoreRejectsPartiallyFilledStatusWhenFullyExecuted() {
    assertThatThrownBy(
            () ->
                restore(
                    10,
                    new BigDecimal("100.5"),
                    OrderStatus.PARTIALLY_FILLED,
                    10,
                    new BigDecimal("100.500000")))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("PARTIALLY_FILLED");
  }

  @Test
  void partialFillMarksOrderPartiallyFilled() {
    Order order = accepted(10, new BigDecimal("285.50"));

    order.applyFill(new Fill(new BigDecimal("286.25"), 4, BigDecimal.ZERO, FILL_TIME));

    assertThat(order.status()).isEqualTo(OrderStatus.PARTIALLY_FILLED);
    assertThat(order.executedQuantity()).isEqualTo(4);
    assertThat(order.averagePrice()).isEqualByComparingTo("286.25");
    assertThat(order.averagePrice().scale()).isEqualTo(6);
    assertThat(order.updatedAt()).isEqualTo(FILL_TIME);
  }

  @Test
  void fullFillMarksOrderFilled() {
    Order order = accepted(10, new BigDecimal("285.50"));

    order.applyFill(new Fill(new BigDecimal("285.50"), 10, BigDecimal.ZERO, FILL_TIME));

    assertThat(order.status()).isEqualTo(OrderStatus.FILLED);
    assertThat(order.executedQuantity()).isEqualTo(10);
    assertThat(order.averagePrice()).isEqualByComparingTo("285.50");
    assertThat(order.averagePrice().scale()).isEqualTo(6);
    assertThat(order.updatedAt()).isEqualTo(FILL_TIME);
  }

  @Test
  void twoFillsProduceVolumeWeightedAveragePrice() {
    Order order = accepted(10, new BigDecimal("285.50"));
    order.applyFill(new Fill(new BigDecimal("100.500000"), 4, BigDecimal.ZERO, FILL_TIME));
    Instant secondFillTime = FILL_TIME.plusSeconds(60);

    order.applyFill(new Fill(new BigDecimal("102.500000"), 6, BigDecimal.ZERO, secondFillTime));

    assertThat(order.status()).isEqualTo(OrderStatus.FILLED);
    assertThat(order.executedQuantity()).isEqualTo(10);
    assertThat(order.averagePrice()).isEqualTo(new BigDecimal("101.700000"));
    assertThat(order.updatedAt()).isEqualTo(secondFillTime);
  }

  @Test
  void averagePriceRoundsHalfUpToSixDigits() {
    Order order = accepted(3, new BigDecimal("100"));
    order.applyFill(new Fill(new BigDecimal("100.000000"), 1, BigDecimal.ZERO, FILL_TIME));

    order.applyFill(new Fill(new BigDecimal("100.000001"), 2, BigDecimal.ZERO, FILL_TIME));

    // (1 × 100.000000 + 2 × 100.000001) / 3 = 100.000000666… → 100.000001 (HALF_UP, масштаб 6).
    assertThat(order.averagePrice()).isEqualTo(new BigDecimal("100.000001"));
  }

  @Test
  void rejectsFillAboveRemainingQuantity() {
    Order order = accepted(10, new BigDecimal("285.50"));
    order.applyFill(new Fill(new BigDecimal("285.50"), 4, BigDecimal.ZERO, FILL_TIME));

    assertThatThrownBy(
            () ->
                order.applyFill(new Fill(new BigDecimal("285.50"), 7, BigDecimal.ZERO, FILL_TIME)))
        .isInstanceOf(InvalidOrderException.class)
        .hasMessageContaining("остат");
    assertThat(order.status()).isEqualTo(OrderStatus.PARTIALLY_FILLED);
    assertThat(order.executedQuantity()).isEqualTo(4);
  }

  @Test
  void rejectsFillForFilledOrder() {
    Order order = accepted(1, new BigDecimal("285.50"));
    order.applyFill(new Fill(new BigDecimal("285.50"), 1, BigDecimal.ZERO, FILL_TIME));

    assertThatThrownBy(
            () ->
                order.applyFill(new Fill(new BigDecimal("285.50"), 1, BigDecimal.ZERO, FILL_TIME)))
        .isInstanceOf(InvalidOrderException.class)
        .hasMessageContaining("FILLED");
  }

  @Test
  void rejectsFillForCanceledOrder() {
    Order order = accepted(10, new BigDecimal("285.50"));
    order.cancel(NOW.plusSeconds(30));

    assertThatThrownBy(
            () ->
                order.applyFill(new Fill(new BigDecimal("285.50"), 1, BigDecimal.ZERO, FILL_TIME)))
        .isInstanceOf(InvalidOrderException.class)
        .hasMessageContaining("CANCELED");
  }

  @Test
  void rejectsZeroFillQuantity() {
    Order order = accepted(10, new BigDecimal("285.50"));
    Fill fill = mock(Fill.class);
    when(fill.quantity()).thenReturn(0L);

    assertThatThrownBy(() -> order.applyFill(fill))
        .isInstanceOf(InvalidOrderException.class)
        .hasMessageContaining("Объём сделки");
  }

  @Test
  void rejectsNegativeFillQuantity() {
    Order order = accepted(10, new BigDecimal("285.50"));
    Fill fill = mock(Fill.class);
    when(fill.quantity()).thenReturn(-1L);

    assertThatThrownBy(() -> order.applyFill(fill))
        .isInstanceOf(InvalidOrderException.class)
        .hasMessageContaining("Объём сделки");
  }

  @Test
  void cancelAfterPartialFillKeepsExecutionDetails() {
    Order order = accepted(10, new BigDecimal("285.50"));
    order.applyFill(new Fill(new BigDecimal("286.25"), 4, BigDecimal.ZERO, FILL_TIME));
    Instant cancelledAt = FILL_TIME.plusSeconds(30);

    order.cancel(cancelledAt);

    assertThat(order.status()).isEqualTo(OrderStatus.CANCELED);
    assertThat(order.executedQuantity()).isEqualTo(4);
    assertThat(order.averagePrice()).isEqualByComparingTo("286.25");
    assertThat(order.updatedAt()).isEqualTo(cancelledAt);
  }

  private static Order accepted(long quantity, BigDecimal price) {
    return Order.accept(
        newOrder(OrderType.LIMIT, price, quantity),
        TestInstruments.sber(),
        ORDER_ID,
        7,
        CLIENT_CODE,
        NOW);
  }

  private static Order restore(
      long quantity,
      BigDecimal price,
      OrderStatus status,
      long executedQuantity,
      BigDecimal averagePrice) {
    return Order.restore(
        7,
        CLIENT_ORDER_ID,
        ORDER_ID,
        TestInstruments.sber(),
        Side.BUY,
        OrderType.LIMIT,
        TimeInForce.DAY,
        quantity,
        price,
        CLIENT_CODE,
        NOW,
        UPDATED,
        status,
        executedQuantity,
        averagePrice);
  }

  private static NewOrder newOrder(OrderType type, BigDecimal price, long quantity) {
    return new NewOrder(
        UUID.randomUUID(), "SBER", "TQBR", Side.BUY, type, TimeInForce.DAY, quantity, price);
  }
}
