package ru.moex.papertrader.infra.services;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import ru.moex.papertrader.PostgresContainerSupport;
import ru.moex.papertrader.core.domain.Instrument;
import ru.moex.papertrader.core.domain.NewOrder;
import ru.moex.papertrader.core.domain.Order;
import ru.moex.papertrader.core.domain.OrderIdType;
import ru.moex.papertrader.core.domain.OrderStatus;
import ru.moex.papertrader.core.domain.OrderType;
import ru.moex.papertrader.core.domain.Side;
import ru.moex.papertrader.core.domain.TimeInForce;
import ru.moex.papertrader.core.marketdata.Clock;
import ru.moex.papertrader.core.marketdata.MarketDataProvider;
import ru.moex.papertrader.core.marketdata.MarketDataSnapshot;
import ru.moex.papertrader.core.marketdata.OrderBookEvent;
import ru.moex.papertrader.core.marketdata.OrderBookSnapshot;
import ru.moex.papertrader.core.marketdata.Quote;
import ru.moex.papertrader.core.marketdata.QuoteSnapshot;
import ru.moex.papertrader.core.marketdata.ReplayMarketDataProvider;
import ru.moex.papertrader.core.marketdata.TradingCalendar;
import ru.moex.papertrader.core.marketdata.TradingDay;
import ru.moex.papertrader.core.marketdata.TradingSession;
import ru.moex.papertrader.core.marketdata.VirtualClock;

/**
 * Мини-сценарий REPLAY: две торговые сессии, покупка и продажа SBER по виртуальному таймлайну,
 * закрытие NAV обоих дней и отчёт этапа 6.
 *
 * <p>Провайдер рыночных данных подменяется детерминированным {@link ReplayMarketDataProvider} с
 * явным таймлайном: так тест не зависит от записи в БД и сетевых источников. Проскальзывание
 * берётся из seed REPLAY (одинаковый при каждом запуске), часы — только {@link VirtualClock}.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
@TestPropertySource(
    properties = {
      "emulator.marketdata.mode=REPLAY",
      "emulator.marketdata.replay.start=2026-11-02T07:00:00Z",
      // Планировщик REPLAY не должен двигать время во время теста.
      "emulator.marketdata.replay.speed=0",
      "emulator.portfolio.account=IT-REPLAY-REPORT",
      "emulator.reporting.output-dir=build/reports/test/replay-it"
    })
@Import(ReplayReportIntegrationIT.ReplayMarketConfiguration.class)
class ReplayReportIntegrationIT extends PostgresContainerSupport {

  private static final String ACCOUNT = "IT-REPLAY-REPORT";
  private static final String TICKER = "SBER";
  private static final String CLASS_CODE = "TQBR";
  private static final String STRATEGY = "replay-strategy";
  private static final Instant DAY_ONE = Instant.parse("2026-11-02T07:00:00Z");
  private static final Instant DAY_TWO = Instant.parse("2026-11-03T07:00:00Z");
  private static final LocalDate TRADE_DATE_ONE = LocalDate.of(2026, 11, 2);
  private static final LocalDate TRADE_DATE_TWO = LocalDate.of(2026, 11, 3);

  @Autowired private ReportService reportService;
  @Autowired private OrderPlacementService placementService;
  @Autowired private OrderService orderService;
  @Autowired private MarginSettlementService settlementService;
  @Autowired private AccountService accountService;
  @Autowired private Clock clock;
  @Autowired private JdbcTemplate jdbcTemplate;

  @BeforeEach
  void prepareAccountAndClock() {
    accountService.ensureAccount();
    ((VirtualClock) clock).advanceTo(DAY_ONE.plus(Duration.ofHours(3)));
  }

  @AfterEach
  void deleteAccountData() {
    jdbcTemplate.update("DELETE FROM backtest_results WHERE account_id = ?", ACCOUNT);
    jdbcTemplate.update("DELETE FROM reports WHERE account_id = ?", ACCOUNT);
    jdbcTemplate.update(
        "DELETE FROM order_events WHERE order_id IN (SELECT order_id FROM orders WHERE account_id = ?)",
        ACCOUNT);
    jdbcTemplate.update("DELETE FROM fills WHERE account_id = ?", ACCOUNT);
    jdbcTemplate.update("DELETE FROM orders WHERE account_id = ?", ACCOUNT);
    jdbcTemplate.update("DELETE FROM positions WHERE account_id = ?", ACCOUNT);
    jdbcTemplate.update("DELETE FROM nav_daily WHERE account_id = ?", ACCOUNT);
    jdbcTemplate.update("DELETE FROM account_balances WHERE account_id = ?", ACCOUNT);
    jdbcTemplate.update("DELETE FROM accounts WHERE account_id = ?", ACCOUNT);
  }

  @Test
  void replayRunProducesReportWithJournalSlippageAndFifoWinRate() throws Exception {
    Order buy = place(Side.BUY);
    assertThat(orderService.get(OrderIdType.EXCHANGE, buy.orderId()).status())
        .isEqualTo(OrderStatus.FILLED);
    settlementService.closeDay(TRADE_DATE_ONE);

    ((VirtualClock) clock).advanceTo(DAY_TWO.plus(Duration.ofHours(3)));
    Order sell = place(Side.SELL);
    assertThat(orderService.get(OrderIdType.EXCHANGE, sell.orderId()).status())
        .isEqualTo(OrderStatus.FILLED);
    settlementService.closeDay(TRADE_DATE_TWO);

    ReportService.StoredReport stored =
        reportService.generate(ACCOUNT, "REPLAY-IT", TRADE_DATE_ONE, TRADE_DATE_TWO, null);

    assertThat(stored.payload().accountId()).isEqualTo(ACCOUNT);
    assertThat(stored.payload().label()).isEqualTo("REPLAY-IT");
    assertThat(stored.payload().navSeries()).hasSize(2);
    assertThat(stored.payload().navSeries())
        .extracting(point -> point.date())
        .containsExactly(TRADE_DATE_ONE, TRADE_DATE_TWO);
    assertThat(stored.payload().metrics().observations()).isEqualTo(2);

    assertThat(stored.payload().journal()).hasSize(2);
    assertThat(stored.payload().journal())
        .extracting(entry -> entry.strategyId())
        .containsOnly(STRATEGY);
    assertThat(stored.payload().journal())
        .allSatisfy(
            entry -> {
              assertThat(entry.referencePrice()).isPositive();
              assertThat(entry.slippagePerUnit())
                  .as("маркет исполняется с неблагоприятным проскальзыванием")
                  .isPositive();
              assertThat(entry.commission()).isPositive();
            });
    assertThat(stored.payload().slippageTotal()).isPositive();
    assertThat(stored.payload().commissionTotal()).isPositive();

    assertThat(stored.payload().closedTrades()).hasSize(1);
    assertThat(stored.payload().closedTrades().get(0).quantity()).isEqualTo(10);
    assertThat(stored.payload().winRate()).isEqualByComparingTo("1.000000");
    assertThat(stored.payload().strategies())
        .singleElement()
        .satisfies(
            strategy -> {
              assertThat(strategy.strategyId()).isEqualTo(STRATEGY);
              assertThat(strategy.tradeCount()).isEqualTo(2);
              assertThat(strategy.closedTradeCount()).isEqualTo(1);
              assertThat(strategy.realizedPnl()).isPositive();
            });

    Path json = Path.of(stored.files().get("json"));
    Path csv = Path.of(stored.files().get("csv"));
    assertThat(Files.exists(json)).isTrue();
    assertThat(Files.exists(csv)).isTrue();
    assertThat(Files.readString(json))
        .contains("\"accountId\":\"IT-REPLAY-REPORT\"")
        .contains("\"strategyId\":\"replay-strategy\"");
    assertThat(Files.readString(csv))
        .startsWith("executedAt,orderId")
        .contains("SBER")
        .contains(STRATEGY);

    assertThat(reportService.journalCsv(stored.id()))
        .startsWith("executedAt,orderId")
        .contains(STRATEGY);
  }

  private Order place(Side side) {
    return placementService.place(
        new NewOrder(
            UUID.randomUUID(),
            TICKER,
            CLASS_CODE,
            side,
            OrderType.MARKET,
            TimeInForce.DAY,
            10,
            null,
            null,
            STRATEGY));
  }

  /** Детерминированный провайдер REPLAY и фиксированный календарь двух сессий. */
  @TestConfiguration
  static class ReplayMarketConfiguration {

    private static final ZoneId MOSCOW = ZoneId.of("Europe/Moscow");

    @Bean
    TradingCalendar replayCalendar() {
      return new FixedTradingCalendar();
    }

    @Bean
    @Primary
    MarketDataProvider replayMarketData(Clock clock) {
      Instrument sber =
          new Instrument(
              TICKER,
              CLASS_CODE,
              "STOCK",
              "Сбербанк",
              "Сбербанк",
              "RU0009029540",
              new BigDecimal("10"),
              new BigDecimal("0.01"),
              "RUB");
      return new ReplayMarketDataProvider(
          timeline(),
          (VirtualClock) clock,
          Map.of(TICKER + "|" + CLASS_CODE, sber),
          FixedTradingCalendar.days());
    }

    private static List<MarketDataSnapshot> timeline() {
      return List.of(
          new QuoteSnapshot(DAY_ONE, quote(DAY_ONE, "285.50", "285.49", "285.51")),
          new OrderBookEvent(DAY_ONE, book(DAY_ONE, "285.49", "285.51")),
          new QuoteSnapshot(DAY_TWO, quote(DAY_TWO, "290.00", "289.99", "290.01")),
          new OrderBookEvent(DAY_TWO, book(DAY_TWO, "289.99", "290.01")));
    }

    private static Quote quote(Instant at, String last, String bid, String offer) {
      BigDecimal lastPrice = new BigDecimal(last);
      return new Quote(
          TICKER,
          CLASS_CODE,
          at,
          1,
          "RUB",
          new BigDecimal(bid),
          new BigDecimal(offer),
          lastPrice,
          lastPrice,
          lastPrice,
          lastPrice,
          lastPrice,
          BigDecimal.ZERO,
          BigDecimal.ZERO);
    }

    private static OrderBookSnapshot book(Instant at, String bid, String offer) {
      return new OrderBookSnapshot(
          TICKER,
          CLASS_CODE,
          10,
          at,
          List.of(new OrderBookSnapshot.Level(new BigDecimal(bid), 1000)),
          List.of(new OrderBookSnapshot.Level(new BigDecimal(offer), 1000)));
    }

    /** Календарь двух торговых дней: 07:00–15:00 UTC. */
    static final class FixedTradingCalendar implements TradingCalendar {

      private static final List<TradingDay> DAYS =
          List.of(
              new TradingDay(TRADE_DATE_ONE, true, DAY_ONE, Instant.parse("2026-11-02T15:00:00Z")),
              new TradingDay(TRADE_DATE_TWO, true, DAY_TWO, Instant.parse("2026-11-03T15:00:00Z")));

      static List<TradingDay> days() {
        return DAYS;
      }

      @Override
      public TradingSession tradingSession(Instant at) {
        LocalDate date = at.atZone(MOSCOW).toLocalDate();
        for (TradingDay day : DAYS) {
          if (day.date().equals(date)
              && !at.isBefore(day.openUtc())
              && !at.isAfter(day.closeUtc())) {
            return new TradingSession(1, "Основная сессия", true, day.closeUtc());
          }
        }
        Instant next =
            DAYS.stream()
                .map(TradingDay::openUtc)
                .filter(open -> open.isAfter(at))
                .findFirst()
                .orElse(at);
        return new TradingSession(1, "Основная сессия", false, next);
      }

      @Override
      public List<TradingDay> tradingDays(Instant from, Instant to) {
        LocalDate fromDate = from.atZone(MOSCOW).toLocalDate();
        LocalDate toDate = to.atZone(MOSCOW).toLocalDate();
        return DAYS.stream()
            .filter(day -> !day.date().isBefore(fromDate) && !day.date().isAfter(toDate))
            .toList();
      }
    }
  }
}
