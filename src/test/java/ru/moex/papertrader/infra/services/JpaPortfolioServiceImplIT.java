package ru.moex.papertrader.infra.services;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;
import ru.moex.papertrader.PostgresContainerSupport;
import ru.moex.papertrader.core.domain.NewOrder;
import ru.moex.papertrader.core.domain.Order;
import ru.moex.papertrader.core.domain.OrderIdType;
import ru.moex.papertrader.core.domain.OrderType;
import ru.moex.papertrader.core.domain.PortfolioSnapshot;
import ru.moex.papertrader.core.domain.Position;
import ru.moex.papertrader.core.domain.Side;
import ru.moex.papertrader.core.domain.TimeInForce;
import ru.moex.papertrader.infra.model.AccountBalanceEntity;
import ru.moex.papertrader.infra.model.PositionEntity;
import ru.moex.papertrader.infra.repository.AccountBalanceRepository;
import ru.moex.papertrader.infra.repository.InstrumentRepository;
import ru.moex.papertrader.infra.repository.PositionRepository;

/**
 * Портфельный снапшот из PostgreSQL: кэш счёта, блокировки акций, ГО фьючерсных заявок и
 * заблокированная маржа позиций.
 *
 * <p>БД общая для всего прогона (BKS-тесты коммитят активные заявки), поэтому проверяются дельты
 * относительно базовой линии, снятой в начале метода. Транзакция теста откатывает созданные заявки
 * и правки баланса.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Transactional
@Import(FixedClockTestConfiguration.class)
class JpaPortfolioServiceImplIT extends PostgresContainerSupport {

  private static final String FUTURES_TICKER = "SiU6";
  private static final String FUTURES_CLASS_CODE = "SPBFUT";
  private static final String FUTURES_PRICE = "90000";
  private static final BigDecimal FUTURES_MARGIN_FOR_TWO = new BigDecimal("18000");

  @Autowired private PortfolioService portfolioService;
  @Autowired private OrderService orderService;
  @Autowired private PortfolioProperties properties;
  @Autowired private AccountBalanceRepository balances;
  @Autowired private PositionRepository positions;
  @Autowired private InstrumentRepository instruments;
  @Autowired private EntityManager entityManager;

  @Test
  void stockBuyLimitLocksCashWithoutFuturesMargin() {
    PortfolioSnapshot before = snapshot();

    Order order = orderService.place(limitOrder("SBER", "TQBR", Side.BUY, "100", 10));
    entityManager.flush();

    PortfolioSnapshot after = snapshot();

    assertThat(order.instrument().type()).isEqualTo("STOCK");
    assertDelta(after.lockedCash(), before.lockedCash(), "1000");
    assertDelta(after.lockedForFutures(), before.lockedForFutures(), "0");
  }

  @Test
  void futuresBuyLimitLocksInitialMargin() {
    PortfolioSnapshot before = snapshot();

    orderService.place(limitOrder(FUTURES_TICKER, FUTURES_CLASS_CODE, Side.BUY, FUTURES_PRICE, 2));
    entityManager.flush();

    PortfolioSnapshot after = snapshot();

    assertDelta(after.lockedForFutures(), before.lockedForFutures(), FUTURES_MARGIN_FOR_TWO);
    assertDelta(after.lockedCash(), before.lockedCash(), FUTURES_MARGIN_FOR_TWO);
  }

  @Test
  void futuresSellLimitLocksInitialMarginOfBothSides() {
    PortfolioSnapshot before = snapshot();

    orderService.place(limitOrder(FUTURES_TICKER, FUTURES_CLASS_CODE, Side.SELL, FUTURES_PRICE, 2));
    entityManager.flush();

    PortfolioSnapshot after = snapshot();

    assertDelta(after.lockedForFutures(), before.lockedForFutures(), FUTURES_MARGIN_FOR_TWO);
    assertDelta(after.lockedCash(), before.lockedCash(), FUTURES_MARGIN_FOR_TWO);
  }

  @Test
  void marketFuturesOrderUsesLastStubPrice() {
    PortfolioSnapshot before = snapshot();

    orderService.place(marketOrder(FUTURES_TICKER, FUTURES_CLASS_CODE, Side.BUY, 2));
    entityManager.flush();

    PortfolioSnapshot after = snapshot();

    assertDelta(after.lockedForFutures(), before.lockedForFutures(), FUTURES_MARGIN_FOR_TWO);
    assertDelta(after.lockedCash(), before.lockedCash(), FUTURES_MARGIN_FOR_TWO);
  }

  @Test
  void canceledFuturesOrderReleasesMargin() {
    PortfolioSnapshot before = snapshot();
    Order placed =
        orderService.place(
            limitOrder(FUTURES_TICKER, FUTURES_CLASS_CODE, Side.BUY, FUTURES_PRICE, 2));
    entityManager.flush();

    orderService.cancel(OrderIdType.EXCHANGE, placed.orderId());
    entityManager.flush();

    PortfolioSnapshot after = snapshot();

    assertDelta(after.lockedForFutures(), before.lockedForFutures(), "0");
    assertDelta(after.lockedCash(), before.lockedCash(), "0");
  }

  @Test
  void balanceLockedIsIncludedInFuturesMargin() {
    PortfolioSnapshot before = snapshot();
    AccountBalanceEntity current =
        balances
            .findByAccountIdAndCurrency(properties.account(), properties.currency())
            .orElseThrow();

    int updated =
        balances.updateBalances(
            properties.account(),
            properties.currency(),
            current.getCash(),
            current.getLocked().add(new BigDecimal("500")),
            FixedClockTestConfiguration.NOW);
    entityManager.flush();
    entityManager.clear();

    PortfolioSnapshot after = snapshot();

    assertThat(updated).isEqualTo(1);
    assertDelta(after.lockedForFutures(), before.lockedForFutures(), "500");
    assertDelta(after.lockedCash(), before.lockedCash(), "500");
  }

  @Test
  void splitsFuturesMarginBetweenOrdersAndPositions() {
    PortfolioSnapshot before = snapshot();

    orderService.place(limitOrder(FUTURES_TICKER, FUTURES_CLASS_CODE, Side.BUY, FUTURES_PRICE, 2));
    AccountBalanceEntity current =
        balances
            .findByAccountIdAndCurrency(properties.account(), properties.currency())
            .orElseThrow();
    // balance.locked — ГО открытых позиций (9000 за контракт SiU6); заявка даёт ГО 18000.
    int updated =
        balances.updateBalances(
            properties.account(),
            properties.currency(),
            current.getCash(),
            current.getLocked().add(new BigDecimal("9000")),
            FixedClockTestConfiguration.NOW);
    entityManager.flush();
    entityManager.clear();

    PortfolioSnapshot after = snapshot();

    assertThat(updated).isEqualTo(1);
    assertDelta(after.lockedForFuturesOrders(), before.lockedForFuturesOrders(), "18000");
    assertDelta(after.lockedForFuturesPositions(), before.lockedForFuturesPositions(), "9000");
    assertDelta(after.lockedForFutures(), before.lockedForFutures(), "27000");
    assertThat(after.lockedForFutures())
        .isEqualByComparingTo(
            after.lockedForFuturesOrders().add(after.lockedForFuturesPositions()));
  }

  @Test
  void freeCashIsCashMinusLockedCash() {
    PortfolioSnapshot before = snapshot();

    orderService.place(limitOrder("SBER", "TQBR", Side.BUY, "100", 10));
    entityManager.flush();

    PortfolioSnapshot after = snapshot();

    assertThat(after.freeCash()).isEqualByComparingTo(after.cash().subtract(after.lockedCash()));
    assertDelta(after.freeCash(), before.freeCash(), "-1000");
  }

  @Test
  void snapshotRecreatesMissingBalanceInsideWritableTransaction() {
    balances
        .findByAccountIdAndCurrency(properties.account(), properties.currency())
        .ifPresent(balances::delete);
    entityManager.flush();
    entityManager.clear();

    PortfolioSnapshot snapshot = portfolioService.snapshot();

    assertThat(snapshot.cash()).isEqualByComparingTo(properties.initialCash());
    assertThat(balances.findByAccountIdAndCurrency(properties.account(), properties.currency()))
        .isPresent();
  }

  @Test
  void mapsStoredPositionsWithLastStubPrice() {
    orderService.place(limitOrder("LKOH", "TQBR", Side.BUY, "7100", 1));
    Long instrumentId = instruments.findByTickerAndClassCode("LKOH", "TQBR").orElseThrow().getId();
    positions.saveAndFlush(
        new PositionEntity(
            properties.account(),
            instrumentId,
            3,
            new BigDecimal("7000"),
            FixedClockTestConfiguration.NOW));

    PortfolioSnapshot snapshot = portfolioService.snapshot();

    Position position =
        snapshot.positions().stream()
            .filter(row -> "LKOH".equals(row.instrument().ticker()))
            .findFirst()
            .orElseThrow();
    assertThat(position.quantity()).isEqualTo(3);
    assertThat(position.averagePrice()).isEqualByComparingTo("7000");
    assertThat(position.currentPrice()).isEqualByComparingTo("7100.00");
    assertThat(snapshot.asOf()).isEqualTo(FixedClockTestConfiguration.NOW);
  }

  private PortfolioSnapshot snapshot() {
    return portfolioService.snapshot();
  }

  private static void assertDelta(BigDecimal after, BigDecimal before, String expected) {
    assertDelta(after, before, new BigDecimal(expected));
  }

  private static void assertDelta(BigDecimal after, BigDecimal before, BigDecimal expected) {
    assertThat(after.subtract(before)).isEqualByComparingTo(expected);
  }

  private static NewOrder limitOrder(
      String ticker, String classCode, Side side, String price, long quantity) {
    return new NewOrder(
        UUID.randomUUID(),
        ticker,
        classCode,
        side,
        OrderType.LIMIT,
        TimeInForce.DAY,
        quantity,
        new BigDecimal(price));
  }

  private static NewOrder marketOrder(String ticker, String classCode, Side side, long quantity) {
    return new NewOrder(
        UUID.randomUUID(),
        ticker,
        classCode,
        side,
        OrderType.MARKET,
        TimeInForce.DAY,
        quantity,
        null);
  }
}
