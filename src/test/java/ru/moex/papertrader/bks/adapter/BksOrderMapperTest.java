package ru.moex.papertrader.bks.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import ru.moex.papertrader.TestInstruments;
import ru.moex.papertrader.bks.contract.CreateOrderRequest;
import ru.moex.papertrader.bks.contract.OrdersSearchRequest;
import ru.moex.papertrader.bks.contract.OrdersSearchResponse;
import ru.moex.papertrader.core.domain.NewOrder;
import ru.moex.papertrader.core.domain.Order;
import ru.moex.papertrader.core.domain.OrderIdType;
import ru.moex.papertrader.core.domain.OrderSearchCriteria;
import ru.moex.papertrader.core.domain.OrderStatus;
import ru.moex.papertrader.core.domain.OrderType;
import ru.moex.papertrader.core.domain.Side;
import ru.moex.papertrader.core.domain.TimeInForce;
import ru.moex.papertrader.infra.services.exception.InvalidRequestException;

class BksOrderMapperTest {

  private static final Instant NOW = Instant.parse("2026-11-01T10:00:00Z");

  private final BksOrderMapper mapper = new BksOrderMapper();

  @Test
  void mapsCreateRequestToNewOrder() {
    UUID uuid = UUID.randomUUID();
    CreateOrderRequest request =
        new CreateOrderRequest(
            uuid.toString(),
            "1",
            "2",
            "1",
            null,
            10L,
            "SBER",
            "TQBR",
            new BigDecimal("285.50"),
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null);

    NewOrder newOrder = mapper.toNewOrder(request);

    assertThat(newOrder.clientOrderId()).isEqualTo(uuid);
    assertThat(newOrder.side()).isEqualTo(Side.BUY);
    assertThat(newOrder.orderType()).isEqualTo(OrderType.LIMIT);
    assertThat(newOrder.timeInForce()).isEqualTo(TimeInForce.DAY);
    assertThat(newOrder.quantity()).isEqualTo(10);
    assertThat(newOrder.price()).isEqualByComparingTo("285.50");
  }

  @Test
  void defaultsMissingTimeInForceToDay() {
    NewOrder newOrder =
        mapper.toNewOrder(
            new CreateOrderRequest(
                UUID.randomUUID().toString(),
                "2",
                "1",
                null,
                null,
                1L,
                "GAZP",
                "TQBR",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null));

    assertThat(newOrder.timeInForce()).isEqualTo(TimeInForce.DAY);
    assertThat(newOrder.side()).isEqualTo(Side.SELL);
    assertThat(newOrder.orderType()).isEqualTo(OrderType.MARKET);
  }

  @Test
  void rejectsMalformedRequest() {
    CreateOrderRequest withoutQuantity =
        new CreateOrderRequest(
            UUID.randomUUID().toString(),
            "1",
            "2",
            null,
            null,
            null,
            "SBER",
            "TQBR",
            BigDecimal.ONE,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null);

    assertThatThrownBy(() -> mapper.toNewOrder(withoutQuantity))
        .isInstanceOf(InvalidRequestException.class);
    assertThatThrownBy(
            () ->
                mapper.toNewOrder(
                    new CreateOrderRequest(
                        "not-a-uuid",
                        "1",
                        "2",
                        null,
                        null,
                        1L,
                        "SBER",
                        "TQBR",
                        BigDecimal.ONE,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null)))
        .isInstanceOf(InvalidRequestException.class);
    assertThatThrownBy(
            () ->
                mapper.toNewOrder(
                    new CreateOrderRequest(
                        UUID.randomUUID().toString(),
                        "9",
                        "2",
                        null,
                        null,
                        1L,
                        "SBER",
                        "TQBR",
                        BigDecimal.ONE,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null)))
        .isInstanceOf(InvalidRequestException.class);
  }

  @Test
  void mapsActiveOrderToOrderResponse() {
    Order order = order(OrderType.LIMIT, Side.BUY, new BigDecimal("285.50"), 10);

    var response = mapper.toOrderResponse(order);

    assertThat(response.clientOrderId()).isEqualTo(order.clientOrderId().toString());
    assertThat(response.data()).hasSize(1);
    var data = response.data().get(0);
    assertThat(data.orderStatus()).isEqualTo("0");
    assertThat(data.executionType()).isEqualTo("0");
    assertThat(data.ticker()).isEqualTo("SBER");
    assertThat(data.classCode()).isEqualTo("TQBR");
    assertThat(data.side()).isEqualTo("1");
    assertThat(data.orderType()).isEqualTo("2");
    assertThat(data.orderQuantity()).isEqualByComparingTo("10");
    assertThat(data.remainedQuantity()).isEqualByComparingTo("10");
    assertThat(data.price()).isEqualByComparingTo("285.50");
    assertThat(data.currency()).isEqualTo("RUB");
    assertThat(data.timeInForce()).isEqualTo(1);
    assertThat(data.transactionTime()).isEqualTo(OffsetDateTime.ofInstant(NOW, ZoneOffset.UTC));
    assertThat(data.orderNumber()).isEqualTo(String.valueOf(order.orderNum()));
  }

  @Test
  void mapsCancelledOrderToSearchRecordWithCancelledStatus() {
    Order order = order(OrderType.LIMIT, Side.BUY, new BigDecimal("285.50"), 10);
    order.cancel(NOW.plusSeconds(5));

    OrdersSearchResponse record = mapper.toSearchResponse(order);

    assertThat(record.orderStatus()).isEqualTo(1);
    assertThat(record.orderType()).isEqualTo(2);
    assertThat(record.side()).isEqualTo(1);
    assertThat(record.orderQuantityLots()).isEqualByComparingTo("1");
    assertThat(record.remainedQuantity()).isEqualByComparingTo("10");
    assertThat(record.settlementCurrency()).isEqualTo("RUB");
  }

  @Test
  void mapsSearchRequestToCriteria() {
    OrdersSearchRequest request =
        new OrdersSearchRequest(
            OffsetDateTime.parse("2026-11-01T00:00:00Z"),
            OffsetDateTime.parse("2026-11-02T00:00:00Z"),
            1,
            List.of(1, 2, 3),
            List.of(1, 2),
            List.of(42L),
            List.of("SBER"),
            List.of("TQBR"));

    OrderSearchCriteria criteria = mapper.toCriteria(request);

    assertThat(criteria.from()).isEqualTo(Instant.parse("2026-11-01T00:00:00Z"));
    assertThat(criteria.to()).isEqualTo(Instant.parse("2026-11-02T00:00:00Z"));
    assertThat(criteria.side()).isEqualTo(Side.BUY);
    assertThat(criteria.statuses())
        .contains(OrderStatus.CANCELED, OrderStatus.FILLED, OrderStatus.NEW);
    assertThat(criteria.orderTypes()).containsExactlyInAnyOrder(OrderType.MARKET, OrderType.LIMIT);
    assertThat(criteria.orderNums()).containsExactly(42L);
    assertThat(criteria.tickers()).containsExactly("SBER");
  }

  @Test
  void mapsOnlySupportedOrderTypeFilters() {
    OrdersSearchRequest request =
        new OrdersSearchRequest(null, null, null, null, List.of(1, 3, 10), null, null, null);

    OrderSearchCriteria criteria = mapper.toCriteria(request);

    assertThat(criteria.orderTypes()).containsExactly(OrderType.MARKET);
  }

  @Test
  void parsesOrderIdTypeWithDefault() {
    assertThat(mapper.parseOrderIdType(null)).isEqualTo(OrderIdType.CLIENT);
    assertThat(mapper.parseOrderIdType("1")).isEqualTo(OrderIdType.CLIENT);
    assertThat(mapper.parseOrderIdType("2")).isEqualTo(OrderIdType.EXCHANGE);
    assertThatThrownBy(() -> mapper.parseOrderIdType("3"))
        .isInstanceOf(InvalidRequestException.class);
  }

  @Test
  void mapsAsyncResponses() {
    Order order = order(OrderType.MARKET, Side.SELL, null, 5);

    assertThat(mapper.toShortOrderResponse(order).status()).isEqualTo("OK");
    assertThat(mapper.toShortOrderResponse(order).clientOrderId())
        .isEqualTo(order.clientOrderId().toString());
    assertThat(mapper.toCancelOrderResponse("request-id").clientOrderId()).isEqualTo("request-id");
  }

  private static Order order(OrderType type, Side side, BigDecimal price, long quantity) {
    return Order.accept(
        new NewOrder(
            UUID.randomUUID(), "SBER", "TQBR", side, type, TimeInForce.DAY, quantity, price),
        TestInstruments.sber(),
        "20261101-TQBR-1",
        1,
        "PAPER-0001",
        NOW);
  }
}
