package ru.moex.papertrader.infra.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.moex.papertrader.TestInstruments;
import ru.moex.papertrader.core.domain.NewOrder;
import ru.moex.papertrader.core.domain.Order;
import ru.moex.papertrader.core.domain.OrderIdType;
import ru.moex.papertrader.core.domain.OrderStatus;
import ru.moex.papertrader.core.domain.OrderType;
import ru.moex.papertrader.core.domain.Side;
import ru.moex.papertrader.core.domain.TimeInForce;
import ru.moex.papertrader.core.execution.AdvLiquidityPolicy;
import ru.moex.papertrader.core.execution.MatchFill;
import ru.moex.papertrader.core.execution.MatchResult;
import ru.moex.papertrader.core.execution.MatchingEngine;
import ru.moex.papertrader.core.execution.SpecSlippageModel;
import ru.moex.papertrader.core.marketdata.Clock;
import ru.moex.papertrader.core.marketdata.MarketDataProvider;
import ru.moex.papertrader.core.marketdata.OrderBookSnapshot;
import ru.moex.papertrader.core.marketdata.TradingSession;
import ru.moex.papertrader.infra.repository.TradingCalendarRepository;
import tools.jackson.databind.json.JsonMapper;

/** Жизненный цикл исполнения: экспирация, отказ, снятие маркет-остатка и ошибки fill. */
@ExtendWith(MockitoExtension.class)
class ExecutionServiceImplTest {

  private static final Instant NOW = Instant.parse("2026-11-02T10:15:30Z");
  private static final TradingSession OPEN =
      new TradingSession(1, "Основная сессия", true, NOW.plusSeconds(3600));
  private static final TradingSession CLOSED =
      new TradingSession(0, "Сессия закрыта", false, NOW.plusSeconds(3600));
  private static final BigDecimal ADV = new BigDecimal("100000");

  @Mock private OrderService orderService;
  @Mock private FillService fillService;
  @Mock private MarketDataProvider marketDataProvider;
  @Mock private MatchingEngine matchingEngine;
  @Mock private Clock clock;

  private ExecutionServiceImpl service;

  @BeforeEach
  void setUp() {
    ExecutionProperties properties =
        new ExecutionProperties(
            true,
            1000,
            10,
            AdvLiquidityPolicy.Mode.REJECT,
            AdvLiquidityPolicy.DEFAULT_THRESHOLD_PERCENT,
            new ExecutionProperties.Slippage(
                42L, SpecSlippageModel.BASE_MIN, SpecSlippageModel.BASE_MAX));
    service =
        new ExecutionServiceImpl(
            orderService, fillService, marketDataProvider, matchingEngine, clock, properties);
    lenient().when(clock.instant()).thenReturn(NOW);
  }

  @Test
  void executeReturnsInactiveOrderWithoutMatching() {
    Order filled =
        restored(order(OrderType.LIMIT, TimeInForce.DAY, 10, "100.00"), OrderStatus.FILLED, 10);
    when(orderService.get(OrderIdType.EXCHANGE, "ORD-1")).thenReturn(filled);

    Order result = service.execute(OrderIdType.EXCHANGE, "ORD-1");

    assertThat(result).isSameAs(filled);
    verifyNoInteractions(marketDataProvider, matchingEngine, fillService);
  }

  @Test
  void executeRejectsFokOutsideTradingSession() {
    Order fok = order(OrderType.LIMIT, TimeInForce.FILL_OR_KILL, 10, "285.50");
    Order rejected = restored(fok, OrderStatus.REJECTED, 0);
    when(orderService.get(OrderIdType.EXCHANGE, fok.orderId())).thenReturn(fok);
    when(marketDataProvider.tradingSession(NOW)).thenReturn(CLOSED);
    when(orderService.reject(eq(OrderIdType.EXCHANGE), eq(fok.orderId()), anyString()))
        .thenReturn(rejected);

    Order result = service.execute(OrderIdType.EXCHANGE, fok.orderId());

    assertThat(result.status()).isEqualTo(OrderStatus.REJECTED);
    verify(orderService)
        .reject(eq(OrderIdType.EXCHANGE), eq(fok.orderId()), contains("Сессия закрыта"));
    verifyNoInteractions(matchingEngine, fillService);
  }

  @Test
  void executeRejectsMarketOrderOutsideTradingSession() {
    Order market = order(OrderType.MARKET, TimeInForce.DAY, 10, null);
    Order rejected = restored(market, OrderStatus.REJECTED, 0);
    when(orderService.get(OrderIdType.EXCHANGE, market.orderId())).thenReturn(market);
    when(marketDataProvider.tradingSession(NOW)).thenReturn(CLOSED);
    when(orderService.reject(eq(OrderIdType.EXCHANGE), eq(market.orderId()), anyString()))
        .thenReturn(rejected);

    assertThat(service.execute(OrderIdType.EXCHANGE, market.orderId()).status())
        .isEqualTo(OrderStatus.REJECTED);
  }

  @Test
  void executeRejectsOrderWhenMatchingRejectsIt() {
    Order order = order(OrderType.LIMIT, TimeInForce.DAY, 60, "285.50");
    Order rejected = restored(order, OrderStatus.REJECTED, 0);
    OrderBookSnapshot book = book();
    when(orderService.get(OrderIdType.EXCHANGE, order.orderId())).thenReturn(order);
    when(marketDataProvider.tradingSession(NOW)).thenReturn(OPEN);
    when(marketDataProvider.orderBook(order.instrument(), 10)).thenReturn(book);
    when(marketDataProvider.adv(order.instrument())).thenReturn(ADV);
    when(matchingEngine.match(order, book, ADV))
        .thenReturn(MatchResult.reject("Объём превышает 5% ADV"));
    when(orderService.reject(eq(OrderIdType.EXCHANGE), eq(order.orderId()), anyString()))
        .thenReturn(rejected);

    Order result = service.execute(OrderIdType.EXCHANGE, order.orderId());

    assertThat(result.status()).isEqualTo(OrderStatus.REJECTED);
    verify(orderService).reject(eq(OrderIdType.EXCHANGE), eq(order.orderId()), contains("5% ADV"));
    verifyNoInteractions(fillService);
  }

  @Test
  void executeRejectsOrderWhenFillFails() {
    Order order = order(OrderType.MARKET, TimeInForce.DAY, 10, null);
    Order rejected = restored(order, OrderStatus.REJECTED, 0);
    OrderBookSnapshot book = book();
    when(orderService.get(OrderIdType.EXCHANGE, order.orderId())).thenReturn(order);
    when(marketDataProvider.tradingSession(NOW)).thenReturn(OPEN);
    when(marketDataProvider.orderBook(order.instrument(), 10)).thenReturn(book);
    when(marketDataProvider.adv(order.instrument())).thenReturn(ADV);
    when(matchingEngine.match(order, book, ADV))
        .thenReturn(fills(new MatchFill(new BigDecimal("285.51"), 10)));
    doThrow(new IllegalStateException("Недостаточно свободных средств RUB"))
        .when(fillService)
        .fillAll(eq(OrderIdType.EXCHANGE), eq(order.orderId()), any());
    when(orderService.reject(eq(OrderIdType.EXCHANGE), eq(order.orderId()), anyString()))
        .thenReturn(rejected);

    Order result = service.execute(OrderIdType.EXCHANGE, order.orderId());

    assertThat(result.status()).isEqualTo(OrderStatus.REJECTED);
    verify(orderService)
        .reject(
            eq(OrderIdType.EXCHANGE),
            eq(order.orderId()),
            contains("Недостаточно свободных средств"));
  }

  @Test
  void executeCancelsUnfilledMarketRemainder() {
    Order order = order(OrderType.MARKET, TimeInForce.DAY, 10, null);
    Order partial = restored(order, OrderStatus.PARTIALLY_FILLED, 4);
    Order canceled = restored(order, OrderStatus.CANCELED, 4);
    OrderBookSnapshot book = book();
    when(orderService.get(OrderIdType.EXCHANGE, order.orderId())).thenReturn(order, partial);
    when(marketDataProvider.tradingSession(NOW)).thenReturn(OPEN);
    when(marketDataProvider.orderBook(order.instrument(), 10)).thenReturn(book);
    when(marketDataProvider.adv(order.instrument())).thenReturn(ADV);
    MatchResult match = fills(new MatchFill(new BigDecimal("285.51"), 4));
    match = new MatchResult(match.fills(), MatchResult.RemainderAction.CANCEL, null);
    when(matchingEngine.match(order, book, ADV)).thenReturn(match);
    when(orderService.cancel(eq(OrderIdType.EXCHANGE), eq(order.orderId()), anyString()))
        .thenReturn(canceled);

    Order result = service.execute(OrderIdType.EXCHANGE, order.orderId());

    assertThat(result.status()).isEqualTo(OrderStatus.CANCELED);
    verify(orderService)
        .cancel(eq(OrderIdType.EXCHANGE), eq(order.orderId()), contains("Маркет-остаток"));
  }

  @Test
  void executeKeepsLimitRemainderActive() {
    Order order = order(OrderType.LIMIT, TimeInForce.DAY, 10, "286.00");
    Order partial = restored(order, OrderStatus.PARTIALLY_FILLED, 4);
    OrderBookSnapshot book = book();
    when(orderService.get(OrderIdType.EXCHANGE, order.orderId())).thenReturn(order, partial);
    when(marketDataProvider.tradingSession(NOW)).thenReturn(OPEN);
    when(marketDataProvider.orderBook(order.instrument(), 10)).thenReturn(book);
    when(marketDataProvider.adv(order.instrument())).thenReturn(ADV);
    when(matchingEngine.match(order, book, ADV))
        .thenReturn(fills(new MatchFill(new BigDecimal("285.51"), 4)));

    Order result = service.execute(OrderIdType.EXCHANGE, order.orderId());

    assertThat(result.status()).isEqualTo(OrderStatus.PARTIALLY_FILLED);
    verify(orderService, never()).cancel(any(), anyString(), anyString());
  }

  @Test
  void tickExpiresDayOrderWhenSessionCloses() {
    Order day = order(OrderType.LIMIT, TimeInForce.DAY, 10, "285.50");
    Order expired = restored(day, OrderStatus.CANCELED, 0);
    when(marketDataProvider.tradingSession(NOW)).thenReturn(CLOSED);
    when(orderService.activeOrders()).thenReturn(List.of(day));
    when(orderService.expire(eq(OrderIdType.EXCHANGE), eq(day.orderId()), anyString()))
        .thenReturn(expired);

    int processed = service.tick();

    assertThat(processed).isEqualTo(1);
    verify(orderService).expire(eq(OrderIdType.EXCHANGE), eq(day.orderId()), anyString());
    verify(orderService, never()).get(any(), anyString());
  }

  @Test
  void tickKeepsDayOrderCreatedAfterSessionCloseAlive() {
    // Регресс E2E-07 (P3): DAY-заявка, созданная после закрытия сессии, живёт до конца следующего
    // торгового дня, а не экспайрится на первом же тике.
    Instant closeAt = NOW.minusSeconds(3600);
    TradingSession afterClose =
        new TradingSession(0, "Сессия закрыта", false, NOW.plusSeconds(20 * 3600), closeAt);
    Order day = order(OrderType.LIMIT, TimeInForce.DAY, 10, "285.50");
    when(marketDataProvider.tradingSession(NOW)).thenReturn(afterClose);
    when(orderService.activeOrders()).thenReturn(List.of(day));

    int processed = service.tick();

    assertThat(processed).isZero();
    verify(orderService, never()).expire(any(), anyString());
    verify(orderService, never()).expire(any(), anyString(), anyString());
  }

  @Test
  void tickExpiresDayOrderOfPastSessionWithBoundaryReason() {
    // Граница дня: DAY-заявка, созданная до закрытия, истекает после него; причина EXPIRED
    // содержит таймстемп границы — иначе «день закончился» неотличимо от «календарь не совпал».
    Instant closeAt = NOW.minusSeconds(3600);
    TradingSession afterClose =
        new TradingSession(0, "Сессия закрыта", false, NOW.plusSeconds(20 * 3600), closeAt);
    Order day =
        createdAt(order(OrderType.LIMIT, TimeInForce.DAY, 10, "285.50"), NOW.minusSeconds(7200));
    when(marketDataProvider.tradingSession(NOW)).thenReturn(afterClose);
    when(orderService.activeOrders()).thenReturn(List.of(day));
    when(orderService.expire(eq(OrderIdType.EXCHANGE), eq(day.orderId()), anyString()))
        .thenReturn(restedCanceled(day));

    int processed = service.tick();

    assertThat(processed).isEqualTo(1);
    verify(orderService)
        .expire(
            eq(OrderIdType.EXCHANGE),
            eq(day.orderId()),
            org.mockito.ArgumentMatchers.contains(closeAt.toString()));
  }

  @Test
  void tickKeepsDayOrderCreatedBeforeOpenAlive() {
    // DAY-заявка, созданная до открытия, ждёт сессию и не экспайрится.
    TradingSession beforeOpen =
        new TradingSession(
            1, "Основная сессия", false, NOW.plusSeconds(3600), NOW.plusSeconds(10 * 3600));
    Order day =
        createdAt(order(OrderType.LIMIT, TimeInForce.DAY, 10, "285.50"), NOW.minusSeconds(600));
    when(marketDataProvider.tradingSession(NOW)).thenReturn(beforeOpen);
    when(orderService.activeOrders()).thenReturn(List.of(day));

    int processed = service.tick();

    assertThat(processed).isZero();
    verify(orderService, never()).expire(any(), anyString());
    verify(orderService, never()).expire(any(), anyString(), anyString());
  }

  @Test
  void tickExpiresGtdOrderAfterExpiryDate() {
    Order gtd = gtdOrder(LocalDate.of(2026, 11, 1));
    Order expired = restored(gtd, OrderStatus.CANCELED, 0);
    when(marketDataProvider.tradingSession(NOW)).thenReturn(OPEN);
    when(orderService.activeOrders()).thenReturn(List.of(gtd));
    when(orderService.expire(eq(OrderIdType.EXCHANGE), eq(gtd.orderId()), anyString()))
        .thenReturn(expired);

    int processed = service.tick();

    assertThat(processed).isEqualTo(1);
    verify(orderService).expire(eq(OrderIdType.EXCHANGE), eq(gtd.orderId()), anyString());
  }

  @Test
  void tickExpiresGtdOrderAtCloseOfExpiryDate() {
    Order gtd = gtdOrder(NOW.atZone(ZoneId.of("Europe/Moscow")).toLocalDate());
    when(marketDataProvider.tradingSession(NOW)).thenReturn(CLOSED);
    when(orderService.activeOrders()).thenReturn(List.of(gtd));
    when(orderService.expire(eq(OrderIdType.EXCHANGE), eq(gtd.orderId()), anyString()))
        .thenReturn(restored(gtd, OrderStatus.CANCELED, 0));

    service.tick();

    verify(orderService).expire(eq(OrderIdType.EXCHANGE), eq(gtd.orderId()), anyString());
  }

  @Test
  void tickExecutesActiveLimitOrderWhileSessionIsOpen() {
    Order limit = order(OrderType.LIMIT, TimeInForce.DAY, 10, "285.50");
    OrderBookSnapshot book = book();
    when(marketDataProvider.tradingSession(NOW)).thenReturn(OPEN);
    when(orderService.activeOrders()).thenReturn(List.of(limit));
    when(orderService.get(OrderIdType.EXCHANGE, limit.orderId())).thenReturn(limit);
    when(marketDataProvider.orderBook(limit.instrument(), 10)).thenReturn(book);
    when(marketDataProvider.adv(limit.instrument())).thenReturn(ADV);
    when(matchingEngine.match(limit, book, ADV)).thenReturn(MatchResult.none());

    int processed = service.tick();

    assertThat(processed).isEqualTo(1);
    verify(orderService, never()).expire(any(), anyString());
    verifyNoInteractions(fillService);
  }

  @Test
  void tickExecutesDayOrderWhenCalendarIsEmptyButTimeInsideFallbackWindow() {
    // Регресс E2E-07: в LIVE с пустым календарём торговая сессия определяется fallback-окном
    // настроек (будний день 07:00–23:50 МСК), DAY-заявка не экспайрится на первом тике.
    TradingCalendarRepository repository = mock(TradingCalendarRepository.class);
    TradingCalendarService emptyCalendar =
        new TradingCalendarService(repository, MarketDataProperties.defaults(), jsonMapper());
    lenient()
        .when(marketDataProvider.tradingSession(NOW))
        .thenAnswer(invocation -> emptyCalendar.tradingSession(NOW));
    Order day = order(OrderType.LIMIT, TimeInForce.DAY, 10, "285.50");
    OrderBookSnapshot book = book();
    lenient().when(orderService.activeOrders()).thenReturn(List.of(day));
    lenient().when(orderService.get(OrderIdType.EXCHANGE, day.orderId())).thenReturn(day);
    lenient().when(marketDataProvider.orderBook(day.instrument(), 10)).thenReturn(book);
    lenient().when(marketDataProvider.adv(day.instrument())).thenReturn(ADV);
    lenient().when(matchingEngine.match(day, book, ADV)).thenReturn(MatchResult.none());

    int processed = service.tick();

    assertThat(processed).isEqualTo(1);
    verify(orderService, never()).expire(any(), anyString());
  }

  @Test
  void tickSkipsFokOutsideSessionWithoutRejectingIt() {
    Order fok = order(OrderType.LIMIT, TimeInForce.FILL_OR_KILL, 10, "285.50");
    when(marketDataProvider.tradingSession(NOW)).thenReturn(CLOSED);
    when(orderService.activeOrders()).thenReturn(List.of(fok));

    int processed = service.tick();

    assertThat(processed).isZero();
    verify(orderService, never()).expire(any(), anyString());
    verify(orderService, never()).reject(any(), any(), any());
    verifyNoInteractions(matchingEngine);
  }

  @Test
  void tickContinuesWhenOneOrderFails() {
    Order broken = order("ORD-1", OrderType.LIMIT, TimeInForce.DAY, 10, "285.50");
    Order second = order("ORD-2", OrderType.LIMIT, TimeInForce.DAY, 50, "100.00");
    when(marketDataProvider.tradingSession(NOW)).thenReturn(OPEN);
    when(orderService.activeOrders()).thenReturn(List.of(broken, second));
    when(orderService.get(OrderIdType.EXCHANGE, broken.orderId()))
        .thenThrow(new IllegalStateException("Заявка недоступна"));
    when(orderService.get(OrderIdType.EXCHANGE, second.orderId())).thenReturn(second);
    when(marketDataProvider.orderBook(second.instrument(), 10)).thenReturn(book());
    when(marketDataProvider.adv(second.instrument())).thenReturn(ADV);
    when(matchingEngine.match(eq(second), any(), any())).thenReturn(MatchResult.none());

    int processed = service.tick();

    assertThat(processed).isEqualTo(1);
  }

  private static Order order(OrderType type, TimeInForce timeInForce, long quantity, String price) {
    return order("ORD-1", type, timeInForce, quantity, price);
  }

  private static Order order(
      String orderId, OrderType type, TimeInForce timeInForce, long quantity, String price) {
    return Order.accept(
        new NewOrder(
            UUID.randomUUID(),
            "SBER",
            "TQBR",
            Side.BUY,
            type,
            timeInForce,
            quantity,
            price == null ? null : new BigDecimal(price)),
        TestInstruments.sber(),
        orderId,
        1,
        "PAPER-0001",
        NOW);
  }

  private static Order gtdOrder(LocalDate expiryDate) {
    return Order.restore(
        1,
        UUID.randomUUID(),
        "ORD-1",
        TestInstruments.sber(),
        Side.BUY,
        OrderType.LIMIT,
        TimeInForce.GOOD_TILL_DATE,
        10,
        new BigDecimal("285.50"),
        "PAPER-0001",
        NOW,
        NOW,
        OrderStatus.NEW,
        0,
        null,
        expiryDate);
  }

  /** Копия заявки с другим моментом создания (сценарии экспирации DAY). */
  private static Order createdAt(Order base, Instant createdAt) {
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
        createdAt,
        createdAt,
        OrderStatus.NEW,
        0,
        null,
        base.expiryDate());
  }

  private static Order restedCanceled(Order base) {
    return restored(base, OrderStatus.CANCELED, base.executedQuantity());
  }

  private static Order restored(Order base, OrderStatus status, long executedQuantity) {
    BigDecimal averagePrice =
        executedQuantity == 0
            ? null
            : base.price() == null ? new BigDecimal("285.51") : base.price();
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
        executedQuantity,
        averagePrice,
        base.expiryDate());
  }

  private static MatchResult fills(MatchFill fill) {
    return new MatchResult(List.of(fill), MatchResult.RemainderAction.KEEP, null);
  }

  private static JsonMapper jsonMapper() {
    return JsonMapper.builder().build();
  }

  private static OrderBookSnapshot book() {
    return new OrderBookSnapshot(
        "SBER",
        "TQBR",
        10,
        NOW,
        List.of(new OrderBookSnapshot.Level(new BigDecimal("285.49"), 100)),
        List.of(new OrderBookSnapshot.Level(new BigDecimal("285.51"), 100)));
  }
}
