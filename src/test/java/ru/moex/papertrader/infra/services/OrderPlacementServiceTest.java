package ru.moex.papertrader.infra.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.moex.papertrader.TestInstruments;
import ru.moex.papertrader.core.domain.Instrument;
import ru.moex.papertrader.core.domain.NewOrder;
import ru.moex.papertrader.core.domain.Order;
import ru.moex.papertrader.core.domain.OrderIdType;
import ru.moex.papertrader.core.domain.OrderStatus;
import ru.moex.papertrader.core.domain.OrderType;
import ru.moex.papertrader.core.domain.Side;
import ru.moex.papertrader.core.domain.TimeInForce;
import ru.moex.papertrader.core.execution.AdvLiquidityPolicy;
import ru.moex.papertrader.core.marketdata.MarketDataProvider;
import ru.moex.papertrader.infra.services.exception.LiquidityExceededException;

/** Постановка заявки: предпроверка ADV до сохранения и best-effort исполнение после него. */
@ExtendWith(MockitoExtension.class)
class OrderPlacementServiceTest {

  private static final Instant NOW = Instant.parse("2026-11-02T10:15:30Z");

  @Mock private OrderService orderService;
  @Mock private ExecutionService executionService;
  @Mock private MarketDataProvider marketDataProvider;

  private Order order;

  @BeforeEach
  void setUp() {
    order = accepted(60);
  }

  @Test
  void rejectsOrderAboveAdvBeforeSaving() {
    OrderPlacementService service =
        new OrderPlacementService(
            orderService,
            executionService,
            marketDataProvider,
            AdvLiquidityPolicy.rejectAtDefaultThreshold());
    stubKnownInstrument();
    when(marketDataProvider.adv(TestInstruments.sber())).thenReturn(new BigDecimal("1000"));

    assertThatThrownBy(() -> service.place(request(60)))
        .isInstanceOf(LiquidityExceededException.class)
        .hasMessageContaining("5%")
        .hasMessageContaining("60");

    verify(orderService, never()).place(any());
    verifyNoInteractions(executionService);
  }

  @Test
  void splitPolicyAllowsOrderThatExceedsAdvThreshold() {
    OrderPlacementService service =
        new OrderPlacementService(
            orderService,
            executionService,
            marketDataProvider,
            new AdvLiquidityPolicy(
                AdvLiquidityPolicy.Mode.SPLIT, AdvLiquidityPolicy.DEFAULT_THRESHOLD_PERCENT));
    stubKnownInstrument();
    when(marketDataProvider.adv(TestInstruments.sber())).thenReturn(new BigDecimal("1000"));
    when(orderService.place(any())).thenReturn(order);
    when(executionService.execute(OrderIdType.EXCHANGE, order.orderId())).thenReturn(order);

    Order result = service.place(request(60));

    assertThat(result).isSameAs(order);
    verify(orderService).place(any());
  }

  @Test
  void unknownInstrumentSkipsLiquidityCheckAndSavesOrder() {
    OrderPlacementService service =
        new OrderPlacementService(
            orderService,
            executionService,
            marketDataProvider,
            AdvLiquidityPolicy.rejectAtDefaultThreshold());
    when(marketDataProvider.instrument("SBER", "TQBR")).thenReturn(Optional.empty());
    when(orderService.place(any())).thenReturn(order);
    when(executionService.execute(OrderIdType.EXCHANGE, order.orderId())).thenReturn(order);

    Order result = service.place(request(60));

    assertThat(result).isSameAs(order);
    verify(orderService).place(any());
  }

  @Test
  void nonPositiveQuantitySkipsLiquidityCheck() {
    OrderPlacementService service =
        new OrderPlacementService(
            orderService,
            executionService,
            marketDataProvider,
            AdvLiquidityPolicy.rejectAtDefaultThreshold());
    when(orderService.place(any())).thenReturn(order);
    when(executionService.execute(OrderIdType.EXCHANGE, order.orderId())).thenReturn(order);

    service.place(request(0));

    verifyNoInteractions(marketDataProvider);
  }

  @Test
  void returnsReloadedOrderWhenImmediateExecutionFails() {
    OrderPlacementService service =
        new OrderPlacementService(
            orderService,
            executionService,
            marketDataProvider,
            AdvLiquidityPolicy.rejectAtDefaultThreshold());
    Order rejected = restored(order, OrderStatus.REJECTED);
    when(marketDataProvider.instrument("SBER", "TQBR")).thenReturn(Optional.empty());
    when(orderService.place(any())).thenReturn(order);
    when(executionService.execute(OrderIdType.EXCHANGE, order.orderId()))
        .thenThrow(new IllegalStateException("Исполнение не удалось"));
    when(orderService.get(OrderIdType.EXCHANGE, order.orderId())).thenReturn(rejected);

    Order result = service.place(request(1));

    assertThat(result.status()).isEqualTo(OrderStatus.REJECTED);
  }

  @Test
  void returnsPlacedOrderWhenStateCannotBeReloaded() {
    OrderPlacementService service =
        new OrderPlacementService(
            orderService,
            executionService,
            marketDataProvider,
            AdvLiquidityPolicy.rejectAtDefaultThreshold());
    when(marketDataProvider.instrument("SBER", "TQBR")).thenReturn(Optional.empty());
    when(orderService.place(any())).thenReturn(order);
    when(executionService.execute(OrderIdType.EXCHANGE, order.orderId()))
        .thenThrow(new IllegalStateException("Исполнение не удалось"));
    when(orderService.get(OrderIdType.EXCHANGE, order.orderId()))
        .thenThrow(new IllegalStateException("Состояние недоступно"));

    Order result = service.place(request(1));

    assertThat(result).isSameAs(order);
  }

  private void stubKnownInstrument() {
    Instrument instrument = TestInstruments.sber();
    when(marketDataProvider.instrument("SBER", "TQBR")).thenReturn(Optional.of(instrument));
  }

  private static NewOrder request(long quantity) {
    return new NewOrder(
        UUID.randomUUID(),
        "SBER",
        "TQBR",
        Side.BUY,
        OrderType.LIMIT,
        TimeInForce.DAY,
        quantity,
        new BigDecimal("285.50"));
  }

  private static Order accepted(long quantity) {
    return Order.accept(
        new NewOrder(
            UUID.randomUUID(),
            "SBER",
            "TQBR",
            Side.BUY,
            OrderType.LIMIT,
            TimeInForce.DAY,
            quantity,
            new BigDecimal("285.50")),
        TestInstruments.sber(),
        "ORD-1",
        1,
        "PAPER-0001",
        NOW);
  }

  private static Order restored(Order base, OrderStatus status) {
    return Order.restore(
        base.orderNum(),
        base.clientOrderId(),
        base.orderId(),
        base.instrument(),
        base.side(),
        base.orderType(),
        base.timeInForce(),
        base.quantity(),
        base.price(),
        base.clientCode(),
        base.createdAt(),
        NOW,
        status,
        0,
        null,
        base.expiryDate());
  }
}
