package ru.moex.papertrader.infra.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import ru.moex.papertrader.core.domain.OrderStatus;
import ru.moex.papertrader.core.domain.OrderType;
import ru.moex.papertrader.core.domain.Side;
import ru.moex.papertrader.core.domain.TimeInForce;
import ru.moex.papertrader.core.reporting.BacktestInput;
import ru.moex.papertrader.core.reporting.MetricsCalculator;
import ru.moex.papertrader.core.reporting.NavPoint;
import ru.moex.papertrader.infra.model.FillEntity;
import ru.moex.papertrader.infra.model.InstrumentEntity;
import ru.moex.papertrader.infra.model.NavDailyEntity;
import ru.moex.papertrader.infra.model.OrderEntity;
import ru.moex.papertrader.infra.model.ReportEntity;
import ru.moex.papertrader.infra.repository.BacktestResultRepository;
import ru.moex.papertrader.infra.repository.FillRepository;
import ru.moex.papertrader.infra.repository.InstrumentRepository;
import ru.moex.papertrader.infra.repository.NavDailyRepository;
import ru.moex.papertrader.infra.repository.OrderRepository;
import ru.moex.papertrader.infra.repository.ReportRepository;
import ru.moex.papertrader.infra.services.exception.InvalidRequestException;
import ru.moex.papertrader.testutil.FixedClock;
import tools.jackson.databind.json.JsonMapper;

class ReportServiceImplTest {

  private static final String ACCOUNT = "ACC-1";
  private static final Instant NOW = Instant.parse("2026-11-04T12:00:00Z");
  private static final LocalDate FROM = LocalDate.of(2026, 11, 2);
  private static final LocalDate TO = LocalDate.of(2026, 11, 3);

  @TempDir private Path outputDir;

  private final NavDailyRepository navDaily = mock(NavDailyRepository.class);
  private final FillRepository fills = mock(FillRepository.class);
  private final OrderRepository orders = mock(OrderRepository.class);
  private final InstrumentRepository instruments = mock(InstrumentRepository.class);
  private final ReportRepository reports = mock(ReportRepository.class);
  private final BacktestResultRepository backtests = mock(BacktestResultRepository.class);

  private ReportServiceImpl service;

  @BeforeEach
  void setUp() {
    service =
        new ReportServiceImpl(
            FixedClock.at(NOW),
            new ReportProperties(
                new BigDecimal("0.16"), new BigDecimal("0.30"), outputDir.toString()),
            new PortfolioProperties(ACCOUNT, new BigDecimal("1000000"), "RUB", false),
            navDaily,
            fills,
            orders,
            instruments,
            reports,
            backtests,
            JsonMapper.builder().findAndAddModules().build());
  }

  @Test
  void generateBuildsPayloadExportsFilesAndStoresBacktest() throws Exception {
    when(navDaily.findByAccountIdAndNavDateBetweenOrderByNavDateAsc(ACCOUNT, FROM, TO))
        .thenReturn(
            List.of(
                navRow(LocalDate.of(2026, 11, 2), "1000000"),
                navRow(LocalDate.of(2026, 11, 3), "1010000")));
    when(fills.findByAccountIdOrderByExecutedAtAscIdAsc(ACCOUNT))
        .thenReturn(
            List.of(
                fill(1L, "ORD-1", Side.BUY, 10, "100.5", "100.0", "1.01", "2026-11-02T10:00:00Z"),
                fill(
                    2L, "ORD-2", Side.SELL, 10, "105.0", "105.5", "1.05", "2026-11-03T10:00:00Z")));
    when(orders.findAllById(any()))
        .thenReturn(
            List.of(
                order("ORD-1", 1, 10L, Side.BUY, "strategy-a"),
                order("ORD-2", 2, 10L, Side.SELL, "strategy-a")));
    when(instruments.findAllById(any())).thenReturn(List.of(instrument(10L)));
    ReportEntity saved = mock(ReportEntity.class);
    when(saved.getId()).thenReturn(42L);
    when(reports.saveAndFlush(any())).thenReturn(saved);

    BigDecimal expectedCagr =
        new MetricsCalculator(BigDecimal.ZERO)
            .calculate(
                List.of(
                    new NavPoint(LocalDate.of(2026, 11, 2), new BigDecimal("1000000")),
                    new NavPoint(LocalDate.of(2026, 11, 3), new BigDecimal("1010000"))))
            .cagr();
    BacktestInput backtest =
        new BacktestInput("benchmark", List.of(), Map.of("cagr", expectedCagr));

    ReportService.StoredReport result = service.generate(ACCOUNT, "run", FROM, TO, backtest);

    assertThat(result.id()).isEqualTo(42);
    assertThat(result.payload().accountId()).isEqualTo(ACCOUNT);
    assertThat(result.payload().label()).isEqualTo("run");
    assertThat(result.payload().navSeries()).hasSize(2);
    assertThat(result.payload().metrics().cagr()).isEqualByComparingTo(expectedCagr);
    assertThat(result.payload().journal()).hasSize(2);
    assertThat(result.payload().journal().get(0).strategyId()).isEqualTo("strategy-a");
    assertThat(result.payload().journal().get(0).slippageTotal()).isEqualByComparingTo("5.00");
    assertThat(result.payload().journal().get(1).slippageTotal()).isEqualByComparingTo("5.00");
    assertThat(result.payload().slippageTotal()).isEqualByComparingTo("10.00");
    assertThat(result.payload().commissionTotal()).isEqualByComparingTo("2.06");
    assertThat(result.payload().winRate()).isEqualByComparingTo("1.000000");
    assertThat(result.payload().strategies())
        .singleElement()
        .satisfies(
            entry -> {
              assertThat(entry.strategyId()).isEqualTo("strategy-a");
              assertThat(entry.realizedPnl()).isEqualByComparingTo("42.94");
            });
    assertThat(result.payload().backtest().passed()).isTrue();

    Path jsonFile = Path.of(result.files().get("json"));
    Path csvFile = Path.of(result.files().get("csv"));
    assertThat(Files.readString(jsonFile)).contains("\"accountId\":\"ACC-1\"");
    assertThat(Files.readString(csvFile)).contains("executedAt,orderId").contains("SBER");

    ArgumentCaptor<ReportEntity> reportCaptor = ArgumentCaptor.forClass(ReportEntity.class);
    verify(reports).saveAndFlush(reportCaptor.capture());
    assertThat(reportCaptor.getValue().getPayload()).contains("\"winRate\":1.000000");
    verify(backtests).save(any());
  }

  @Test
  void savedReportCanBeReadAndExportedAsCsv() {
    ReportEntity entity = mock(ReportEntity.class);
    when(entity.getId()).thenReturn(7L);
    when(entity.getPayload())
        .thenReturn(
            "{\"accountId\":\"ACC-1\",\"label\":\"run\",\"generatedAt\":\"2026-11-04T12:00:00Z\","
                + "\"periodFrom\":\"2026-11-02\",\"periodTo\":\"2026-11-03\","
                + "\"navSeries\":[],\"metrics\":{\"cagr\":null,\"sharpe\":null,\"sortino\":null,"
                + "\"maxDrawdown\":null,\"calmar\":null,\"observations\":0},"
                + "\"journal\":[],\"slippageTotal\":0.00,\"commissionTotal\":0.00,"
                + "\"closedTrades\":[],\"winRate\":null,\"strategies\":[],\"backtest\":null}");
    when(reports.findById(7L)).thenReturn(Optional.of(entity));

    ReportService.StoredReport stored = service.find(7).orElseThrow();

    assertThat(stored.payload().accountId()).isEqualTo(ACCOUNT);
    assertThat(service.journalCsv(7)).startsWith("executedAt,orderId");
  }

  @Test
  void invalidPeriodIsRejected() {
    assertThatThrownBy(() -> service.generate(ACCOUNT, null, TO, FROM, null))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("to не может быть раньше from");
  }

  @Test
  void emptyPeriodProducesDefinedEmptyReport() {
    when(navDaily.findByAccountIdAndNavDateBetweenOrderByNavDateAsc(ACCOUNT, FROM, TO))
        .thenReturn(List.of());
    when(fills.findByAccountIdOrderByExecutedAtAscIdAsc(ACCOUNT)).thenReturn(List.of());
    ReportEntity saved = mock(ReportEntity.class);
    when(saved.getId()).thenReturn(8L);
    when(reports.saveAndFlush(any())).thenReturn(saved);

    ReportService.StoredReport result = service.generate(ACCOUNT, null, FROM, TO, null);

    assertThat(result.payload().navSeries()).isEmpty();
    assertThat(result.payload().metrics().cagr()).isNull();
    assertThat(result.payload().winRate()).isNull();
    assertThat(result.payload().backtest()).isNull();
    assertThat(result.payload().journal()).isEmpty();
    assertThat(result.payload().strategies()).isEmpty();
  }

  private static NavDailyEntity navRow(LocalDate date, String nav) {
    BigDecimal value = new BigDecimal(nav);
    return new NavDailyEntity(
        ACCOUNT, date, NOW, value, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, value, "[]");
  }

  private static FillEntity fill(
      Long id,
      String orderId,
      Side side,
      long quantity,
      String price,
      String referencePrice,
      String commission,
      String at) {
    FillEntity entity =
        new FillEntity(
            orderId,
            ACCOUNT,
            10L,
            new BigDecimal(price),
            new BigDecimal(referencePrice),
            quantity,
            new BigDecimal(commission),
            Instant.parse(at));
    ReflectionTestUtils.setField(entity, "id", id);
    return entity;
  }

  private static OrderEntity order(
      String orderId, long orderNum, Long instrumentId, Side side, String strategyId) {
    return new OrderEntity(
        orderId,
        orderNum,
        UUID.nameUUIDFromBytes(orderId.getBytes(java.nio.charset.StandardCharsets.UTF_8)),
        ACCOUNT,
        instrumentId,
        side,
        OrderType.LIMIT,
        TimeInForce.DAY,
        10,
        new BigDecimal("100"),
        null,
        "ACC-1",
        strategyId,
        NOW,
        NOW,
        OrderStatus.FILLED,
        10,
        new BigDecimal("100"));
  }

  private static InstrumentEntity instrument(Long id) {
    InstrumentEntity entity =
        new InstrumentEntity(
            "SBER",
            "TQBR",
            "STOCK",
            "Сбербанк",
            "Сбербанк",
            "RU0009029540",
            new BigDecimal("10"),
            new BigDecimal("0.01"),
            "RUB");
    ReflectionTestUtils.setField(entity, "id", id);
    return entity;
  }
}
