package ru.moex.papertrader.infra.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.moex.papertrader.core.domain.Instrument;
import ru.moex.papertrader.core.domain.PageResult;
import ru.moex.papertrader.core.domain.Side;
import ru.moex.papertrader.core.domain.Trade;
import ru.moex.papertrader.core.domain.TradeSearchCriteria;
import ru.moex.papertrader.infra.mapper.InstrumentMapper;
import ru.moex.papertrader.infra.model.FillEntity;
import ru.moex.papertrader.infra.model.InstrumentEntity;
import ru.moex.papertrader.infra.model.OrderEntity;
import ru.moex.papertrader.infra.repository.FillRepository;
import ru.moex.papertrader.infra.repository.InstrumentRepository;
import ru.moex.papertrader.infra.repository.OrderRepository;
import ru.moex.papertrader.infra.services.exception.InvalidRequestException;

/** Выборка сделок счёта из PostgreSQL для {@code POST /v1/trades/search}. */
@ExtendWith(MockitoExtension.class)
class JpaTradeServiceImplTest {

  private static final String ACCOUNT = "IT-TRADES";
  private static final Instant FIRST = Instant.parse("2026-09-01T10:00:00Z");
  private static final Instant SECOND = Instant.parse("2026-09-02T11:00:00Z");

  @Mock private FillRepository fills;
  @Mock private OrderRepository orders;
  @Mock private InstrumentRepository instruments;
  @Mock private InstrumentMapper instrumentMapper;

  private JpaTradeServiceImpl service;

  @BeforeEach
  void setUp() {
    service =
        new JpaTradeServiceImpl(
            new PortfolioProperties(ACCOUNT, new BigDecimal("1000000.00"), "RUB", false),
            fills,
            orders,
            instruments,
            instrumentMapper);
  }

  @Test
  void emptyFillsGiveEmptyPage() {
    when(fills.findByAccountIdOrderByExecutedAtAscIdAsc(ACCOUNT)).thenReturn(List.of());

    PageResult<Trade> result = service.search(criteria(), 0, 50, List.of());

    assertThat(result.items()).isEmpty();
    assertThat(result.totalRecords()).isZero();
    assertThat(result.totalPages()).isZero();
  }

  @Test
  void mapsFillsToTradesWithOrderAndInstrument() {
    FillEntity fill = fill(10L, "EX-1", 100L, "285.50", 500, FIRST, "143.21");
    stubFill(fill, order(7L, Side.BUY, "CLIENT-1"), instrument(100L, "SBER", "TQBR"));
    when(fills.findByAccountIdOrderByExecutedAtAscIdAsc(ACCOUNT)).thenReturn(List.of(fill));

    PageResult<Trade> result = service.search(criteria(), 0, 50, List.of());

    assertThat(result.items())
        .singleElement()
        .satisfies(
            trade -> {
              assertThat(trade.tradeNum()).isEqualTo(10L);
              assertThat(trade.orderNum()).isEqualTo(7L);
              assertThat(trade.clientCode()).isEqualTo("CLIENT-1");
              assertThat(trade.instrument().ticker()).isEqualTo("SBER");
              assertThat(trade.side()).isEqualTo(Side.BUY);
              assertThat(trade.price()).isEqualByComparingTo("285.50");
              assertThat(trade.quantity()).isEqualTo(500);
              assertThat(trade.commission()).isEqualByComparingTo("143.21");
              assertThat(trade.executedAt()).isEqualTo(FIRST);
            });
  }

  @Test
  void filtersByCriteriaAndSortsDescending() {
    FillEntity first = fill(1L, "EX-1", 100L, "285.50", 500, FIRST, "143.21");
    FillEntity second = fill(2L, "EX-2", 200L, "165.20", 300, SECOND, "49.56");
    stubFill(first, order(1L, Side.BUY, null), instrument(100L, "SBER", "TQBR"));
    stubFill(second, order(2L, Side.SELL, null), instrument(200L, "GAZP", "TQBR"));
    when(fills.findByAccountIdOrderByExecutedAtAscIdAsc(ACCOUNT))
        .thenReturn(List.of(first, second));

    PageResult<Trade> result =
        service.search(
            new TradeSearchCriteria(null, null, Side.SELL, null, null, null, null),
            0,
            50,
            List.of("ticker,desc"));

    assertThat(result.items())
        .singleElement()
        .satisfies(trade -> assertThat(trade.instrument().ticker()).isEqualTo("GAZP"));
    assertThat(result.totalRecords()).isEqualTo(1);
  }

  @Test
  void paginatesInsteadOfReturningEverything() {
    FillEntity first = fill(1L, "EX-1", 100L, "285.50", 500, FIRST, "143.21");
    FillEntity second = fill(2L, "EX-2", 100L, "286.00", 500, SECOND, "143.50");
    stubFill(first, order(1L, Side.BUY, null), instrument(100L, "SBER", "TQBR"));
    stubFill(second, order(2L, Side.SELL, null), instrument(100L, "SBER", "TQBR"));
    when(fills.findByAccountIdOrderByExecutedAtAscIdAsc(ACCOUNT))
        .thenReturn(List.of(first, second));

    PageResult<Trade> pageTwo = service.search(criteria(), 1, 1, List.of("tradeDateTime,asc"));

    assertThat(pageTwo.items())
        .singleElement()
        .satisfies(trade -> assertThat(trade.executedAt()).isEqualTo(SECOND));
    assertThat(pageTwo.totalRecords()).isEqualTo(2);
    assertThat(pageTwo.totalPages()).isEqualTo(2);
  }

  @Test
  void skipsFillsWithoutOrderOrInstrument() {
    FillEntity orphan = fill(3L, "EX-3", 100L, "285.50", 500, FIRST, "143.21");
    when(fills.findByAccountIdOrderByExecutedAtAscIdAsc(ACCOUNT)).thenReturn(List.of(orphan));
    when(orders.findById("EX-3")).thenReturn(Optional.empty());

    PageResult<Trade> result = service.search(criteria(), 0, 50, List.of());

    assertThat(result.items()).isEmpty();
  }

  @Test
  void rejectsUnsupportedSortFields() {
    FillEntity fill = fill(1L, "EX-1", 100L, "285.50", 500, FIRST, "143.21");
    stubFill(fill, order(1L, Side.BUY, null), instrument(100L, "SBER", "TQBR"));
    when(fills.findByAccountIdOrderByExecutedAtAscIdAsc(ACCOUNT)).thenReturn(List.of(fill));

    assertThatThrownBy(() -> service.search(criteria(), 0, 50, List.of("price,asc")))
        .isInstanceOf(InvalidRequestException.class);
  }

  @Test
  void registerIsANoOpBecauseFillsAreWrittenByFillService() {
    Trade trade =
        new Trade(
            1L,
            1L,
            null,
            instrument(100L, "SBER", "TQBR"),
            Side.BUY,
            new BigDecimal("285.50"),
            500,
            new BigDecimal("143.21"),
            FIRST);

    assertThat(service.register(trade)).isSameAs(trade);
  }

  private TradeSearchCriteria criteria() {
    return new TradeSearchCriteria(null, null, null, null, null, null, null);
  }

  private void stubFill(FillEntity fill, OrderEntity order, Instrument instrument) {
    when(orders.findById(fill.getOrderId())).thenReturn(Optional.of(order));
    InstrumentEntity entity = mock(InstrumentEntity.class);
    lenient().when(instruments.findById(fill.getInstrumentId())).thenReturn(Optional.of(entity));
    lenient().when(instrumentMapper.toDomain(any(InstrumentEntity.class))).thenReturn(instrument);
  }

  private static FillEntity fill(
      long id,
      String orderId,
      long instrumentId,
      String price,
      long quantity,
      Instant executedAt,
      String commission) {
    FillEntity fill = mock(FillEntity.class);
    lenient().when(fill.getId()).thenReturn(id);
    lenient().when(fill.getOrderId()).thenReturn(orderId);
    lenient().when(fill.getInstrumentId()).thenReturn(instrumentId);
    lenient().when(fill.getPrice()).thenReturn(new BigDecimal(price));
    lenient().when(fill.getQuantity()).thenReturn(quantity);
    lenient().when(fill.getExecutedAt()).thenReturn(executedAt);
    lenient().when(fill.getCommission()).thenReturn(new BigDecimal(commission));
    return fill;
  }

  private static OrderEntity order(long orderNum, Side side, String clientCode) {
    OrderEntity order = mock(OrderEntity.class);
    when(order.getOrderNum()).thenReturn(orderNum);
    when(order.getSide()).thenReturn(side);
    when(order.getClientCode()).thenReturn(clientCode);
    return order;
  }

  private static Instrument instrument(long id, String ticker, String classCode) {
    return new Instrument(
        ticker,
        classCode,
        "STOCK",
        ticker,
        ticker,
        "",
        new BigDecimal("10"),
        new BigDecimal("0.01"),
        "RUB");
  }
}
