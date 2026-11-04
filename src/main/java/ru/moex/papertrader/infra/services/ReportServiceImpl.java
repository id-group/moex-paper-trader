package ru.moex.papertrader.infra.services;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.moex.papertrader.core.marketdata.Clock;
import ru.moex.papertrader.core.reporting.BacktestComparator;
import ru.moex.papertrader.core.reporting.BacktestComparison;
import ru.moex.papertrader.core.reporting.BacktestInput;
import ru.moex.papertrader.core.reporting.JournalEntryView;
import ru.moex.papertrader.core.reporting.MetricsCalculator;
import ru.moex.papertrader.core.reporting.NavPoint;
import ru.moex.papertrader.core.reporting.PerformanceMetrics;
import ru.moex.papertrader.core.reporting.ReportPayload;
import ru.moex.papertrader.core.reporting.TradeJournal;
import ru.moex.papertrader.core.reporting.TradeJournalEntry;
import ru.moex.papertrader.infra.model.BacktestResultEntity;
import ru.moex.papertrader.infra.model.FillEntity;
import ru.moex.papertrader.infra.model.InstrumentEntity;
import ru.moex.papertrader.infra.model.OrderEntity;
import ru.moex.papertrader.infra.model.ReportEntity;
import ru.moex.papertrader.infra.repository.BacktestResultRepository;
import ru.moex.papertrader.infra.repository.FillRepository;
import ru.moex.papertrader.infra.repository.InstrumentRepository;
import ru.moex.papertrader.infra.repository.NavDailyRepository;
import ru.moex.papertrader.infra.repository.OrderRepository;
import ru.moex.papertrader.infra.repository.ReportRepository;
import ru.moex.papertrader.infra.services.exception.InvalidRequestException;
import ru.moex.papertrader.infra.services.exception.ResourceNotFoundException;
import tools.jackson.core.JacksonException;
import tools.jackson.core.StreamWriteFeature;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.ObjectReader;
import tools.jackson.databind.ObjectWriter;
import tools.jackson.databind.cfg.DateTimeFeature;

/**
 * Сборка отчёта из PostgreSQL, файловый экспорт и хранение результата.
 *
 * <p>Период журнала — календарные дни UTC {@code [from 00:00, to+1 00:00)}: это та же граница, по
 * которой этап 3 считает комиссию дня NAV (временное решение №16). Кривая NAV берётся из {@code
 * nav_daily} включительно по обеим датам.
 *
 * <p>Отчёт и результат сравнения пишутся в одной транзакции; файлы создаются после получения
 * идентификатора. Если файловая запись не удалась, транзакция откатывается (осиротевшие файлы
 * возможны — известное ограничение файлового экспорта).
 */
@Service
public class ReportServiceImpl implements ReportService {

  private static final Logger log = LoggerFactory.getLogger(ReportServiceImpl.class);

  private final Clock clock;
  private final ReportProperties properties;
  private final PortfolioProperties portfolioProperties;
  private final NavDailyRepository navDaily;
  private final FillRepository fills;
  private final OrderRepository orders;
  private final InstrumentRepository instruments;
  private final ReportRepository reports;
  private final BacktestResultRepository backtests;
  private final ObjectWriter payloadWriter;
  private final ObjectReader payloadReader;

  public ReportServiceImpl(
      Clock clock,
      ReportProperties properties,
      PortfolioProperties portfolioProperties,
      NavDailyRepository navDaily,
      FillRepository fills,
      OrderRepository orders,
      InstrumentRepository instruments,
      ReportRepository reports,
      BacktestResultRepository backtests,
      ObjectMapper objectMapper) {
    this.clock = clock;
    this.properties = properties;
    this.portfolioProperties = portfolioProperties;
    this.navDaily = navDaily;
    this.fills = fills;
    this.orders = orders;
    this.instruments = instruments;
    this.reports = reports;
    this.backtests = backtests;
    this.payloadWriter =
        objectMapper
            .writer()
            .with(StreamWriteFeature.WRITE_BIGDECIMAL_AS_PLAIN)
            .without(DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS);
    this.payloadReader =
        objectMapper
            .readerFor(ReportPayload.class)
            .without(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
  }

  @Override
  @Transactional
  public StoredReport generate(
      String accountId, String label, LocalDate from, LocalDate to, BacktestInput backtest) {
    String account = account(accountId);
    validatePeriod(from, to);
    Instant generatedAt = clock.instant();

    List<NavPoint> navSeries = navSeries(account, from, to);
    TradeJournal journal = journal(account, from, to);
    PerformanceMetrics metrics = calculator().calculate(navSeries);
    BigDecimal winRate = journal.winRate();
    BacktestComparison comparison = compare(backtest, metrics, winRate);

    ReportPayload payload =
        new ReportPayload(
            account,
            normalizeLabel(label),
            generatedAt,
            from,
            to,
            navSeries,
            metrics,
            journal.views(),
            journal.slippageTotal(),
            journal.commissionTotal(),
            journal.closedTrades(),
            winRate,
            journal.strategyBreakdown(),
            comparison);

    String json = write(payload);
    ReportEntity saved =
        reports.saveAndFlush(
            new ReportEntity(account, payload.label(), from, to, generatedAt, json));
    Map<String, String> files = export(saved.getId(), json, journal.toCsv());
    if (comparison != null) {
      saveBacktest(saved.getId(), account, backtest, comparison, generatedAt);
    }
    log.debug(
        "Отчёт {} по счёту {} за {}..{}: {} точек NAV, {} сделок",
        saved.getId(),
        account,
        from,
        to,
        navSeries.size(),
        journal.views().size());
    return new StoredReport(saved.getId(), payload, files);
  }

  @Override
  @Transactional(readOnly = true)
  public List<ReportSummary> list(String accountId) {
    List<ReportEntity> rows =
        accountId == null || accountId.isBlank()
            ? reports.findAllByOrderByGeneratedAtDescIdDesc()
            : reports.findByAccountIdOrderByGeneratedAtDescIdDesc(accountId.trim());
    return rows.stream()
        .map(
            entity ->
                new ReportSummary(
                    entity.getId(),
                    entity.getAccountId(),
                    entity.getLabel(),
                    entity.getPeriodFrom(),
                    entity.getPeriodTo(),
                    entity.getGeneratedAt()))
        .toList();
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<StoredReport> find(long id) {
    return reports.findById(id).map(this::stored);
  }

  @Override
  @Transactional(readOnly = true)
  public String journalCsv(long id) {
    ReportEntity entity =
        reports
            .findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Отчёт " + id + " не найден"));
    ReportPayload payload = read(entity.getPayload());
    List<TradeJournalEntry> entries =
        payload.journal().stream().map(ReportServiceImpl::entry).toList();
    return new TradeJournal(entries).toCsv();
  }

  private StoredReport stored(ReportEntity entity) {
    ReportPayload payload = read(entity.getPayload());
    return new StoredReport(entity.getId(), payload, files(entity.getId()));
  }

  private BacktestComparison compare(
      BacktestInput input, PerformanceMetrics metrics, BigDecimal winRate) {
    if (input == null) {
      return null;
    }
    Map<String, BigDecimal> actual = new LinkedHashMap<>(BacktestComparator.toMap(metrics));
    actual.put("winRate", winRate);
    Map<String, BigDecimal> benchmark =
        input.hasMetrics()
            ? input.metrics()
            : BacktestComparator.toMap(calculator().calculate(input.nav()));
    return new BacktestComparator().compare(actual, benchmark, properties.backtestThreshold());
  }

  private void saveBacktest(
      long reportId,
      String account,
      BacktestInput input,
      BacktestComparison comparison,
      Instant comparedAt) {
    String backtestLabel = input.label() == null ? "external" : input.label();
    String payload = write(new BacktestResultPayload(input, comparison));
    backtests.save(
        new BacktestResultEntity(
            reportId,
            account,
            backtestLabel,
            comparison.threshold(),
            comparison.passed(),
            comparedAt,
            payload));
    log.debug(
        "Сравнение отчёта {} с бэктестом {}: {}",
        reportId,
        backtestLabel,
        comparison.passed() ? "PASS" : "FAIL");
  }

  private List<NavPoint> navSeries(String account, LocalDate from, LocalDate to) {
    return navDaily.findByAccountIdAndNavDateBetweenOrderByNavDateAsc(account, from, to).stream()
        .map(row -> new NavPoint(row.getNavDate(), row.getNav()))
        .toList();
  }

  private TradeJournal journal(String account, LocalDate from, LocalDate to) {
    Instant fromInclusive = from.atStartOfDay(ZoneOffset.UTC).toInstant();
    Instant toExclusive = to.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
    List<FillEntity> periodFills =
        fills.findByAccountIdOrderByExecutedAtAscIdAsc(account).stream()
            .filter(fill -> !fill.getExecutedAt().isBefore(fromInclusive))
            .filter(fill -> fill.getExecutedAt().isBefore(toExclusive))
            .toList();
    if (periodFills.isEmpty()) {
      return new TradeJournal(List.of());
    }

    Map<String, OrderEntity> ordersById =
        orders
            .findAllById(
                periodFills.stream().map(FillEntity::getOrderId).collect(Collectors.toSet()))
            .stream()
            .collect(Collectors.toMap(OrderEntity::getOrderId, Function.identity()));
    Set<Long> instrumentIds =
        periodFills.stream().map(FillEntity::getInstrumentId).collect(Collectors.toSet());
    Map<Long, InstrumentEntity> instrumentsById =
        instruments.findAllById(instrumentIds).stream()
            .collect(Collectors.toMap(InstrumentEntity::getId, Function.identity()));

    List<TradeJournalEntry> entries =
        periodFills.stream()
            .map(
                fill ->
                    entry(
                        fill,
                        required(ordersById, fill.getOrderId(), "заявка"),
                        required(instrumentsById, fill.getInstrumentId(), "инструмент")))
            .toList();
    return new TradeJournal(entries);
  }

  private static TradeJournalEntry entry(
      FillEntity fill, OrderEntity order, InstrumentEntity instrument) {
    BigDecimal referencePrice =
        fill.getReferencePrice() == null ? fill.getPrice() : fill.getReferencePrice();
    return new TradeJournalEntry(
        fill.getExecutedAt(),
        order.getOrderId(),
        order.getClientOrderId(),
        instrument.getTicker(),
        instrument.getClassCode(),
        order.getSide(),
        fill.getQuantity(),
        referencePrice,
        fill.getPrice(),
        fill.getCommission(),
        order.getStrategyId());
  }

  private static TradeJournalEntry entry(JournalEntryView view) {
    return new TradeJournalEntry(
        view.executedAt(),
        view.orderId(),
        view.clientOrderId(),
        view.ticker(),
        view.classCode(),
        view.side(),
        view.quantity(),
        view.referencePrice(),
        view.price(),
        view.commission(),
        view.strategyId());
  }

  private static <T> T required(Map<?, T> values, Object key, String what) {
    T value = values.get(key);
    if (value == null) {
      throw new IllegalStateException("Не найден " + what + " " + key + " для журнала отчёта");
    }
    return value;
  }

  private Map<String, String> export(long id, String json, String csv) {
    Path directory = directory();
    Path jsonFile = directory.resolve("report-" + id + ".json");
    Path csvFile = directory.resolve("report-" + id + "-journal.csv");
    writeFile(jsonFile, json);
    writeFile(csvFile, csv);
    return Map.of("json", jsonFile.toString(), "csv", csvFile.toString());
  }

  private Map<String, String> files(long id) {
    Path directory = directory();
    return Map.of(
        "json", directory.resolve("report-" + id + ".json").toString(),
        "csv", directory.resolve("report-" + id + "-journal.csv").toString());
  }

  private Path directory() {
    return Path.of(properties.outputDir()).toAbsolutePath().normalize();
  }

  private static void writeFile(Path file, String content) {
    try {
      Files.createDirectories(file.getParent());
      Files.writeString(file, content, StandardCharsets.UTF_8);
    } catch (IOException exception) {
      throw new IllegalStateException("Не удалось записать файл отчёта " + file, exception);
    }
  }

  private String write(Object value) {
    try {
      return payloadWriter.writeValueAsString(value);
    } catch (JacksonException exception) {
      throw new IllegalStateException("Не удалось сериализовать отчёт", exception);
    }
  }

  private ReportPayload read(String payload) {
    try {
      return payloadReader.readValue(payload);
    } catch (JacksonException exception) {
      throw new IllegalStateException("Не удалось разобрать сохранённый отчёт", exception);
    }
  }

  private String account(String accountId) {
    if (accountId == null || accountId.isBlank()) {
      return portfolioProperties.account();
    }
    String trimmed = accountId.trim();
    if (trimmed.length() > 64) {
      throw new InvalidRequestException("accountId не может быть длиннее 64 символов");
    }
    return trimmed;
  }

  private static void validatePeriod(LocalDate from, LocalDate to) {
    Objects.requireNonNull(from, "from");
    Objects.requireNonNull(to, "to");
    if (to.isBefore(from)) {
      throw new InvalidRequestException("to не может быть раньше from");
    }
  }

  private static String normalizeLabel(String label) {
    if (label == null || label.isBlank()) {
      return null;
    }
    String trimmed = label.trim();
    if (trimmed.length() > 128) {
      throw new InvalidRequestException("label не может быть длиннее 128 символов");
    }
    return trimmed;
  }

  private MetricsCalculator calculator() {
    return new MetricsCalculator(properties.riskFreeRate());
  }
}
