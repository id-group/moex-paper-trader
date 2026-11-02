package ru.moex.papertrader.infra.mapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import ru.moex.papertrader.TestInstruments;
import ru.moex.papertrader.core.domain.Fill;
import ru.moex.papertrader.core.domain.NewOrder;
import ru.moex.papertrader.core.domain.Order;
import ru.moex.papertrader.core.domain.OrderStatus;
import ru.moex.papertrader.core.domain.OrderType;
import ru.moex.papertrader.core.domain.Side;
import ru.moex.papertrader.core.domain.TimeInForce;
import ru.moex.papertrader.infra.model.OrderEntity;

class OrderMapperTest {

  private static final Instant CREATED = Instant.parse("2026-11-02T10:00:00Z");
  private static final Instant CANCELLED = Instant.parse("2026-11-02T10:05:00Z");
  private static final String ACCOUNT = "PAPER-0001";
  private static final Long INSTRUMENT_ID = 42L;

  private final OrderMapper mapper = new OrderMapper();

  @Test
  void mapsOrderToEntityAndKeepsInfraReferences() {
    Order order = accepted(OrderType.LIMIT, Side.BUY, new BigDecimal("100.5"), 10);

    OrderEntity entity = mapper.toEntity(order, ACCOUNT, INSTRUMENT_ID);

    assertThat(entity.getOrderId()).isEqualTo("ORD-1");
    assertThat(entity.getOrderNum()).isEqualTo(7);
    assertThat(entity.getClientOrderId()).isEqualTo(order.clientOrderId());
    assertThat(entity.getAccountId()).isEqualTo(ACCOUNT);
    assertThat(entity.getInstrumentId()).isEqualTo(INSTRUMENT_ID);
    assertThat(entity.getSide()).isEqualTo(Side.BUY);
    assertThat(entity.getOrderType()).isEqualTo(OrderType.LIMIT);
    assertThat(entity.getTimeInForce()).isEqualTo(TimeInForce.DAY);
    assertThat(entity.getQuantity()).isEqualTo(10);
    assertThat(entity.getPrice()).isEqualByComparingTo("100.5");
    assertThat(entity.getPrice().scale()).isEqualTo(6);
    assertThat(entity.getClientCode()).isEqualTo(ACCOUNT);
    assertThat(entity.getCreatedAt()).isEqualTo(CREATED);
    assertThat(entity.getUpdatedAt()).isEqualTo(CREATED);
    assertThat(entity.getStatus()).isEqualTo(OrderStatus.NEW);
    assertThat(entity.getExecutedQuantity()).isZero();
    assertThat(entity.getAveragePrice()).isNull();
  }

  @Test
  void restoresNewOrderFromEntity() {
    Order original = accepted(OrderType.LIMIT, Side.BUY, new BigDecimal("100.5"), 10);
    OrderEntity entity = mapper.toEntity(original, ACCOUNT, INSTRUMENT_ID);

    Order restored = mapper.toDomain(entity, TestInstruments.sber());

    assertThat(restored.orderId()).isEqualTo(original.orderId());
    assertThat(restored.orderNum()).isEqualTo(original.orderNum());
    assertThat(restored.clientOrderId()).isEqualTo(original.clientOrderId());
    assertThat(restored.instrument()).isEqualTo(TestInstruments.sber());
    assertThat(restored.side()).isEqualTo(Side.BUY);
    assertThat(restored.orderType()).isEqualTo(OrderType.LIMIT);
    assertThat(restored.timeInForce()).isEqualTo(TimeInForce.DAY);
    assertThat(restored.quantity()).isEqualTo(10);
    assertThat(restored.price()).isEqualByComparingTo("100.5");
    assertThat(restored.clientCode()).isEqualTo(ACCOUNT);
    assertThat(restored.createdAt()).isEqualTo(CREATED);
    assertThat(restored.updatedAt()).isEqualTo(CREATED);
    assertThat(restored.status()).isEqualTo(OrderStatus.NEW);
    assertThat(restored.executedQuantity()).isZero();
    assertThat(restored.averagePrice()).isNull();
  }

  @Test
  void restoresCanceledOrderWithUpdatedAt() {
    Order original = accepted(OrderType.LIMIT, Side.SELL, new BigDecimal("101.25"), 3);
    original.cancel(CANCELLED);
    OrderEntity entity = mapper.toEntity(original, ACCOUNT, INSTRUMENT_ID);

    Order restored = mapper.toDomain(entity, TestInstruments.sber());

    assertThat(restored.status()).isEqualTo(OrderStatus.CANCELED);
    assertThat(restored.updatedAt()).isEqualTo(CANCELLED);
    assertThat(restored.createdAt()).isEqualTo(CREATED);
  }

  @Test
  void restoresMarketOrderWithoutPrice() {
    Order original = accepted(OrderType.MARKET, Side.BUY, null, 5);
    assertThat(original.price()).isNull();

    Order restored =
        mapper.toDomain(mapper.toEntity(original, ACCOUNT, INSTRUMENT_ID), TestInstruments.sber());

    assertThat(restored.price()).isNull();
    assertThat(restored.orderType()).isEqualTo(OrderType.MARKET);
  }

  @Test
  void roundTripsDomainEntityDomain() {
    Order original = accepted(OrderType.LIMIT, Side.BUY, new BigDecimal("100.500000"), 10);

    Order restored =
        mapper.toDomain(mapper.toEntity(original, ACCOUNT, INSTRUMENT_ID), TestInstruments.sber());

    assertThat(restored.orderId()).isEqualTo(original.orderId());
    assertThat(restored.orderNum()).isEqualTo(original.orderNum());
    assertThat(restored.clientOrderId()).isEqualTo(original.clientOrderId());
    assertThat(restored.instrument()).isEqualTo(original.instrument());
    assertThat(restored.side()).isEqualTo(original.side());
    assertThat(restored.orderType()).isEqualTo(original.orderType());
    assertThat(restored.timeInForce()).isEqualTo(original.timeInForce());
    assertThat(restored.quantity()).isEqualTo(original.quantity());
    assertThat(restored.price()).isEqualByComparingTo(original.price());
    assertThat(restored.clientCode()).isEqualTo(original.clientCode());
    assertThat(restored.createdAt()).isEqualTo(original.createdAt());
    assertThat(restored.updatedAt()).isEqualTo(original.updatedAt());
    assertThat(restored.status()).isEqualTo(original.status());
    assertThat(restored.executedQuantity()).isEqualTo(original.executedQuantity());
    assertThat(restored.averagePrice()).isEqualTo(original.averagePrice());
  }

  @Test
  void roundTripsEntityDomainEntity() {
    OrderEntity original =
        mapper.toEntity(
            accepted(OrderType.LIMIT, Side.BUY, new BigDecimal("100.500000"), 10),
            ACCOUNT,
            INSTRUMENT_ID);

    OrderEntity restored =
        mapper.toEntity(mapper.toDomain(original, TestInstruments.sber()), ACCOUNT, INSTRUMENT_ID);

    assertThat(restored.getOrderId()).isEqualTo(original.getOrderId());
    assertThat(restored.getOrderNum()).isEqualTo(original.getOrderNum());
    assertThat(restored.getClientOrderId()).isEqualTo(original.getClientOrderId());
    assertThat(restored.getAccountId()).isEqualTo(original.getAccountId());
    assertThat(restored.getInstrumentId()).isEqualTo(original.getInstrumentId());
    assertThat(restored.getSide()).isEqualTo(original.getSide());
    assertThat(restored.getOrderType()).isEqualTo(original.getOrderType());
    assertThat(restored.getTimeInForce()).isEqualTo(original.getTimeInForce());
    assertThat(restored.getQuantity()).isEqualTo(original.getQuantity());
    assertThat(restored.getPrice()).isEqualByComparingTo(original.getPrice());
    assertThat(restored.getClientCode()).isEqualTo(original.getClientCode());
    assertThat(restored.getCreatedAt()).isEqualTo(original.getCreatedAt());
    assertThat(restored.getUpdatedAt()).isEqualTo(original.getUpdatedAt());
    assertThat(restored.getStatus()).isEqualTo(original.getStatus());
    assertThat(restored.getExecutedQuantity()).isEqualTo(original.getExecutedQuantity());
    assertThat(restored.getAveragePrice()).isEqualTo(original.getAveragePrice());
  }

  @Test
  void normalizesPriceScaleOnRead() {
    Order original = accepted(OrderType.LIMIT, Side.BUY, new BigDecimal("100.500000"), 10);
    original.applyFill(new Fill(new BigDecimal("101.250000"), 4, BigDecimal.ZERO, CANCELLED));

    Order restored =
        mapper.toDomain(mapper.toEntity(original, ACCOUNT, INSTRUMENT_ID), TestInstruments.sber());

    // Масштаб схемы NUMERIC(19,6) не должен протекать в домен и JSON БКС.
    assertThat(restored.price().toPlainString()).isEqualTo("100.5");
    assertThat(restored.averagePrice().toPlainString()).isEqualTo("101.25");
  }

  @Test
  void rejectsStatusThatCannotBeRestored() {
    OrderEntity entity = entity(OrderStatus.FILLED, 0, null, CREATED, CREATED);

    // FILLED без полного исполнения — противоречие в строке БД, а не бизнес-отказ.
    assertThatThrownBy(() -> mapper.toDomain(entity, TestInstruments.sber()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("FILLED");
  }

  @Test
  void rejectsExecutedQuantityThatCannotBeRestored() {
    OrderEntity entity = entity(OrderStatus.NEW, 5, new BigDecimal("100.000000"), CREATED, CREATED);

    assertThatThrownBy(() -> mapper.toDomain(entity, TestInstruments.sber()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("NEW");
  }

  @ParameterizedTest
  @MethodSource("ordersInEveryStatus")
  void roundTripsDomainEntityDomainForEveryStatus(Order original) {
    OrderEntity entity = mapper.toEntity(original, ACCOUNT, INSTRUMENT_ID);

    Order restored = mapper.toDomain(entity, TestInstruments.sber());

    assertThat(restored.orderId()).isEqualTo(original.orderId());
    assertThat(restored.orderNum()).isEqualTo(original.orderNum());
    assertThat(restored.clientOrderId()).isEqualTo(original.clientOrderId());
    assertThat(restored.instrument()).isEqualTo(original.instrument());
    assertThat(restored.side()).isEqualTo(original.side());
    assertThat(restored.orderType()).isEqualTo(original.orderType());
    assertThat(restored.timeInForce()).isEqualTo(original.timeInForce());
    assertThat(restored.quantity()).isEqualTo(original.quantity());
    assertThat(restored.price()).isEqualByComparingTo(original.price());
    assertThat(restored.clientCode()).isEqualTo(original.clientCode());
    assertThat(restored.createdAt()).isEqualTo(original.createdAt());
    assertThat(restored.updatedAt()).isEqualTo(original.updatedAt());
    assertThat(restored.status()).isEqualTo(original.status());
    assertThat(restored.executedQuantity()).isEqualTo(original.executedQuantity());
    if (original.averagePrice() == null) {
      assertThat(restored.averagePrice()).isNull();
    } else {
      assertThat(restored.averagePrice()).isEqualByComparingTo(original.averagePrice());
      assertThat(restored.averagePrice().toPlainString())
          .isEqualTo(original.averagePrice().stripTrailingZeros().toPlainString());
    }
  }

  @ParameterizedTest
  @MethodSource("ordersInEveryStatus")
  void roundTripsEntityDomainEntityForEveryStatus(Order original) {
    OrderEntity entity = mapper.toEntity(original, ACCOUNT, INSTRUMENT_ID);

    OrderEntity restored =
        mapper.toEntity(mapper.toDomain(entity, TestInstruments.sber()), ACCOUNT, INSTRUMENT_ID);

    assertThat(restored.getOrderId()).isEqualTo(entity.getOrderId());
    assertThat(restored.getOrderNum()).isEqualTo(entity.getOrderNum());
    assertThat(restored.getClientOrderId()).isEqualTo(entity.getClientOrderId());
    assertThat(restored.getAccountId()).isEqualTo(entity.getAccountId());
    assertThat(restored.getInstrumentId()).isEqualTo(entity.getInstrumentId());
    assertThat(restored.getSide()).isEqualTo(entity.getSide());
    assertThat(restored.getOrderType()).isEqualTo(entity.getOrderType());
    assertThat(restored.getTimeInForce()).isEqualTo(entity.getTimeInForce());
    assertThat(restored.getQuantity()).isEqualTo(entity.getQuantity());
    assertThat(restored.getPrice()).isEqualByComparingTo(entity.getPrice());
    assertThat(restored.getPrice().scale()).isEqualTo(MappingScales.PRICE_SCALE);
    assertThat(restored.getClientCode()).isEqualTo(entity.getClientCode());
    assertThat(restored.getCreatedAt()).isEqualTo(entity.getCreatedAt());
    assertThat(restored.getUpdatedAt()).isEqualTo(entity.getUpdatedAt());
    assertThat(restored.getStatus()).isEqualTo(entity.getStatus());
    assertThat(restored.getExecutedQuantity()).isEqualTo(entity.getExecutedQuantity());
    if (entity.getAveragePrice() == null) {
      assertThat(restored.getAveragePrice()).isNull();
    } else {
      assertThat(restored.getAveragePrice().scale()).isEqualTo(MappingScales.PRICE_SCALE);
      assertThat(restored.getAveragePrice()).isEqualByComparingTo(entity.getAveragePrice());
    }
  }

  static Stream<Arguments> ordersInEveryStatus() {
    Order partiallyFilled = accepted(OrderType.LIMIT, Side.BUY, new BigDecimal("100.500000"), 10);
    partiallyFilled.applyFill(
        new Fill(new BigDecimal("101.250000"), 4, BigDecimal.ZERO, CANCELLED));
    Order filled = accepted(OrderType.LIMIT, Side.SELL, new BigDecimal("102.500000"), 10);
    filled.applyFill(new Fill(new BigDecimal("102.500000"), 10, BigDecimal.ZERO, CANCELLED));
    Order canceled = accepted(OrderType.LIMIT, Side.BUY, new BigDecimal("100.500000"), 10);
    canceled.applyFill(new Fill(new BigDecimal("101.250000"), 4, BigDecimal.ZERO, CREATED));
    canceled.cancel(CANCELLED);
    return Stream.of(
        Arguments.of(accepted(OrderType.LIMIT, Side.BUY, new BigDecimal("100.500000"), 10)),
        Arguments.of(partiallyFilled),
        Arguments.of(filled),
        Arguments.of(canceled));
  }

  private static Order accepted(OrderType orderType, Side side, BigDecimal price, long quantity) {
    return Order.accept(
        new NewOrder(
            UUID.fromString("11111111-1111-1111-1111-111111111111"),
            "SBER",
            "TQBR",
            side,
            orderType,
            TimeInForce.DAY,
            quantity,
            price),
        TestInstruments.sber(),
        "ORD-1",
        7,
        ACCOUNT,
        CREATED);
  }

  private static OrderEntity entity(
      OrderStatus status,
      long executedQuantity,
      BigDecimal averagePrice,
      Instant createdAt,
      Instant updatedAt) {
    return new OrderEntity(
        "ORD-1",
        7,
        UUID.fromString("11111111-1111-1111-1111-111111111111"),
        ACCOUNT,
        INSTRUMENT_ID,
        Side.BUY,
        OrderType.LIMIT,
        TimeInForce.DAY,
        10,
        new BigDecimal("100.500000"),
        ACCOUNT,
        createdAt,
        updatedAt,
        status,
        executedQuantity,
        averagePrice);
  }
}
