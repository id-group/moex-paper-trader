package ru.moex.papertrader.infra.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;
import ru.moex.papertrader.PostgresContainerSupport;
import ru.moex.papertrader.core.domain.InvalidOrderException;
import ru.moex.papertrader.core.domain.NewOrder;
import ru.moex.papertrader.core.domain.Order;
import ru.moex.papertrader.core.domain.OrderIdType;
import ru.moex.papertrader.core.domain.OrderSearchCriteria;
import ru.moex.papertrader.core.domain.OrderStatus;
import ru.moex.papertrader.core.domain.OrderType;
import ru.moex.papertrader.core.domain.PageResult;
import ru.moex.papertrader.core.domain.Side;
import ru.moex.papertrader.core.domain.TimeInForce;
import ru.moex.papertrader.infra.model.InstrumentEntity;
import ru.moex.papertrader.infra.repository.InstrumentRepository;
import ru.moex.papertrader.infra.repository.OrderRepository;
import ru.moex.papertrader.infra.services.exception.ConflictException;
import ru.moex.papertrader.infra.services.exception.InvalidRequestException;
import ru.moex.papertrader.infra.services.exception.ResourceNotFoundException;

/**
 * Заявки в PostgreSQL: нумерация, поиск, отмена и инструменты.
 *
 * <p>Тест транзакционный: каждый метод откатывается, поэтому данные не попадают в общую БД прогона.
 * Номера заявок проверяются относительно текущего максимума, а не абсолютным значением.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Transactional
@Import(FixedClockTestConfiguration.class)
class JpaOrderServiceImplIT extends PostgresContainerSupport {

  // SiU6/SPBFUT есть в стабе application.yml, но его не использует ни один другой тест:
  // в БД он появляется только здесь и откатывается вместе с транзакцией метода.
  private static final String TEST_TICKER = "SiU6";
  private static final String TEST_CLASS_CODE = "SPBFUT";
  private static final long ORDER_ID_SEQUENCE_BASE = 796_285_400_000L;

  @Autowired private JpaOrderServiceImpl service;
  @Autowired private OrderRepository orders;
  @Autowired private InstrumentRepository instruments;
  @Autowired private EntityManager entityManager;

  @Test
  void placePersistsOrdersWithSequentialNumbers() {
    long baseline = orders.findMaxOrderNum().orElse(0L);

    Order first = service.place(limitOrder("SBER", Side.BUY, "285.5", 10));
    Order second = service.place(limitOrder("SBER", Side.BUY, "286.5", 10));

    assertThat(first.orderNum()).isEqualTo(baseline + 1);
    assertThat(second.orderNum()).isEqualTo(baseline + 2);
    assertThat(first.orderId())
        .isEqualTo("20261102-TQBR-" + (ORDER_ID_SEQUENCE_BASE + baseline + 1));

    flushAndClear();
    assertThat(orders.existsById(first.orderId())).isTrue();
    assertThat(orders.existsById(second.orderId())).isTrue();
  }

  @Test
  void readsOrderByClientAndExchangeIdentifiers() {
    Order placed = service.place(limitOrder("SBER", Side.BUY, "285.5", 10));
    flushAndClear();

    Order byClient = service.get(OrderIdType.CLIENT, placed.clientOrderId().toString());
    Order byExchange = service.get(OrderIdType.EXCHANGE, placed.orderId());

    assertThat(byClient.orderId()).isEqualTo(placed.orderId());
    assertThat(byExchange.clientOrderId()).isEqualTo(placed.clientOrderId());
    assertThat(byExchange.orderNum()).isEqualTo(placed.orderNum());
    assertThat(byExchange.instrument().ticker()).isEqualTo("SBER");
    assertThat(byExchange.instrument().classCode()).isEqualTo("TQBR");
    assertThat(byExchange.instrument().lotSize()).isEqualByComparingTo("10");
    assertThat(byExchange.side()).isEqualTo(Side.BUY);
    assertThat(byExchange.orderType()).isEqualTo(OrderType.LIMIT);
    assertThat(byExchange.timeInForce()).isEqualTo(TimeInForce.DAY);
    assertThat(byExchange.quantity()).isEqualTo(10);
    assertThat(byExchange.price()).isEqualByComparingTo("285.5");
    // Масштаб NUMERIC(19,6) не должен протекать в домен: в JSON БКС снова "285.5".
    assertThat(byExchange.price().toPlainString()).isEqualTo("285.5");
    assertThat(byExchange.status()).isEqualTo(OrderStatus.NEW);
    assertThat(byExchange.createdAt()).isEqualTo(FixedClockTestConfiguration.NOW);
    assertThat(byExchange.updatedAt()).isEqualTo(FixedClockTestConfiguration.NOW);
  }

  @Test
  void restoresMarketOrderWithoutPrice() {
    Order placed = service.place(marketOrder("GAZP", Side.SELL, 5));
    flushAndClear();

    Order loaded = service.get(OrderIdType.EXCHANGE, placed.orderId());

    assertThat(loaded.orderType()).isEqualTo(OrderType.MARKET);
    assertThat(loaded.price()).isNull();
  }

  @Test
  void searchFiltersSortsAndPaginates() {
    Order sber = service.place(limitOrder("SBER", Side.BUY, "285.5", 10));
    Order gazp = service.place(limitOrder("GAZP", Side.BUY, "165.2", 5));
    Order lkoh = service.place(limitOrder("LKOH", Side.BUY, "7100", 1));
    flushAndClear();

    OrderSearchCriteria criteria =
        new OrderSearchCriteria(
            null,
            null,
            null,
            null,
            null,
            Set.of(sber.orderNum(), gazp.orderNum(), lkoh.orderNum()),
            null,
            null);

    PageResult<Order> firstPage = service.search(criteria, 0, 2, List.of("ticker,asc"));
    PageResult<Order> secondPage = service.search(criteria, 1, 2, List.of("ticker,asc"));

    assertThat(firstPage.totalRecords()).isEqualTo(3);
    assertThat(firstPage.totalPages()).isEqualTo(2);
    assertThat(firstPage.items())
        .extracting(Order::orderNum)
        .containsExactly(gazp.orderNum(), lkoh.orderNum());
    assertThat(secondPage.items()).extracting(Order::orderNum).containsExactly(sber.orderNum());
  }

  @Test
  void cancelPersistsCanceledStatus() {
    Order placed = service.place(limitOrder("SBER", Side.BUY, "285.5", 10));
    flushAndClear();

    Order canceled = service.cancel(OrderIdType.CLIENT, placed.clientOrderId().toString());

    assertThat(canceled.status()).isEqualTo(OrderStatus.CANCELED);
    flushAndClear();
    assertThat(service.get(OrderIdType.EXCHANGE, placed.orderId()).status())
        .isEqualTo(OrderStatus.CANCELED);
  }

  @Test
  void repeatedCancelIsRejected() {
    Order placed = service.place(limitOrder("SBER", Side.BUY, "285.5", 10));
    flushAndClear();
    service.cancel(OrderIdType.CLIENT, placed.clientOrderId().toString());
    flushAndClear();

    assertThatThrownBy(() -> service.cancel(OrderIdType.CLIENT, placed.clientOrderId().toString()))
        .isInstanceOf(InvalidOrderException.class);
  }

  @Test
  void duplicateClientOrderIdIsRejected() {
    UUID clientOrderId = UUID.randomUUID();
    service.place(limitOrder(clientOrderId, "SBER", Side.BUY, "285.5", 10));
    flushAndClear();

    assertThatThrownBy(
            () -> service.place(limitOrder(clientOrderId, "SBER", Side.BUY, "285.5", 10)))
        .isInstanceOf(ConflictException.class)
        .hasMessageContaining(clientOrderId.toString());
  }

  @Test
  void activeOrdersContainOnlyActiveOrders() {
    Order active = service.place(limitOrder("SBER", Side.BUY, "285.5", 10));
    Order canceled = service.place(limitOrder("GAZP", Side.BUY, "165.2", 5));
    flushAndClear();
    service.cancel(OrderIdType.CLIENT, canceled.clientOrderId().toString());
    flushAndClear();

    List<Order> activeOrders = service.activeOrders();

    assertThat(activeOrders).allMatch(order -> order.status().isActive());
    assertThat(activeOrders).extracting(Order::orderId).contains(active.orderId());
    assertThat(activeOrders).extracting(Order::orderId).doesNotContain(canceled.orderId());
    assertThat(activeOrders).isSortedAccordingTo(Comparator.comparingLong(Order::orderNum));
  }

  @Test
  void createsMissingInstrumentAndReusesIt() {
    assertThat(instruments.findByTickerAndClassCode(TEST_TICKER, TEST_CLASS_CODE)).isEmpty();

    service.place(limitOrder(TEST_TICKER, TEST_CLASS_CODE, Side.BUY, "100", 1));
    flushAndClear();
    InstrumentEntity created =
        instruments.findByTickerAndClassCode(TEST_TICKER, TEST_CLASS_CODE).orElseThrow();

    service.place(limitOrder(TEST_TICKER, TEST_CLASS_CODE, Side.BUY, "100", 1));
    flushAndClear();
    InstrumentEntity reused =
        instruments.findByTickerAndClassCode(TEST_TICKER, TEST_CLASS_CODE).orElseThrow();

    assertThat(reused.getId()).isEqualTo(created.getId());
    assertThat(instruments.findAll().stream().filter(row -> TEST_TICKER.equals(row.getTicker())))
        .hasSize(1);
  }

  @Test
  void unknownInstrumentIsRejected() {
    assertThatThrownBy(() -> service.place(limitOrder("NOPE", "TQBR", Side.BUY, "1", 1)))
        .isInstanceOf(ResourceNotFoundException.class)
        .hasMessage("Инструмент NOPE/TQBR не найден");
  }

  @Test
  void missingClientOrderIdIsRejected() {
    NewOrder request =
        new NewOrder(
            null,
            "SBER",
            "TQBR",
            Side.BUY,
            OrderType.LIMIT,
            TimeInForce.DAY,
            10,
            new BigDecimal("285.5"));

    assertThatThrownBy(() -> service.place(request))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("clientOrderId обязателен");
  }

  @Test
  void unknownOrderIsRejected() {
    assertThatThrownBy(() -> service.get(OrderIdType.CLIENT, UUID.randomUUID().toString()))
        .isInstanceOf(ResourceNotFoundException.class);
    assertThatThrownBy(() -> service.get(OrderIdType.EXCHANGE, "IT-UNKNOWN-ORDER"))
        .isInstanceOf(ResourceNotFoundException.class);
  }

  @Test
  void malformedClientOrderIdIsRejected() {
    assertThatThrownBy(() -> service.get(OrderIdType.CLIENT, "not-a-uuid"))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("orderId должен быть UUID при orderIdType=1");
  }

  private void flushAndClear() {
    entityManager.flush();
    entityManager.clear();
  }

  private static NewOrder limitOrder(String ticker, Side side, String price, long quantity) {
    return limitOrder(UUID.randomUUID(), ticker, "TQBR", side, price, quantity);
  }

  private static NewOrder limitOrder(
      String ticker, String classCode, Side side, String price, long quantity) {
    return limitOrder(UUID.randomUUID(), ticker, classCode, side, price, quantity);
  }

  private static NewOrder limitOrder(
      UUID clientOrderId, String ticker, Side side, String price, long quantity) {
    return limitOrder(clientOrderId, ticker, "TQBR", side, price, quantity);
  }

  private static NewOrder limitOrder(
      UUID clientOrderId, String ticker, String classCode, Side side, String price, long quantity) {
    return new NewOrder(
        clientOrderId,
        ticker,
        classCode,
        side,
        OrderType.LIMIT,
        TimeInForce.DAY,
        quantity,
        new BigDecimal(price));
  }

  private static NewOrder marketOrder(String ticker, Side side, long quantity) {
    return new NewOrder(
        UUID.randomUUID(), ticker, "TQBR", side, OrderType.MARKET, TimeInForce.DAY, quantity, null);
  }
}
