package ru.moex.papertrader.infra.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;
import ru.moex.papertrader.PostgresContainerSupport;
import ru.moex.papertrader.core.domain.InvalidOrderException;
import ru.moex.papertrader.core.domain.NewOrder;
import ru.moex.papertrader.core.domain.Order;
import ru.moex.papertrader.core.domain.OrderIdType;
import ru.moex.papertrader.core.domain.OrderStatus;
import ru.moex.papertrader.core.domain.OrderType;
import ru.moex.papertrader.core.domain.PortfolioSnapshot;
import ru.moex.papertrader.core.domain.Side;
import ru.moex.papertrader.core.domain.TimeInForce;
import ru.moex.papertrader.infra.model.FillEntity;
import ru.moex.papertrader.infra.model.InstrumentEntity;
import ru.moex.papertrader.infra.model.PositionEntity;
import ru.moex.papertrader.infra.repository.AccountBalanceRepository;
import ru.moex.papertrader.infra.repository.FillRepository;
import ru.moex.papertrader.infra.repository.InstrumentRepository;
import ru.moex.papertrader.infra.repository.PositionRepository;
import ru.moex.papertrader.infra.services.exception.InvalidRequestException;
import ru.moex.papertrader.infra.services.exception.ResourceNotFoundException;

/**
 * Исполнение акционных заявок внутри виртуального портфеля.
 *
 * <p>Тест работает на отдельном счёте {@value #ACCOUNT}, чтобы коммитящиеся заявки BKS-тестов не
 * влияли на блокировки и кэш. Транзакция метода откатывается, начальный баланс задаётся явно.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Transactional
@Import(FixedClockTestConfiguration.class)
@TestPropertySource(properties = "emulator.portfolio.account=IT-FILL-ACC-1")
class FillServiceIT extends PostgresContainerSupport {

  private static final String ACCOUNT = "IT-FILL-ACC-1";
  private static final String CURRENCY = "RUB";
  private static final String SBER = "SBER";
  private static final String TQBR = "TQBR";
  private static final String FUTURES_TICKER = "SiU6";
  private static final String FUTURES_CLASS_CODE = "SPBFUT";
  private static final Instant NOW = FixedClockTestConfiguration.NOW;

  @Autowired private FillService fillService;
  @Autowired private OrderService orderService;
  @Autowired private PortfolioService portfolioService;
  @Autowired private AccountService accountService;
  @Autowired private AccountBalanceRepository balances;
  @Autowired private PositionRepository positions;
  @Autowired private FillRepository fills;
  @Autowired private InstrumentRepository instruments;
  @Autowired private EntityManager entityManager;

  @BeforeEach
  void prepareAccount() {
    accountService.ensureAccount();
    setCash("100000");
  }

  @Test
  void partialBuyFillDebitsCashAndCommissionAndUpdatesPosition() {
    Order order = placeStock(Side.BUY, "100", 10);
    flushAndClear();
    PortfolioSnapshot lockedBefore = portfolioService.snapshot();

    Order filled =
        fillService.fill(OrderIdType.EXCHANGE, order.orderId(), 4, new BigDecimal("100"));
    flushAndClear();

    FillEntity storedFill = fills.findByOrderId(order.orderId()).getFirst();
    assertThat(storedFill.getQuantity()).isEqualTo(4);
    assertThat(storedFill.getPrice()).isEqualByComparingTo("100");
    assertThat(storedFill.getCommission()).isEqualByComparingTo("0.40");
    assertThat(storedFill.getExecutedAt()).isEqualTo(NOW);
    assertThat(filled.executedQuantity()).isEqualTo(4);

    assertThat(cash()).isEqualByComparingTo("99599.60");
    assertThat(position(SBER, TQBR)).isPresent();
    assertThat(position(SBER, TQBR).orElseThrow().getQuantity()).isEqualTo(4);
    assertThat(position(SBER, TQBR).orElseThrow().getAveragePrice()).isEqualByComparingTo("100");

    Order loaded = orderService.get(OrderIdType.EXCHANGE, order.orderId());
    assertThat(loaded.status()).isEqualTo(OrderStatus.PARTIALLY_FILLED);
    assertThat(loaded.executedQuantity()).isEqualTo(4);
    assertThat(fills.findByOrderId(order.orderId())).hasSize(1);

    // Исполненная часть больше не блокирует средства: 10 @100 -> 6 @100.
    PortfolioSnapshot lockedAfter = portfolioService.snapshot();
    assertThat(lockedAfter.lockedCash())
        .isEqualByComparingTo(lockedBefore.lockedCash().subtract(new BigDecimal("400")));
  }

  @Test
  void remainingFillCompletesOrderAndPosition() {
    Order order = placeStock(Side.BUY, "100", 10);
    fillService.fill(OrderIdType.EXCHANGE, order.orderId(), 4, new BigDecimal("100"));
    flushAndClear();

    Order filled =
        fillService.fill(OrderIdType.EXCHANGE, order.orderId(), 6, new BigDecimal("100"));
    flushAndClear();

    assertThat(filled.executedQuantity()).isEqualTo(10);
    assertThat(cash()).isEqualByComparingTo("98999.00");
    assertThat(position(SBER, TQBR).orElseThrow().getQuantity()).isEqualTo(10);
    assertThat(position(SBER, TQBR).orElseThrow().getAveragePrice()).isEqualByComparingTo("100");

    Order loaded = orderService.get(OrderIdType.EXCHANGE, order.orderId());
    assertThat(loaded.status()).isEqualTo(OrderStatus.FILLED);
    assertThat(loaded.executedQuantity()).isEqualTo(10);
    assertThat(loaded.averagePrice()).isEqualByComparingTo("100");
    assertThat(fills.findByOrderId(order.orderId())).hasSize(2);
  }

  @Test
  void sellCreditsCashMinusCommissionAndReducesPosition() {
    Order buy = placeStock(Side.BUY, "100", 10);
    fillService.fill(OrderIdType.EXCHANGE, buy.orderId(), 10, new BigDecimal("100"));
    flushAndClear();
    BigDecimal cashBeforeSell = cash();

    Order sell = placeStock(Side.SELL, "110", 3);
    flushAndClear();

    Order filled = fillService.fill(OrderIdType.EXCHANGE, sell.orderId(), 3, new BigDecimal("110"));
    flushAndClear();

    FillEntity storedFill = fills.findByOrderId(sell.orderId()).getFirst();
    assertThat(storedFill.getCommission()).isEqualByComparingTo("0.33");
    assertThat(filled.status()).isEqualTo(OrderStatus.FILLED);
    assertThat(cash()).isEqualByComparingTo(cashBeforeSell.add(new BigDecimal("329.67")));
    assertThat(position(SBER, TQBR).orElseThrow().getQuantity()).isEqualTo(7);
    assertThat(orderService.get(OrderIdType.EXCHANGE, sell.orderId()).status())
        .isEqualTo(OrderStatus.FILLED);
  }

  @Test
  void sellAbovePositionIsRejectedAndChangesNothing() {
    Order buy = placeStock(Side.BUY, "100", 7);
    fillService.fill(OrderIdType.EXCHANGE, buy.orderId(), 7, new BigDecimal("100"));
    flushAndClear();
    BigDecimal cashBefore = cash();

    Order sell = placeStock(Side.SELL, "110", 8);
    flushAndClear();

    assertThatThrownBy(
            () -> fillService.fill(OrderIdType.EXCHANGE, sell.orderId(), 8, new BigDecimal("110")))
        .isInstanceOf(InvalidOrderException.class);

    flushAndClear();
    assertThat(cash()).isEqualByComparingTo(cashBefore);
    assertThat(position(SBER, TQBR).orElseThrow().getQuantity()).isEqualTo(7);
    Order loaded = orderService.get(OrderIdType.EXCHANGE, sell.orderId());
    assertThat(loaded.status()).isEqualTo(OrderStatus.NEW);
    assertThat(loaded.executedQuantity()).isZero();
    assertThat(fills.findByOrderId(sell.orderId())).isEmpty();
  }

  @Test
  void buyWithoutEnoughCashIsRejectedAndChangesNothing() {
    setCash("100");
    Order buy = placeStock(Side.BUY, "100", 10);
    flushAndClear();

    assertThatThrownBy(
            () -> fillService.fill(OrderIdType.EXCHANGE, buy.orderId(), 10, new BigDecimal("100")))
        .isInstanceOf(InvalidOrderException.class);

    flushAndClear();
    assertThat(cash()).isEqualByComparingTo("100");
    assertThat(position(SBER, TQBR)).isEmpty();
    Order loaded = orderService.get(OrderIdType.EXCHANGE, buy.orderId());
    assertThat(loaded.status()).isEqualTo(OrderStatus.NEW);
    assertThat(loaded.executedQuantity()).isZero();
    assertThat(fills.findByOrderId(buy.orderId())).isEmpty();
  }

  @Test
  void lockOfAnotherActiveOrderIsCountedButOwnLockIsExcluded() {
    setCash("1001.00");
    Order own = placeStock(Side.BUY, "100", 10);
    Order other = placeStock(Side.BUY, "100", 10);
    flushAndClear();

    assertThatThrownBy(
            () -> fillService.fill(OrderIdType.EXCHANGE, own.orderId(), 10, new BigDecimal("100")))
        .isInstanceOf(InvalidOrderException.class);
    flushAndClear();
    assertThat(orderService.get(OrderIdType.EXCHANGE, own.orderId()).status())
        .isEqualTo(OrderStatus.NEW);
    assertThat(cash()).isEqualByComparingTo("1001.00");

    orderService.cancel(OrderIdType.EXCHANGE, other.orderId());
    flushAndClear();

    // Блокировка собственной исполняемой заявки (10 @100) не должна мешать: доступно 1001.00.
    fillService.fill(OrderIdType.EXCHANGE, own.orderId(), 10, new BigDecimal("100"));
    flushAndClear();

    assertThat(orderService.get(OrderIdType.EXCHANGE, own.orderId()).status())
        .isEqualTo(OrderStatus.FILLED);
    assertThat(cash()).isEqualByComparingTo("0.00");
    assertThat(position(SBER, TQBR).orElseThrow().getQuantity()).isEqualTo(10);
  }

  @Test
  void completedOrderCannotBeFilledAgain() {
    Order buy = placeStock(Side.BUY, "100", 1);
    fillService.fill(OrderIdType.EXCHANGE, buy.orderId(), 1, new BigDecimal("100"));
    flushAndClear();

    assertThatThrownBy(
            () -> fillService.fill(OrderIdType.EXCHANGE, buy.orderId(), 1, new BigDecimal("100")))
        .isInstanceOf(InvalidOrderException.class);
  }

  @Test
  void canceledOrderCannotBeFilled() {
    Order buy = placeStock(Side.BUY, "100", 1);
    flushAndClear();
    orderService.cancel(OrderIdType.EXCHANGE, buy.orderId());
    flushAndClear();

    assertThatThrownBy(
            () -> fillService.fill(OrderIdType.EXCHANGE, buy.orderId(), 1, new BigDecimal("100")))
        .isInstanceOf(InvalidOrderException.class);
  }

  @Test
  void nonPositiveFillQuantityIsRejected() {
    Order buy = placeStock(Side.BUY, "100", 2);
    flushAndClear();

    assertThatThrownBy(
            () -> fillService.fill(OrderIdType.EXCHANGE, buy.orderId(), 0, new BigDecimal("100")))
        .isInstanceOf(InvalidOrderException.class);
  }

  @Test
  void fillQuantityAboveRemainingIsRejected() {
    Order buy = placeStock(Side.BUY, "100", 2);
    flushAndClear();

    assertThatThrownBy(
            () -> fillService.fill(OrderIdType.EXCHANGE, buy.orderId(), 3, new BigDecimal("100")))
        .isInstanceOf(InvalidOrderException.class);
  }

  @Test
  void missingPriceUsesLastStubPrice() {
    Order buy = placeStock(Side.BUY, null, 10);
    flushAndClear();

    fillService.fill(OrderIdType.EXCHANGE, buy.orderId(), 10, null);
    flushAndClear();

    FillEntity stored = fills.findByOrderId(buy.orderId()).getFirst();
    assertThat(stored.getPrice()).isEqualByComparingTo("285.50");
    assertThat(stored.getCommission()).isEqualByComparingTo("2.86");
    assertThat(cash()).isEqualByComparingTo("97142.14");
    assertThat(position(SBER, TQBR).orElseThrow().getAveragePrice()).isEqualByComparingTo("285.50");
    assertThat(orderService.get(OrderIdType.EXCHANGE, buy.orderId()).status())
        .isEqualTo(OrderStatus.FILLED);
  }

  @Test
  void futuresOrderIsFilledAndRemainsSupported() {
    Order futures = placeFutures(Side.BUY, "90000", 1);
    flushAndClear();

    Order filled =
        fillService.fill(OrderIdType.EXCHANGE, futures.orderId(), 1, new BigDecimal("90000"));
    flushAndClear();

    assertThat(filled.status()).isEqualTo(OrderStatus.FILLED);
    assertThat(orderService.get(OrderIdType.EXCHANGE, futures.orderId()).status())
        .isEqualTo(OrderStatus.FILLED);
    assertThat(position(FUTURES_TICKER, FUTURES_CLASS_CODE).orElseThrow().getQuantity())
        .isEqualTo(1);
    assertThat(fills.findByOrderId(futures.orderId())).hasSize(1);
  }

  @Test
  void futuresBuyOpensLongLocksMarginAndChargesOnlyCommission() {
    Order futures = placeFutures(Side.BUY, "90000", 2);
    flushAndClear();
    PortfolioSnapshot beforeFill = portfolioService.snapshot();

    Order filled =
        fillService.fill(OrderIdType.EXCHANGE, futures.orderId(), 2, new BigDecimal("90000"));
    flushAndClear();

    FillEntity storedFill = fills.findByOrderId(futures.orderId()).getFirst();
    assertThat(storedFill.getCommission()).isEqualByComparingTo("2.00");
    assertThat(storedFill.getPrice()).isEqualByComparingTo("90000");
    assertThat(filled.status()).isEqualTo(OrderStatus.FILLED);

    // Стоимость контракта не списывается: кэш уменьшается только на сбор за контракт.
    assertThat(cash()).isEqualByComparingTo("99998.00");
    assertThat(locked()).isEqualByComparingTo("18000.00");
    assertThat(position(FUTURES_TICKER, FUTURES_CLASS_CODE).orElseThrow().getQuantity())
        .isEqualTo(2);
    assertThat(position(FUTURES_TICKER, FUTURES_CLASS_CODE).orElseThrow().getAveragePrice())
        .isEqualByComparingTo("90000");

    // ГО заявки и ГО позиции не удваиваются: блокировка под фьючерсы та же, что до исполнения.
    PortfolioSnapshot afterFill = portfolioService.snapshot();
    assertThat(beforeFill.lockedForFutures()).isEqualByComparingTo("18000.00");
    assertThat(afterFill.lockedForFutures()).isEqualByComparingTo("18000.00");
  }

  @Test
  void partialFuturesFillLocksMarginForOpenedPositionAndKeepsOrderLock() {
    Order futures = placeFutures(Side.BUY, "90000", 3);
    flushAndClear();

    Order filled =
        fillService.fill(OrderIdType.EXCHANGE, futures.orderId(), 1, new BigDecimal("90000"));
    flushAndClear();

    assertThat(filled.status()).isEqualTo(OrderStatus.PARTIALLY_FILLED);
    assertThat(cash()).isEqualByComparingTo("99999.00");
    assertThat(locked()).isEqualByComparingTo("9000.00");
    assertThat(position(FUTURES_TICKER, FUTURES_CLASS_CODE).orElseThrow().getQuantity())
        .isEqualTo(1);

    // 9000.00 ГО позиции + 18000.00 ГО остатка заявки (2 контракта по цене заявки).
    PortfolioSnapshot snapshot = portfolioService.snapshot();
    assertThat(snapshot.lockedForFutures()).isEqualByComparingTo("27000.00");
  }

  @Test
  void futuresSellClosesPositionReleasesMarginAndChargesOnlyCommission() {
    Order buy = placeFutures(Side.BUY, "90000", 2);
    fillService.fill(OrderIdType.EXCHANGE, buy.orderId(), 2, new BigDecimal("90000"));
    flushAndClear();
    BigDecimal cashBeforeSell = cash();

    Order sell = placeFutures(Side.SELL, "91000", 2);
    flushAndClear();

    Order filled =
        fillService.fill(OrderIdType.EXCHANGE, sell.orderId(), 2, new BigDecimal("91000"));
    flushAndClear();

    // Прибыль не зачисляется — это вариационная маржа (S3-5e); списывается только сбор.
    assertThat(cash()).isEqualByComparingTo(cashBeforeSell.subtract(new BigDecimal("2.00")));
    assertThat(cash()).isEqualByComparingTo("99996.00");
    assertThat(locked()).isEqualByComparingTo("0.00");
    assertThat(position(FUTURES_TICKER, FUTURES_CLASS_CODE)).isEmpty();
    assertThat(filled.status()).isEqualTo(OrderStatus.FILLED);
    assertThat(fills.findByOrderId(sell.orderId()).getFirst().getCommission())
        .isEqualByComparingTo("2.00");
  }

  @Test
  void futuresSellWithoutPositionOpensShortAndLocksMargin() {
    Order shortSell = placeFutures(Side.SELL, "90000", 2);
    flushAndClear();

    Order filled =
        fillService.fill(OrderIdType.EXCHANGE, shortSell.orderId(), 2, new BigDecimal("90000"));
    flushAndClear();

    assertThat(filled.status()).isEqualTo(OrderStatus.FILLED);
    assertThat(cash()).isEqualByComparingTo("99998.00");
    assertThat(locked()).isEqualByComparingTo("18000.00");
    assertThat(position(FUTURES_TICKER, FUTURES_CLASS_CODE).orElseThrow().getQuantity())
        .isEqualTo(-2);
  }

  @Test
  void futuresOpenWithoutEnoughMarginIsRejectedAndChangesNothing() {
    setCash("10000");
    Order futures = placeFutures(Side.BUY, "90000", 2);
    flushAndClear();

    assertThatThrownBy(
            () ->
                fillService.fill(
                    OrderIdType.EXCHANGE, futures.orderId(), 2, new BigDecimal("90000")))
        .isInstanceOf(InvalidOrderException.class)
        .hasMessageContaining("Недостаточно обеспечения");

    flushAndClear();
    assertThat(cash()).isEqualByComparingTo("10000.00");
    assertThat(locked()).isEqualByComparingTo("0.00");
    assertThat(position(FUTURES_TICKER, FUTURES_CLASS_CODE)).isEmpty();
    Order loaded = orderService.get(OrderIdType.EXCHANGE, futures.orderId());
    assertThat(loaded.status()).isEqualTo(OrderStatus.NEW);
    assertThat(loaded.executedQuantity()).isZero();
    assertThat(fills.findByOrderId(futures.orderId())).isEmpty();
  }

  @Test
  void futuresMarginOfAnotherActiveOrderIsCountedButOwnMarginIsExcluded() {
    setCash("18002");
    Order own = placeFutures(Side.BUY, "90000", 2);
    Order other = placeFutures(Side.BUY, "90000", 2);
    flushAndClear();

    assertThatThrownBy(
            () -> fillService.fill(OrderIdType.EXCHANGE, own.orderId(), 2, new BigDecimal("90000")))
        .isInstanceOf(InvalidOrderException.class)
        .hasMessageContaining("Недостаточно обеспечения");
    flushAndClear();
    assertThat(cash()).isEqualByComparingTo("18002.00");
    assertThat(locked()).isEqualByComparingTo("0.00");
    assertThat(orderService.get(OrderIdType.EXCHANGE, own.orderId()).status())
        .isEqualTo(OrderStatus.NEW);

    orderService.cancel(OrderIdType.EXCHANGE, other.orderId());
    flushAndClear();

    // Собственная блокировка (2 контракта, 18000.00) не учитывается: доступно 18000.00 после сбора.
    fillService.fill(OrderIdType.EXCHANGE, own.orderId(), 2, new BigDecimal("90000"));
    flushAndClear();

    assertThat(orderService.get(OrderIdType.EXCHANGE, own.orderId()).status())
        .isEqualTo(OrderStatus.FILLED);
    assertThat(cash()).isEqualByComparingTo("18000.00");
    assertThat(locked()).isEqualByComparingTo("18000.00");
    assertThat(position(FUTURES_TICKER, FUTURES_CLASS_CODE).orElseThrow().getQuantity())
        .isEqualTo(2);
  }

  @Test
  void futuresFillWithoutPriceUsesLastStubPrice() {
    Order futures = placeFutures(Side.BUY, "90000", 2);
    flushAndClear();

    fillService.fill(OrderIdType.EXCHANGE, futures.orderId(), 2, null);
    flushAndClear();

    FillEntity stored = fills.findByOrderId(futures.orderId()).getFirst();
    assertThat(stored.getPrice()).isEqualByComparingTo("90000");
    assertThat(stored.getCommission()).isEqualByComparingTo("2.00");
    assertThat(cash()).isEqualByComparingTo("99998.00");
    assertThat(locked()).isEqualByComparingTo("18000.00");
    assertThat(position(FUTURES_TICKER, FUTURES_CLASS_CODE).orElseThrow().getAveragePrice())
        .isEqualByComparingTo("90000");
    assertThat(orderService.get(OrderIdType.EXCHANGE, futures.orderId()).status())
        .isEqualTo(OrderStatus.FILLED);
  }

  @Test
  void nonPositiveFuturesFillQuantityIsRejected() {
    Order futures = placeFutures(Side.BUY, "90000", 2);
    flushAndClear();

    assertThatThrownBy(
            () ->
                fillService.fill(
                    OrderIdType.EXCHANGE, futures.orderId(), 0, new BigDecimal("90000")))
        .isInstanceOf(InvalidOrderException.class);
  }

  @Test
  void futuresFillQuantityAboveRemainingIsRejected() {
    Order futures = placeFutures(Side.BUY, "90000", 2);
    flushAndClear();

    assertThatThrownBy(
            () ->
                fillService.fill(
                    OrderIdType.EXCHANGE, futures.orderId(), 3, new BigDecimal("90000")))
        .isInstanceOf(InvalidOrderException.class);
  }

  @Test
  void unknownOrderIsRejected() {
    assertThatThrownBy(() -> fillService.fill(OrderIdType.EXCHANGE, "IT-UNKNOWN-FILL", 1, null))
        .isInstanceOf(ResourceNotFoundException.class);
  }

  @Test
  void blankOrderIdIsRejected() {
    assertThatThrownBy(() -> fillService.fill(OrderIdType.EXCHANGE, " ", 1, null))
        .isInstanceOf(InvalidRequestException.class);
  }

  @Test
  void malformedClientOrderIdIsRejected() {
    assertThatThrownBy(() -> fillService.fill(OrderIdType.CLIENT, "not-a-uuid", 1, null))
        .isInstanceOf(InvalidRequestException.class);
  }

  @Test
  void stockBuyWithNegativeCashIsRejectedAndChangesNothing() {
    ensureInstrument(SBER, TQBR, "STOCK", "10", "0.01");
    ensureInstrument(FUTURES_TICKER, FUTURES_CLASS_CODE, "FUTURES", "1", "1");
    setBalance("-1000", "18200");
    setPosition(FUTURES_TICKER, FUTURES_CLASS_CODE, -2, "91000");
    Order buy = placeStock(Side.BUY, "100", 1);
    flushAndClear();

    assertThatThrownBy(
            () -> fillService.fill(OrderIdType.EXCHANGE, buy.orderId(), 1, new BigDecimal("100")))
        .isInstanceOf(InvalidOrderException.class)
        .hasMessageContaining("Недостаточно");

    flushAndClear();
    assertThat(cash()).isEqualByComparingTo("-1000.00");
    assertThat(locked()).isEqualByComparingTo("18200.00");
    PositionEntity futures = position(FUTURES_TICKER, FUTURES_CLASS_CODE).orElseThrow();
    assertThat(futures.getQuantity()).isEqualTo(-2);
    assertThat(futures.getAveragePrice()).isEqualByComparingTo("91000");
    assertThat(position(SBER, TQBR)).isEmpty();
    Order loaded = orderService.get(OrderIdType.EXCHANGE, buy.orderId());
    assertThat(loaded.status()).isEqualTo(OrderStatus.NEW);
    assertThat(loaded.executedQuantity()).isZero();
    assertThat(fills.findByOrderId(buy.orderId())).isEmpty();
  }

  @Test
  void closingShortFuturesWithNegativeCashSucceedsAndReleasesMargin() {
    ensureInstrument(FUTURES_TICKER, FUTURES_CLASS_CODE, "FUTURES", "1", "1");
    setBalance("-1000", "18200");
    setPosition(FUTURES_TICKER, FUTURES_CLASS_CODE, -2, "91000");
    Order buy = placeFutures(Side.BUY, "91000", 2);
    flushAndClear();

    Order filled =
        fillService.fill(OrderIdType.EXCHANGE, buy.orderId(), 2, new BigDecimal("91000"));
    flushAndClear();

    // Закрытие убыточного шорта не требует свободных средств: списывается только сбор 1.00 x 2.
    assertThat(filled.status()).isEqualTo(OrderStatus.FILLED);
    assertThat(cash()).isEqualByComparingTo("-1002.00");
    assertThat(locked()).isEqualByComparingTo("0.00");
    assertThat(position(FUTURES_TICKER, FUTURES_CLASS_CODE)).isEmpty();
    assertThat(fills.findByOrderId(buy.orderId()).getFirst().getCommission())
        .isEqualByComparingTo("2.00");
  }

  @Test
  void stockSellWithNegativeCashCreditsProceedsAndReducesPosition() {
    ensureInstrument(SBER, TQBR, "STOCK", "10", "0.01");
    setBalance("-500", "0");
    setPosition(SBER, TQBR, 10, "100");
    Order sell = placeStock(Side.SELL, "110", 4);
    flushAndClear();

    Order filled = fillService.fill(OrderIdType.EXCHANGE, sell.orderId(), 4, new BigDecimal("110"));
    flushAndClear();

    // 4 x 110 - 0.1% = 439.56: продажа имеющихся акций увеличивает отрицательный кэш.
    assertThat(filled.status()).isEqualTo(OrderStatus.FILLED);
    assertThat(cash()).isEqualByComparingTo("-60.44");
    assertThat(position(SBER, TQBR).orElseThrow().getQuantity()).isEqualTo(6);
  }

  @Test
  void futuresOpenWithNegativeCashIsRejectedForMarginAndChangesNothing() {
    setBalance("-500", "0");
    Order buy = placeFutures(Side.BUY, "90000", 1);
    flushAndClear();

    assertThatThrownBy(
            () -> fillService.fill(OrderIdType.EXCHANGE, buy.orderId(), 1, new BigDecimal("90000")))
        .isInstanceOf(InvalidOrderException.class)
        .hasMessageContaining("Недостаточно обеспечения");

    flushAndClear();
    assertThat(cash()).isEqualByComparingTo("-500.00");
    assertThat(locked()).isEqualByComparingTo("0.00");
    assertThat(position(FUTURES_TICKER, FUTURES_CLASS_CODE)).isEmpty();
    Order loaded = orderService.get(OrderIdType.EXCHANGE, buy.orderId());
    assertThat(loaded.status()).isEqualTo(OrderStatus.NEW);
    assertThat(loaded.executedQuantity()).isZero();
    assertThat(fills.findByOrderId(buy.orderId())).isEmpty();
  }

  private Order placeStock(Side side, String price, long quantity) {
    return place(SBER, TQBR, side, price, quantity);
  }

  private Order placeFutures(Side side, String price, long quantity) {
    return place(FUTURES_TICKER, FUTURES_CLASS_CODE, side, price, quantity);
  }

  private Order place(String ticker, String classCode, Side side, String price, long quantity) {
    OrderType orderType = price == null ? OrderType.MARKET : OrderType.LIMIT;
    return orderService.place(
        new NewOrder(
            UUID.randomUUID(),
            ticker,
            classCode,
            side,
            orderType,
            TimeInForce.DAY,
            quantity,
            price == null ? null : new BigDecimal(price)));
  }

  private void setCash(String amount) {
    int updated =
        balances.updateBalances(ACCOUNT, CURRENCY, new BigDecimal(amount), BigDecimal.ZERO, NOW);
    assertThat(updated).isEqualTo(1);
    flushAndClear();
  }

  /** Состояние счёта после убыточной ВМ: кэш и балансовая блокировка ГО. */
  private void setBalance(String cash, String locked) {
    int updated =
        balances.updateBalances(
            ACCOUNT, CURRENCY, new BigDecimal(cash), new BigDecimal(locked), NOW);
    assertThat(updated).isEqualTo(1);
  }

  private void setPosition(String ticker, String classCode, long quantity, String averagePrice) {
    Long instrumentId =
        instruments.findByTickerAndClassCode(ticker, classCode).orElseThrow().getId();
    positions.upsert(ACCOUNT, instrumentId, quantity, new BigDecimal(averagePrice), NOW);
  }

  private void ensureInstrument(
      String ticker, String classCode, String type, String lotSize, String minimumStep) {
    instruments.insertIfAbsent(
        ticker,
        classCode,
        type,
        ticker,
        ticker,
        "",
        new BigDecimal(lotSize),
        new BigDecimal(minimumStep),
        CURRENCY);
  }

  private BigDecimal cash() {
    return balances.findByAccountIdAndCurrency(ACCOUNT, CURRENCY).orElseThrow().getCash();
  }

  /** Балансовая блокировка счёта — ГО открытых фьючерсных позиций. */
  private BigDecimal locked() {
    return balances.findByAccountIdAndCurrency(ACCOUNT, CURRENCY).orElseThrow().getLocked();
  }

  private Optional<PositionEntity> position(String ticker, String classCode) {
    return instruments
        .findByTickerAndClassCode(ticker, classCode)
        .flatMap(
            (InstrumentEntity instrument) ->
                positions.findByAccountIdAndInstrumentId(ACCOUNT, instrument.getId()));
  }

  private void flushAndClear() {
    entityManager.flush();
    entityManager.clear();
  }
}
