package ru.moex.papertrader.infra.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import ru.moex.papertrader.BrokerEmulatorApplication;
import ru.moex.papertrader.PostgresContainerSupport;
import ru.moex.papertrader.core.domain.NewOrder;
import ru.moex.papertrader.core.domain.Order;
import ru.moex.papertrader.core.domain.OrderIdType;
import ru.moex.papertrader.core.domain.OrderStatus;
import ru.moex.papertrader.core.domain.OrderType;
import ru.moex.papertrader.core.domain.PortfolioSnapshot;
import ru.moex.papertrader.core.domain.Position;
import ru.moex.papertrader.core.domain.Side;
import ru.moex.papertrader.core.domain.TimeInForce;
import ru.moex.papertrader.core.portfolio.NavSnapshot;
import ru.moex.papertrader.infra.model.NavDailyEntity;
import ru.moex.papertrader.infra.repository.AccountBalanceRepository;
import ru.moex.papertrader.infra.repository.NavDailyRepository;
import ru.moex.papertrader.infra.services.exception.ConflictException;

/**
 * Перезапуск сервиса на той же БД: заявки, сделки, позиции, баланс и NAV-срез восстанавливаются из
 * PostgreSQL, нумерация заявок продолжается.
 *
 * <p>Контекст поднимается вручную дважды через {@link SpringApplicationBuilder}: первый
 * закрывается, второй видит то же состояние. Testcontainers-контейнер общий, параметры подключения
 * берутся из {@link PostgresContainerSupport}. Счёт {@value #ACCOUNT} отдельный, данные удаляются
 * до и после теста (повторный запуск на том же контейнере остаётся детерминированным).
 */
class RestartIntegrationIT extends PostgresContainerSupport {

  private static final String ACCOUNT = "IT-RESTART-ACC-1";
  private static final String CURRENCY = "RUB";
  private static final String SBER = "SBER";
  private static final String SI = "SiU6";
  private static final LocalDate NAV_DATE = LocalDate.of(2026, 11, 2);

  @BeforeEach
  void deleteAccountDataBeforeTest() {
    deleteAccountData();
  }

  @AfterEach
  void deleteAccountDataAfterTest() {
    deleteAccountData();
  }

  @Test
  void stateSurvivesContextRestart() {
    State firstState;
    try (ConfigurableApplicationContext context = startContext()) {
      firstState = prepareState(context);
    }

    try (ConfigurableApplicationContext context = startContext()) {
      verifyStateRestored(context, firstState);
    }
  }

  /** Контекст A: акции (частичное исполнение), фьючерс и NAV-срез закрытия дня. */
  private State prepareState(ConfigurableApplicationContext context) {
    OrderService orderService = context.getBean(OrderService.class);
    FillService fillService = context.getBean(FillService.class);
    PortfolioService portfolioService = context.getBean(PortfolioService.class);
    MarginSettlementService marginSettlementService =
        context.getBean(MarginSettlementService.class);
    AccountBalanceRepository balances = context.getBean(AccountBalanceRepository.class);

    Order stockOrder = place(context, SBER, "TQBR", 10, "285.5");
    fillService.fill(OrderIdType.EXCHANGE, stockOrder.orderId(), 4, new BigDecimal("285.5"));
    Order futuresOrder = place(context, SI, "SPBFUT", 1, "90000");
    fillService.fill(OrderIdType.EXCHANGE, futuresOrder.orderId(), 1, new BigDecimal("90000"));
    assertThat(orderService.get(OrderIdType.CLIENT, stockOrder.clientOrderId().toString()).status())
        .isEqualTo(OrderStatus.PARTIALLY_FILLED);

    PortfolioSnapshot snapshot = portfolioService.snapshot();
    BigDecimal balanceLocked =
        balances.findByAccountIdAndCurrency(ACCOUNT, CURRENCY).orElseThrow().getLocked();
    NavSnapshot nav = marginSettlementService.closeDay(NAV_DATE);

    // Контекст собран с тестовыми часами: срез и заявки не зависят от реального времени.
    assertThat(snapshot.asOf()).isEqualTo(FixedClockTestConfiguration.NOW);
    assertThat(stockOrder.createdAt()).isEqualTo(FixedClockTestConfiguration.NOW);

    // 1 000 000 − 4 x 285.50 − 1.14 (0.1% комиссии) − 1.00 (сбор фьючерса) = 998 855.86.
    assertThat(snapshot.cash()).isEqualByComparingTo("998855.86");
    assertThat(balanceLocked).isEqualByComparingTo("9000.00");
    assertThat(snapshot.lockedForFuturesPositions()).isEqualByComparingTo("9000.00");
    assertThat(snapshot.lockedForFuturesOrders()).isEqualByComparingTo("0.00");
    // 6 неисполненных акций x 285.50 = 1713.00 + ГО фьючерсной позиции 9000.00.
    assertThat(snapshot.lockedCash()).isEqualByComparingTo("10713.00");
    assertPosition(snapshot, SBER, 4, "285.5");
    assertPosition(snapshot, SI, 1, "90000");
    // NAV = кэш 998 858.00 (баланс + комиссия дня 2.14) − 2.14 + 0 + 4 x 285.50.
    assertThat(nav.cash()).isEqualByComparingTo("998858.00");
    assertThat(nav.commission()).isEqualByComparingTo("2.14");
    assertThat(nav.variationMargin()).isEqualByComparingTo("0.00");
    assertThat(nav.marketValue()).isEqualByComparingTo("1142.00");
    assertThat(nav.nav()).isEqualByComparingTo("999997.86");

    return new State(
        snapshot,
        balanceLocked,
        nav,
        stockOrder.orderNum(),
        futuresOrder.orderNum(),
        stockOrder.clientOrderId().toString(),
        futuresOrder.orderId());
  }

  /**
   * Контекст B: состояние читается из той же БД, нумерация продолжается, повторный NAV — конфликт.
   */
  private void verifyStateRestored(ConfigurableApplicationContext context, State first) {
    OrderService orderService = context.getBean(OrderService.class);
    PortfolioService portfolioService = context.getBean(PortfolioService.class);
    MarginSettlementService marginSettlementService =
        context.getBean(MarginSettlementService.class);
    AccountBalanceRepository balances = context.getBean(AccountBalanceRepository.class);
    NavDailyRepository navDaily = context.getBean(NavDailyRepository.class);

    // Заявки и их статусы: частичное исполнение сохранено.
    Order stockOrder = orderService.get(OrderIdType.CLIENT, first.stockClientOrderId());
    assertThat(stockOrder.status()).isEqualTo(OrderStatus.PARTIALLY_FILLED);
    assertThat(stockOrder.executedQuantity()).isEqualTo(4);
    assertThat(stockOrder.averagePrice()).isEqualByComparingTo("285.5");
    assertThat(stockOrder.quantity() - stockOrder.executedQuantity()).isEqualTo(6);
    Order futuresOrder = orderService.get(OrderIdType.EXCHANGE, first.futuresOrderId());
    assertThat(futuresOrder.status()).isEqualTo(OrderStatus.FILLED);
    assertThat(futuresOrder.executedQuantity()).isEqualTo(1);

    // Портфель: кэш, блокировки и позиции те же.
    PortfolioSnapshot restored = portfolioService.snapshot();
    assertThat(restored.cash()).isEqualByComparingTo(first.snapshot().cash());
    assertThat(restored.lockedCash()).isEqualByComparingTo(first.snapshot().lockedCash());
    assertThat(restored.lockedForFuturesOrders())
        .isEqualByComparingTo(first.snapshot().lockedForFuturesOrders());
    assertThat(restored.lockedForFuturesPositions())
        .isEqualByComparingTo(first.snapshot().lockedForFuturesPositions());
    assertThat(balances.findByAccountIdAndCurrency(ACCOUNT, CURRENCY).orElseThrow().getLocked())
        .isEqualByComparingTo(first.balanceLocked());
    assertPosition(restored, SBER, 4, "285.5");
    assertPosition(restored, SI, 1, "90000");

    // NAV-срез закрытия дня восстановлен без изменений.
    NavDailyEntity navRow = navDaily.findByAccountIdAndNavDate(ACCOUNT, NAV_DATE).orElseThrow();
    assertThat(navRow.getNav()).isEqualByComparingTo(first.nav().nav());
    assertThat(navRow.getCash()).isEqualByComparingTo(first.nav().cash());
    assertThat(navRow.getCommission()).isEqualByComparingTo(first.nav().commission());
    assertThat(navRow.getVariationMargin()).isEqualByComparingTo(first.nav().variationMargin());
    assertThat(navRow.getMarketValue()).isEqualByComparingTo(first.nav().marketValue());

    // Нумерация продолжается: новая заявка получает номер больше всех прежних.
    Order next = place(context, SBER, "TQBR", 1, "285.5");
    assertThat(next.orderNum())
        .isGreaterThan(first.stockOrderNum())
        .isGreaterThan(first.futuresOrderNum());

    // Срез за ту же дату уже существует — повторное закрытие дня конфликтует.
    assertThatThrownBy(() -> marginSettlementService.closeDay(NAV_DATE))
        .isInstanceOf(ConflictException.class);
  }

  private Order place(
      ConfigurableApplicationContext context,
      String ticker,
      String classCode,
      long quantity,
      String price) {
    return context
        .getBean(OrderService.class)
        .place(
            new NewOrder(
                UUID.randomUUID(),
                ticker,
                classCode,
                Side.BUY,
                OrderType.LIMIT,
                TimeInForce.DAY,
                quantity,
                new BigDecimal(price)));
  }

  private static void assertPosition(
      PortfolioSnapshot snapshot, String ticker, long quantity, String averagePrice) {
    Position position =
        snapshot.positions().stream()
            .filter(candidate -> ticker.equals(candidate.instrument().ticker()))
            .findFirst()
            .orElseThrow(() -> new AssertionError("Нет позиции " + ticker + ": " + snapshot));
    assertThat(position.quantity()).isEqualTo(quantity);
    assertThat(position.averagePrice()).isEqualByComparingTo(averagePrice);
  }

  private ConfigurableApplicationContext startContext() {
    // Проверки идут через сервисы, HTTP не нужен, но веб-окружение поднимается как у
    // BKS-контекстов: сервер слушает случайный порт (server.port=0) и закрывается вместе с
    // контекстом. Параметры — аргументами командной строки: у них приоритет выше application.yml,
    // где datasource по умолчанию указывает на локальный papertrader.
    return new SpringApplicationBuilder(BrokerEmulatorApplication.class)
        .profiles("test")
        .sources(FixedClockTestConfiguration.class)
        .run(
            "--spring.main.banner-mode=off",
            "--server.port=0",
            "--emulator.portfolio.account=" + ACCOUNT,
            "--spring.datasource.url=" + jdbcUrl(),
            "--spring.datasource.username=" + jdbcUsername(),
            "--spring.datasource.password=" + jdbcPassword());
  }

  /**
   * Удаление данных счёта вручную: контекст может быть уже закрыт, поэтому JDBC без Spring-бина.
   */
  private static void deleteAccountData() {
    JdbcTemplate jdbc =
        new JdbcTemplate(new DriverManagerDataSource(jdbcUrl(), jdbcUsername(), jdbcPassword()));
    // Контейнер общий на JVM: к моменту первого запуска теста схему мог не создать ещё никто.
    Boolean schemaReady =
        jdbc.queryForObject("SELECT to_regclass('public.fills') IS NOT NULL", Boolean.class);
    if (!Boolean.TRUE.equals(schemaReady)) {
      return;
    }
    jdbc.update("DELETE FROM fills WHERE account_id = ?", ACCOUNT);
    jdbc.update("DELETE FROM orders WHERE account_id = ?", ACCOUNT);
    jdbc.update("DELETE FROM positions WHERE account_id = ?", ACCOUNT);
    jdbc.update("DELETE FROM nav_daily WHERE account_id = ?", ACCOUNT);
    jdbc.update("DELETE FROM account_balances WHERE account_id = ?", ACCOUNT);
    jdbc.update("DELETE FROM accounts WHERE account_id = ?", ACCOUNT);
    // SiU6/SPBFUT создаётся только коммитящими сценариями и не должен «протекать» в
    // JpaOrderServiceImplIT, который проверяет автозоздание инструмента с пустой таблицей.
    jdbc.update("DELETE FROM instruments WHERE ticker = ? AND class_code = ?", SI, "SPBFUT");
  }

  /** Снимок состояния контекста A для сравнения после перезапуска. */
  private record State(
      PortfolioSnapshot snapshot,
      BigDecimal balanceLocked,
      NavSnapshot nav,
      long stockOrderNum,
      long futuresOrderNum,
      String stockClientOrderId,
      String futuresOrderId) {}
}
