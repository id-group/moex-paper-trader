package ru.moex.papertrader.infra.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;
import ru.moex.papertrader.PostgresContainerSupport;
import ru.moex.papertrader.core.domain.Instrument;
import ru.moex.papertrader.core.domain.NewOrder;
import ru.moex.papertrader.core.domain.Order;
import ru.moex.papertrader.core.domain.OrderIdType;
import ru.moex.papertrader.core.domain.OrderType;
import ru.moex.papertrader.core.domain.Side;
import ru.moex.papertrader.core.domain.TimeInForce;
import ru.moex.papertrader.core.marketdata.MarketDataProvider;
import ru.moex.papertrader.core.marketdata.Quote;
import ru.moex.papertrader.core.portfolio.NavSnapshot;
import ru.moex.papertrader.infra.mapper.NavDailyMapper;
import ru.moex.papertrader.infra.model.AccountBalanceEntity;
import ru.moex.papertrader.infra.model.NavDailyEntity;
import ru.moex.papertrader.infra.model.PositionEntity;
import ru.moex.papertrader.infra.repository.AccountBalanceRepository;
import ru.moex.papertrader.infra.repository.InstrumentRepository;
import ru.moex.papertrader.infra.repository.NavDailyRepository;
import ru.moex.papertrader.infra.repository.PositionRepository;
import ru.moex.papertrader.infra.services.exception.ConflictException;

/**
 * Вариационная маржа по явному вызову и NAV-срез закрытия дня.
 *
 * <p>Тест работает на отдельном счёте {@value #ACCOUNT}: позиции и баланс задаются напрямую,
 * поэтому сценарии не зависят от заявок и сделок других тестов. Расчётная цена дня подменяется
 * тестовым провайдером: {@link StubMarketDataProvider} берётся за основу, но {@code quote.last} для
 * выбранных тикеров переопределяется. Транзакция метода откатывается.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Transactional
@Import({FixedClockTestConfiguration.class, MarginSettlementIT.SettlementPriceConfiguration.class})
@TestPropertySource(properties = "emulator.portfolio.account=IT-SETTLE-ACC-1")
class MarginSettlementIT extends PostgresContainerSupport {

  private static final String ACCOUNT = "IT-SETTLE-ACC-1";
  private static final String CURRENCY = "RUB";
  private static final String SBER = "SBER";
  private static final String TQBR = "TQBR";
  private static final String FUTURES = "SiU6";
  private static final String FUTURES_CLASS = "SPBFUT";
  private static final LocalDate NAV_DATE = LocalDate.of(2026, 11, 2);

  @Autowired private MarginSettlementService marginSettlementService;
  @Autowired private FillService fillService;
  @Autowired private OrderService orderService;
  @Autowired private AccountService accountService;
  @Autowired private AccountBalanceRepository balances;
  @Autowired private PositionRepository positions;
  @Autowired private InstrumentRepository instruments;
  @Autowired private NavDailyRepository navDaily;
  @Autowired private NavDailyMapper navDailyMapper;
  @Autowired private EntityManager entityManager;

  @BeforeEach
  void prepareAccount() {
    accountService.ensureAccount();
    ensureInstrument(SBER, TQBR, "STOCK", "Сбербанк", "RU0009029540", "10", "0.01");
    ensureInstrument(FUTURES, FUTURES_CLASS, "FUTURES", "Si-12.26", "", "1", "1");
    setBalance("100000", "0");
    SettlementPriceConfiguration.PRICES.clear();
    SettlementPriceConfiguration.PRICES.put(SBER, new BigDecimal("285.50"));
    flushAndClear();
  }

  @Test
  void longFuturesPositionReceivesPositiveVariationMarginAndRecomputedMargin() {
    setBalance("50000", "18000");
    setPosition(FUTURES, FUTURES_CLASS, 2, "90000");
    SettlementPriceConfiguration.PRICES.put(FUTURES, new BigDecimal("91000"));
    flushAndClear();

    BigDecimal variationMargin = marginSettlementService.settleVariationMargin();
    flushAndClear();

    assertThat(variationMargin).isEqualByComparingTo("2000.00");
    assertThat(balance().getCash()).isEqualByComparingTo("52000.00");
    assertThat(balance().getLocked()).isEqualByComparingTo("18200.00");
    PositionEntity position = position(FUTURES, FUTURES_CLASS).orElseThrow();
    assertThat(position.getQuantity()).isEqualTo(2);
    assertThat(position.getAveragePrice()).isEqualByComparingTo("91000");
  }

  @Test
  void longFuturesPositionLosesWhenSettlementPriceFalls() {
    setBalance("50000", "18000");
    setPosition(FUTURES, FUTURES_CLASS, 2, "90000");
    SettlementPriceConfiguration.PRICES.put(FUTURES, new BigDecimal("89000"));
    flushAndClear();

    BigDecimal variationMargin = marginSettlementService.settleVariationMargin();
    flushAndClear();

    assertThat(variationMargin).isEqualByComparingTo("-2000.00");
    assertThat(balance().getCash()).isEqualByComparingTo("48000.00");
    assertThat(balance().getLocked()).isEqualByComparingTo("17800.00");
    assertThat(position(FUTURES, FUTURES_CLASS).orElseThrow().getAveragePrice())
        .isEqualByComparingTo("89000");
  }

  @Test
  void shortFuturesPositionSettlesEvenWhenFreeCashGoesNegative() {
    // Свободных средств нет: 18000 блокировки при кэше 1000. ВМ обязательна и не проверяет
    // обеспечение, поэтому кэш уходит в минус, а ГО растёт до 18200.
    setBalance("1000", "18000");
    setPosition(FUTURES, FUTURES_CLASS, -2, "90000");
    SettlementPriceConfiguration.PRICES.put(FUTURES, new BigDecimal("91000"));
    flushAndClear();

    BigDecimal variationMargin = marginSettlementService.settleVariationMargin();
    flushAndClear();

    assertThat(variationMargin).isEqualByComparingTo("-2000.00");
    assertThat(balance().getCash()).isEqualByComparingTo("-1000.00");
    assertThat(balance().getLocked()).isEqualByComparingTo("18200.00");
    assertThat(balance().getCash()).isLessThan(balance().getLocked());
    assertThat(position(FUTURES, FUTURES_CLASS).orElseThrow().getQuantity()).isEqualTo(-2);
  }

  @Test
  void repeatedSettlementAtSamePriceChangesNothing() {
    setBalance("50000", "18000");
    setPosition(FUTURES, FUTURES_CLASS, 2, "90000");
    SettlementPriceConfiguration.PRICES.put(FUTURES, new BigDecimal("91000"));
    flushAndClear();

    BigDecimal first = marginSettlementService.settleVariationMargin();
    flushAndClear();
    BigDecimal second = marginSettlementService.settleVariationMargin();
    flushAndClear();

    assertThat(first).isEqualByComparingTo("2000.00");
    assertThat(second).isEqualByComparingTo("0.00");
    assertThat(balance().getCash()).isEqualByComparingTo("52000.00");
    assertThat(balance().getLocked()).isEqualByComparingTo("18200.00");
    assertThat(position(FUTURES, FUTURES_CLASS).orElseThrow().getAveragePrice())
        .isEqualByComparingTo("91000");
  }

  @Test
  void closeDaySettlesMarginAndWritesSingleNavRowWithStockAndFuturesHoldings() {
    setBalance("100000", "18000");
    setPosition(SBER, TQBR, 10, "100");
    setPosition(FUTURES, FUTURES_CLASS, 2, "90000");
    SettlementPriceConfiguration.PRICES.put(FUTURES, new BigDecimal("91000"));
    flushAndClear();

    NavSnapshot snapshot = marginSettlementService.closeDay(NAV_DATE);
    flushAndClear();

    // ВМ +2000 в кэш: 102000; рыночная стоимость только по акциям: 10 x 285.50 = 2855.
    assertThat(balance().getCash()).isEqualByComparingTo("102000.00");
    assertThat(snapshot.variationMargin()).isEqualByComparingTo("2000.00");
    assertThat(snapshot.commission()).isEqualByComparingTo("0.00");
    assertThat(snapshot.marketValue()).isEqualByComparingTo("2855.00");
    assertThat(snapshot.nav()).isEqualByComparingTo("104855.00");
    assertThat(snapshot.nav())
        .isEqualByComparingTo(balance().getCash().add(snapshot.marketValue()));

    NavDailyEntity row = navDaily.findByAccountIdAndNavDate(ACCOUNT, NAV_DATE).orElseThrow();
    assertThat(row.getNav()).isEqualByComparingTo("104855.00");
    assertThat(row.getCash()).isEqualByComparingTo("100000.00");
    assertThat(row.getVariationMargin()).isEqualByComparingTo("2000.00");
    assertThat(row.getMarketValue()).isEqualByComparingTo("2855.00");

    NavSnapshot stored = navDailyMapper.toDomain(row);
    assertThat(stored.holdings())
        .extracting(NavSnapshot.Holding::ticker)
        .containsExactlyInAnyOrder(SBER, FUTURES);
    assertThat(stored.holdings())
        .filteredOn(holding -> FUTURES.equals(holding.ticker()))
        .singleElement()
        .satisfies(
            holding -> {
              assertThat(holding.quantity()).isEqualTo(2);
              assertThat(holding.closingPrice()).isEqualByComparingTo("91000");
            });
    assertThat(stored.holdings())
        .filteredOn(holding -> SBER.equals(holding.ticker()))
        .singleElement()
        .satisfies(
            holding -> {
              assertThat(holding.quantity()).isEqualTo(10);
              assertThat(holding.closingPrice()).isEqualByComparingTo("285.50");
            });
  }

  @Test
  void secondCloseDayForSameDateConflictsAndKeepsSingleRow() {
    setBalance("100000", "0");
    flushAndClear();

    marginSettlementService.closeDay(NAV_DATE);
    flushAndClear();

    assertThatThrownBy(() -> marginSettlementService.closeDay(NAV_DATE))
        .isInstanceOf(ConflictException.class);

    flushAndClear();
    assertThat(navDaily.findByAccountIdAndNavDate(ACCOUNT, NAV_DATE)).isPresent();
    assertThat(
            navDaily.findByAccountIdOrderByNavDateAsc(ACCOUNT).stream()
                .filter(row -> NAV_DATE.equals(row.getNavDate()))
                .count())
        .isEqualTo(1);
  }

  @Test
  void commissionOfTheDayIsPartOfNavComponentsAndNavEqualsCashPlusMarketValue() {
    setBalance("100000", "0");
    flushAndClear();
    Order order =
        orderService.place(
            new NewOrder(
                UUID.randomUUID(),
                SBER,
                TQBR,
                Side.BUY,
                OrderType.LIMIT,
                TimeInForce.DAY,
                10,
                new BigDecimal("100")));
    fillService.fill(OrderIdType.EXCHANGE, order.orderId(), 10, new BigDecimal("100"));
    flushAndClear();

    NavSnapshot snapshot = marginSettlementService.closeDay(NAV_DATE);
    flushAndClear();

    // Комиссия 0.1% от 1000 = 1.00 уже списана из кэша: баланс 98999.00.
    assertThat(balance().getCash()).isEqualByComparingTo("98999.00");
    assertThat(snapshot.commission()).isEqualByComparingTo("1.00");
    assertThat(snapshot.variationMargin()).isEqualByComparingTo("0.00");
    assertThat(snapshot.cash()).isEqualByComparingTo("99000.00");
    assertThat(snapshot.marketValue()).isEqualByComparingTo("2855.00");
    assertThat(snapshot.nav()).isEqualByComparingTo("101854.00");
    assertThat(snapshot.nav())
        .isEqualByComparingTo(balance().getCash().add(snapshot.marketValue()));
  }

  private void ensureInstrument(
      String ticker,
      String classCode,
      String type,
      String shortName,
      String isin,
      String lotSize,
      String minimumStep) {
    instruments.insertIfAbsent(
        ticker,
        classCode,
        type,
        shortName,
        shortName,
        isin,
        new BigDecimal(lotSize),
        new BigDecimal(minimumStep),
        CURRENCY);
  }

  private void setBalance(String cash, String locked) {
    int updated =
        balances.updateBalances(
            ACCOUNT,
            CURRENCY,
            new BigDecimal(cash),
            new BigDecimal(locked),
            FixedClockTestConfiguration.NOW);
    assertThat(updated).isEqualTo(1);
  }

  private void setPosition(String ticker, String classCode, long quantity, String averagePrice) {
    Long instrumentId =
        instruments.findByTickerAndClassCode(ticker, classCode).orElseThrow().getId();
    positions.upsert(
        ACCOUNT,
        instrumentId,
        quantity,
        new BigDecimal(averagePrice),
        FixedClockTestConfiguration.NOW);
  }

  private AccountBalanceEntity balance() {
    return balances.findByAccountIdAndCurrency(ACCOUNT, CURRENCY).orElseThrow();
  }

  private Optional<PositionEntity> position(String ticker, String classCode) {
    return instruments
        .findByTickerAndClassCode(ticker, classCode)
        .flatMap(
            instrument -> positions.findByAccountIdAndInstrumentId(ACCOUNT, instrument.getId()));
  }

  private void flushAndClear() {
    entityManager.flush();
    entityManager.clear();
  }

  /** Тестовый провайдер: базовая заглушка плюс переопределение последней цены по тикеру. */
  @TestConfiguration
  static class SettlementPriceConfiguration {

    private static final Map<String, BigDecimal> PRICES = new ConcurrentHashMap<>();

    @Bean
    @Primary
    MarketDataProvider settlementPriceMarketData(StubMarketDataProperties properties, Clock clock) {
      return new StubMarketDataProvider(properties, clock) {
        @Override
        public Optional<Quote> quote(Instrument instrument) {
          return super.quote(instrument)
              .map(quote -> withLastPrice(quote, PRICES.get(instrument.ticker())));
        }
      };
    }

    private static Quote withLastPrice(Quote quote, BigDecimal last) {
      if (last == null) {
        return quote;
      }
      return new Quote(
          quote.ticker(),
          quote.classCode(),
          quote.dateTime(),
          quote.tradingStatus(),
          quote.currency(),
          quote.bid(),
          quote.offer(),
          quote.open(),
          quote.close(),
          quote.high(),
          quote.low(),
          last,
          quote.change(),
          quote.changeRate());
    }
  }
}
