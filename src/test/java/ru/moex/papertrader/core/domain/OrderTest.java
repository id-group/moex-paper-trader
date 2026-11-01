package ru.moex.papertrader.core.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import ru.moex.papertrader.TestInstruments;

class OrderTest {

  private static final Instant NOW = Instant.parse("2026-11-01T10:00:00Z");

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

  private static NewOrder newOrder(OrderType type, BigDecimal price, long quantity) {
    return new NewOrder(
        UUID.randomUUID(), "SBER", "TQBR", Side.BUY, type, TimeInForce.DAY, quantity, price);
  }
}
