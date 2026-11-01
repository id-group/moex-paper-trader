package ru.moex.papertrader.infra.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ru.moex.papertrader.core.domain.NewOrder;
import ru.moex.papertrader.core.domain.Order;
import ru.moex.papertrader.core.domain.OrderIdType;
import ru.moex.papertrader.core.domain.OrderSearchCriteria;
import ru.moex.papertrader.core.domain.OrderStatus;
import ru.moex.papertrader.core.domain.OrderType;
import ru.moex.papertrader.core.domain.PageResult;
import ru.moex.papertrader.core.domain.Side;
import ru.moex.papertrader.core.domain.TimeInForce;
import ru.moex.papertrader.infra.services.exception.ConflictException;
import ru.moex.papertrader.infra.services.exception.InvalidRequestException;
import ru.moex.papertrader.infra.services.exception.ResourceNotFoundException;

class InMemoryOrderServiceImplTest {

  private static final Instant NOW = Instant.parse("2026-11-01T10:15:30Z");

  private InMemoryOrderServiceImpl service;

  @BeforeEach
  void setUp() {
    Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
    StubMarketDataProvider marketData =
        new StubMarketDataProvider(
            new StubMarketDataProperties(List.of(sberDefinition(), gazpDefinition())), clock);
    service =
        new InMemoryOrderServiceImpl(
            clock,
            marketData,
            new PortfolioProperties("PAPER-0001", new BigDecimal("1000000"), "RUB"));
  }

  @Test
  void placesOrderAndAssignsExchangeIdentifier() {
    UUID clientOrderId = UUID.randomUUID();

    Order order = service.place(limitOrder(clientOrderId, "SBER", Side.BUY, "285.50", 10));

    assertThat(order.clientOrderId()).isEqualTo(clientOrderId);
    assertThat(order.orderNum()).isPositive();
    assertThat(order.orderId()).matches("20261101-TQBR-\\d+");
    assertThat(order.status()).isEqualTo(OrderStatus.NEW);
    assertThat(order.price()).isEqualByComparingTo("285.50");
  }

  @Test
  void rejectsDuplicateClientOrderId() {
    UUID clientOrderId = UUID.randomUUID();
    service.place(limitOrder(clientOrderId, "SBER", Side.BUY, "285.50", 10));

    assertThatThrownBy(
            () -> service.place(limitOrder(clientOrderId, "SBER", Side.BUY, "285.50", 10)))
        .isInstanceOf(ConflictException.class);
  }

  @Test
  void rejectsUnknownInstrument() {
    assertThatThrownBy(() -> service.place(limitOrder(UUID.randomUUID(), "NOPE", Side.BUY, "1", 1)))
        .isInstanceOf(ResourceNotFoundException.class);
  }

  @Test
  void findsOrderByClientAndExchangeIdentifiers() {
    Order order = service.place(limitOrder(UUID.randomUUID(), "SBER", Side.BUY, "285.50", 10));

    assertThat(service.get(OrderIdType.CLIENT, order.clientOrderId().toString())).isSameAs(order);
    assertThat(service.get(OrderIdType.EXCHANGE, order.orderId())).isSameAs(order);
  }

  @Test
  void unknownIdentifierReturnsNotFound() {
    assertThatThrownBy(() -> service.get(OrderIdType.CLIENT, UUID.randomUUID().toString()))
        .isInstanceOf(ResourceNotFoundException.class);
    assertThatThrownBy(() -> service.get(OrderIdType.EXCHANGE, "20261101-TQBR-0"))
        .isInstanceOf(ResourceNotFoundException.class);
  }

  @Test
  void malformedClientIdentifierReturnsBadRequest() {
    assertThatThrownBy(() -> service.get(OrderIdType.CLIENT, "not-a-uuid"))
        .isInstanceOf(InvalidRequestException.class);
  }

  @Test
  void cancelRemovesOrderFromActiveList() {
    Order order = service.place(limitOrder(UUID.randomUUID(), "SBER", Side.BUY, "285.50", 10));

    Order cancelled = service.cancel(OrderIdType.CLIENT, order.clientOrderId().toString());

    assertThat(cancelled.status()).isEqualTo(OrderStatus.CANCELED);
    assertThat(service.activeOrders()).isEmpty();
  }

  @Test
  void searchFiltersByTickerSideAndStatus() {
    Order sberBuy = service.place(limitOrder(UUID.randomUUID(), "SBER", Side.BUY, "285.50", 10));
    service.place(limitOrder(UUID.randomUUID(), "GAZP", Side.SELL, "165.20", 5));
    Order sberSell = service.place(limitOrder(UUID.randomUUID(), "SBER", Side.SELL, "290", 10));
    service.cancel(OrderIdType.CLIENT, sberSell.clientOrderId().toString());

    PageResult<Order> byTicker =
        service.search(
            new OrderSearchCriteria(
                null, null, Side.BUY, null, null, null, java.util.Set.of("SBER"), null),
            0,
            10,
            List.of());
    PageResult<Order> activeOnly =
        service.search(
            new OrderSearchCriteria(
                null, null, null, java.util.Set.of(OrderStatus.NEW), null, null, null, null),
            0,
            10,
            List.of());

    assertThat(byTicker.items()).containsExactly(sberBuy);
    assertThat(activeOnly.items()).hasSize(2);
    assertThat(activeOnly.totalRecords()).isEqualTo(2);
  }

  @Test
  void searchSupportsSortingAndPagination() {
    service.place(limitOrder(UUID.randomUUID(), "SBER", Side.BUY, "285.50", 10));
    service.place(limitOrder(UUID.randomUUID(), "SBER", Side.BUY, "286.50", 10));
    service.place(limitOrder(UUID.randomUUID(), "SBER", Side.BUY, "287.50", 10));

    PageResult<Order> firstPage =
        service.search(
            new OrderSearchCriteria(null, null, null, null, null, null, null, null),
            0,
            2,
            List.of("ticker,asc"));
    PageResult<Order> secondPage =
        service.search(
            new OrderSearchCriteria(null, null, null, null, null, null, null, null),
            1,
            2,
            List.of("orderDateTime,desc"));

    assertThat(firstPage.items()).hasSize(2);
    assertThat(firstPage.totalRecords()).isEqualTo(3);
    assertThat(firstPage.totalPages()).isEqualTo(2);
    assertThat(secondPage.items()).hasSize(1);
  }

  @Test
  void invalidSortFieldReturnsBadRequest() {
    OrderSearchCriteria criteria =
        new OrderSearchCriteria(null, null, null, null, null, null, null, null);

    assertThatThrownBy(() -> service.search(criteria, 0, 10, List.of("unknown,asc")))
        .isInstanceOf(InvalidRequestException.class);
  }

  @Test
  void filtersByOrderNumberAndDateRange() {
    Order first = service.place(limitOrder(UUID.randomUUID(), "SBER", Side.BUY, "285.50", 10));

    PageResult<Order> byNumber =
        service.search(
            new OrderSearchCriteria(
                null, null, null, null, null, java.util.Set.of(first.orderNum()), null, null),
            0,
            10,
            List.of());
    PageResult<Order> byDate =
        service.search(
            new OrderSearchCriteria(
                NOW.minusSeconds(60), NOW.plusSeconds(60), null, null, null, null, null, null),
            0,
            10,
            List.of());

    assertThat(byNumber.items()).containsExactly(first);
    assertThat(byDate.items()).containsExactly(first);
  }

  private static NewOrder limitOrder(
      UUID clientOrderId, String ticker, Side side, String price, long quantity) {
    return new NewOrder(
        clientOrderId,
        ticker,
        "TQBR",
        side,
        OrderType.LIMIT,
        TimeInForce.DAY,
        quantity,
        new BigDecimal(price));
  }

  private static StubMarketDataProperties.InstrumentDefinition sberDefinition() {
    return new StubMarketDataProperties.InstrumentDefinition(
        "SBER",
        "TQBR",
        "STOCK",
        "Сбербанк",
        "Сбербанк",
        "RU0009029540",
        new BigDecimal("10"),
        new BigDecimal("0.01"),
        "RUB",
        new BigDecimal("285.50"));
  }

  private static StubMarketDataProperties.InstrumentDefinition gazpDefinition() {
    return new StubMarketDataProperties.InstrumentDefinition(
        "GAZP",
        "TQBR",
        "STOCK",
        "Газпром",
        "Газпром",
        "RU0007661625",
        new BigDecimal("10"),
        new BigDecimal("0.01"),
        "RUB",
        new BigDecimal("165.20"));
  }
}
