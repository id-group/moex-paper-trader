package ru.moex.papertrader.infra.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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
import ru.moex.papertrader.core.marketdata.Clock;
import ru.moex.papertrader.core.marketdata.MarketDataProvider;
import ru.moex.papertrader.core.portfolio.Money;
import ru.moex.papertrader.core.portfolio.PortfolioPosition;
import ru.moex.papertrader.core.portfolio.VirtualPortfolio;
import ru.moex.papertrader.infra.mapper.FillMapper;
import ru.moex.papertrader.infra.mapper.InstrumentMapper;
import ru.moex.papertrader.infra.mapper.OrderMapper;
import ru.moex.papertrader.infra.mapper.VirtualPortfolioMapper;
import ru.moex.papertrader.infra.model.FillEntity;
import ru.moex.papertrader.infra.model.InstrumentEntity;
import ru.moex.papertrader.infra.model.OrderEntity;
import ru.moex.papertrader.infra.repository.AccountBalanceRepository;
import ru.moex.papertrader.infra.repository.FillRepository;
import ru.moex.papertrader.infra.repository.InstrumentRepository;
import ru.moex.papertrader.infra.repository.OrderRepository;
import ru.moex.papertrader.infra.repository.PositionRepository;

/**
 * Исполнение акционной покупки: флаг маржинального плеча на сутки меняет проверку достаточности
 * средств с «свободный кэш» на «кэш + стоимость удерживаемых бумаг».
 */
@ExtendWith(MockitoExtension.class)
class FillServiceImplTest {

  private static final String ACCOUNT = "PAPER-0001";
  private static final String ORDER_ID = "ORD-1";
  private static final Instant NOW = Instant.parse("2026-11-16T10:15:30Z");
  private static final BigDecimal ZERO = new BigDecimal("0.00");

  @Mock private Clock clock;
  @Mock private MarketDataProvider marketDataProvider;
  @Mock private AccountService accountService;
  @Mock private OrderService orderService;
  @Mock private OrderRepository orders;
  @Mock private FillRepository fills;
  @Mock private PositionRepository positions;
  @Mock private AccountBalanceRepository balances;
  @Mock private InstrumentRepository instruments;
  @Mock private OrderMapper orderMapper;
  @Mock private FillMapper fillMapper;
  @Mock private InstrumentMapper instrumentMapper;
  @Mock private VirtualPortfolioMapper virtualPortfolioMapper;
  @Mock private OrderLocksCalculator orderLocksCalculator;
  @Mock private OrderEventRecorder eventRecorder;

  @BeforeEach
  void setUp() {
    InstrumentEntity sberEntity = instrumentEntity(1L);
    lenient().when(clock.instant()).thenReturn(NOW);
    lenient()
        .when(instruments.findByTickerAndClassCode("SBER", "TQBR"))
        .thenReturn(Optional.of(sberEntity));
    lenient().when(balances.findByAccountId(ACCOUNT)).thenReturn(List.of());
    lenient().when(positions.findByAccountId(ACCOUNT)).thenReturn(List.of());
    lenient()
        .when(orderLocksCalculator.calculate(any(), anyString()))
        .thenReturn(new OrderLocksCalculator.OrderLocks(Money.ZERO, Money.ZERO));
    lenient().when(orderService.activeOrders()).thenReturn(List.of());
  }

  @Test
  void buyIsRejectedWithoutMarginFlagEvenWithSufficientPortfolio() {
    // Портфель достаточен по обеспечению, но флаг выключен: прежнее поведение — полный отказ.
    Order order = buyOrder(10);
    when(orderService.get(OrderIdType.EXCHANGE, ORDER_ID)).thenReturn(order);
    VirtualPortfolio portfolio = portfolioWithCollateral();
    when(virtualPortfolioMapper.toDomainAllowNegativeCash(any(), any(), any()))
        .thenReturn(portfolio);

    FillServiceImpl service = service(false);

    assertThatThrownBy(
            () -> service.fill(OrderIdType.EXCHANGE, ORDER_ID, 10, new BigDecimal("20000.00")))
        .isInstanceOf(ru.moex.papertrader.core.domain.InvalidOrderException.class)
        .hasMessageContaining("Недостаточно свободных средств");

    verify(orders, never()).saveAndFlush(any());
    verify(balances, never()).updateBalances(anyString(), anyString(), any(), any(), any());
  }

  @Test
  void buyGoesIntoDebitWithinCollateralWhenMarginFlagEnabled() {
    // Флаг включён: покупка до зачисления T+1-выручки принята, кэш уходит в минус в пределах
    // стоимости удерживаемых бумаг (10 000 SBER × 100 = 1 000 000 RUB обеспечения).
    Order order = buyOrder(10);
    when(orderService.get(OrderIdType.EXCHANGE, ORDER_ID)).thenReturn(order);
    VirtualPortfolio portfolio = portfolioWithCollateral();
    when(virtualPortfolioMapper.toDomainAllowNegativeCash(any(), any(), any()))
        .thenReturn(portfolio);
    stubPersistence();

    FillServiceImpl service = service(true);
    Order result = service.fill(OrderIdType.EXCHANGE, ORDER_ID, 10, new BigDecimal("20000.00"));

    assertThat(result.status()).isEqualTo(OrderStatus.FILLED);
    assertThat(result.executedQuantity()).isEqualTo(10);
    // Требуемая сумма 10 × 20000 + комиссия 0.1% (200.00) = 200200.00; свободного кэша 0.
    ArgumentCaptor<BigDecimal> cash = ArgumentCaptor.forClass(BigDecimal.class);
    verify(balances)
        .updateBalances(eq(ACCOUNT), eq("RUB"), cash.capture(), eq(Money.ZERO), eq(NOW));
    assertThat(cash.getValue()).isEqualByComparingTo("-200200.00");
  }

  @Test
  void buyIsRejectedWithMarginFlagWhenPortfolioIsInsufficient() {
    // Флаг включён, но дебет не покрывает обеспечение: отказ сохраняется в обоих режимах.
    Order order = buyOrder(10);
    when(orderService.get(OrderIdType.EXCHANGE, ORDER_ID)).thenReturn(order);
    VirtualPortfolio portfolio = new VirtualPortfolio(new BigDecimal("1000.00"));
    when(virtualPortfolioMapper.toDomainAllowNegativeCash(any(), any(), any()))
        .thenReturn(portfolio);

    FillServiceImpl service = service(true);

    assertThatThrownBy(
            () -> service.fill(OrderIdType.EXCHANGE, ORDER_ID, 10, new BigDecimal("20000.00")))
        .isInstanceOf(ru.moex.papertrader.core.domain.InvalidOrderException.class)
        .hasMessageContaining("Недостаточно маржинального обеспечения");

    verify(orders, never()).saveAndFlush(any());
  }

  @Test
  void marginBuyCountsLockedAndOtherOrderLocksOnce() {
    // Балансовая блокировка и блокировки других заявок вычитаются из доступных средств ровно один
    // раз: дебет растёт на их сумму, но при покрытии обеспечением сделка проходит.
    Order order = buyOrder(10);
    when(orderService.get(OrderIdType.EXCHANGE, ORDER_ID)).thenReturn(order);
    // Кэш 0, балансовая блокировка 50000.00, обеспечение — 10000 SBER по средней 100.00.
    VirtualPortfolio portfolio =
        VirtualPortfolio.restoreForSettlement(
            Map.of("RUB", new BigDecimal("0.00")),
            Map.of("RUB", new BigDecimal("50000.00")),
            List.of(
                new PortfolioPosition(TestInstruments.sber(), 10000, new BigDecimal("100.00"))));
    when(virtualPortfolioMapper.toDomainAllowNegativeCash(any(), any(), any()))
        .thenReturn(portfolio);
    when(orderLocksCalculator.calculate(any(), anyString()))
        .thenReturn(new OrderLocksCalculator.OrderLocks(new BigDecimal("100000.00"), Money.ZERO));
    stubPersistence();

    FillServiceImpl service = service(true);
    Order result = service.fill(OrderIdType.EXCHANGE, ORDER_ID, 10, new BigDecimal("20000.00"));

    assertThat(result.status()).isEqualTo(OrderStatus.FILLED);
    // Доступно 0 − 50000 − 100000; дебет 350200.00 покрыт обеспечением 1 000 000.00.
    // Списано ровно стоимость сделки: кэш −200200.00, блокировка 50000.00 без изменений.
    verify(balances)
        .updateBalances(
            eq(ACCOUNT),
            eq("RUB"),
            eq(new BigDecimal("-200200.00")),
            eq(new BigDecimal("50000.00")),
            eq(NOW));
  }

  @Test
  void sellWithoutPositionIsRejectedInBothModes() {
    // Продажа без позиции отклоняется независимо от флага плеча — плечо действует только на
    // покупки.
    for (boolean allowStockMarginBuy : new boolean[] {false, true}) {
      Order order = sellOrder(10);
      when(orderService.get(OrderIdType.EXCHANGE, ORDER_ID)).thenReturn(order);
      VirtualPortfolio portfolio = new VirtualPortfolio(new BigDecimal("1000000.00"));
      when(virtualPortfolioMapper.toDomainAllowNegativeCash(any(), any(), any()))
          .thenReturn(portfolio);

      FillServiceImpl service = service(allowStockMarginBuy);

      assertThatThrownBy(
              () -> service.fill(OrderIdType.EXCHANGE, ORDER_ID, 10, new BigDecimal("200.00")))
          .isInstanceOf(ru.moex.papertrader.core.domain.InvalidOrderException.class)
          .hasMessageContaining("в позиции только 0");

      verify(orders, never()).saveAndFlush(any());
    }
  }

  @Test
  void marginFlagDoesNotAffectFuturesBuy() {
    // Плечо действует только на акции: покупка фьючерса при нехватке кэша отклоняется по ГО.
    InstrumentEntity siu6Entity = instrumentEntity(2L);
    when(instruments.findByTickerAndClassCode("SiU6", "SPBFUT"))
        .thenAnswer(invocation -> Optional.of(siu6Entity));
    Order order = futuresBuyOrder(1);
    when(orderService.get(OrderIdType.EXCHANGE, ORDER_ID)).thenReturn(order);
    VirtualPortfolio portfolio = new VirtualPortfolio(new BigDecimal("1000.00"));
    when(virtualPortfolioMapper.toDomainAllowNegativeCash(any(), any(), any()))
        .thenReturn(portfolio);

    FillServiceImpl service = service(true);

    assertThatThrownBy(
            () -> service.fill(OrderIdType.EXCHANGE, ORDER_ID, 1, new BigDecimal("90000.00")))
        .isInstanceOf(ru.moex.papertrader.core.domain.InvalidOrderException.class)
        .hasMessageContaining("Недостаточно обеспечения");

    verify(orders, never()).saveAndFlush(any());
  }

  /** Портфель без свободного кэша, но с акционным обеспечением 1 000 000 RUB. */
  private static VirtualPortfolio portfolioWithCollateral() {
    VirtualPortfolio portfolio = new VirtualPortfolio(new BigDecimal("1000000.00"));
    portfolio.buy(TestInstruments.sber(), 10000, new BigDecimal("100.00"), ZERO, NOW);
    return portfolio;
  }

  private FillServiceImpl service(boolean allowStockMarginBuy) {
    PortfolioProperties properties =
        new PortfolioProperties(ACCOUNT, new BigDecimal("1000000.00"), "RUB", allowStockMarginBuy);
    return new FillServiceImpl(
        clock,
        marketDataProvider,
        properties,
        accountService,
        orderService,
        orders,
        fills,
        positions,
        balances,
        instruments,
        orderMapper,
        fillMapper,
        instrumentMapper,
        virtualPortfolioMapper,
        orderLocksCalculator,
        eventRecorder);
  }

  /** Заявка BUY LIMIT 10 шт. по SBER. */
  private static Order buyOrder(long quantity) {
    return Order.accept(
        new NewOrder(
            UUID.randomUUID(),
            "SBER",
            "TQBR",
            Side.BUY,
            OrderType.LIMIT,
            TimeInForce.DAY,
            quantity,
            new BigDecimal("20000.00")),
        TestInstruments.sber(),
        ORDER_ID,
        1,
        ACCOUNT,
        NOW);
  }

  /** Заявка SELL LIMIT 10 шт. по SBER. */
  private static Order sellOrder(long quantity) {
    return Order.accept(
        new NewOrder(
            UUID.randomUUID(),
            "SBER",
            "TQBR",
            Side.SELL,
            OrderType.LIMIT,
            TimeInForce.DAY,
            quantity,
            new BigDecimal("200.00")),
        TestInstruments.sber(),
        ORDER_ID,
        1,
        ACCOUNT,
        NOW);
  }

  /** Заявка BUY LIMIT по фьючерсу SiU6. */
  private static Order futuresBuyOrder(long quantity) {
    return Order.accept(
        new NewOrder(
            UUID.randomUUID(),
            "SiU6",
            "SPBFUT",
            Side.BUY,
            OrderType.LIMIT,
            TimeInForce.DAY,
            quantity,
            new BigDecimal("90000.00")),
        TestInstruments.siu6(),
        ORDER_ID,
        1,
        ACCOUNT,
        NOW);
  }

  private void stubPersistence() {
    when(orderMapper.toEntity(any(), eq(ACCOUNT), eq(1L))).thenReturn(mock(OrderEntity.class));
    when(fillMapper.toEntity(any(), anyString(), eq(ACCOUNT), eq(1L), any()))
        .thenReturn(mock(FillEntity.class));
    when(positions.upsert(eq(ACCOUNT), eq(1L), anyLong(), any(), any())).thenReturn(1);
    when(balances.updateBalances(eq(ACCOUNT), eq("RUB"), any(), any(), any())).thenReturn(1);
  }

  private static InstrumentEntity instrumentEntity(Long id) {
    InstrumentEntity entity = mock(InstrumentEntity.class);
    // lenient: флаг strict-stubs валил бы тесты, где инструмент заявки не этот тикер.
    lenient().when(entity.getId()).thenReturn(id);
    return entity;
  }
}
