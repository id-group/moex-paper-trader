package ru.moex.papertrader.infra.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import ru.moex.papertrader.PostgresContainerSupport;
import ru.moex.papertrader.infra.model.AccountEntity;
import ru.moex.papertrader.infra.model.BacktestResultEntity;
import ru.moex.papertrader.infra.model.ReportEntity;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Результат сравнения с бэктестом: необязательная ссылка на отчёт, JSONB-полезная нагрузка, масштаб
 * порога и сортировка списка.
 *
 * <p>Полезная нагрузка сравнивается через {@link JsonNode}: {@code jsonb} нормализует текст JSON.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class BacktestResultRepositoryIT extends PostgresContainerSupport {

  /** Уникальный счёт: БД общая для всех тестов JVM (см. OrderRepositoryIT). */
  private static final String ACCOUNT_ID = "IT-" + UUID.randomUUID();

  private static final Instant CREATED_AT = Instant.parse("2026-11-04T08:00:00Z");
  private static final Instant COMPARED_AT_OLD = Instant.parse("2026-11-04T09:00:00Z");
  private static final Instant COMPARED_AT_NEW = Instant.parse("2026-11-04T15:00:00Z");
  private static final BigDecimal THRESHOLD = new BigDecimal("0.300000");
  private static final String PAYLOAD =
      "{\"input\":{\"nav\":[1.00,2.00]},\"deviations\":[{\"metric\":\"cagr\",\"deviation\":0.01}]}";

  private static final JsonMapper JSON = JsonMapper.builder().build();

  @Autowired private AccountRepository accounts;
  @Autowired private ReportRepository reports;
  @Autowired private BacktestResultRepository backtests;
  @Autowired private EntityManager entityManager;
  @Autowired private JdbcTemplate jdbcTemplate;

  @Test
  void savesAndReadsResultLinkedToReport() {
    long reportId = persistAccountAndReport();
    BacktestResultEntity saved =
        backtests.saveAndFlush(
            new BacktestResultEntity(
                reportId, ACCOUNT_ID, "external", THRESHOLD, true, COMPARED_AT_NEW, PAYLOAD));

    entityManager.clear();

    BacktestResultEntity loaded = backtests.findById(saved.getId()).orElseThrow();
    String payloadColumnType =
        jdbcTemplate.queryForObject(
            "SELECT data_type FROM information_schema.columns"
                + " WHERE table_name = 'backtest_results' AND column_name = 'payload'",
            String.class);
    String metricFromJson =
        jdbcTemplate.queryForObject(
            "SELECT payload -> 'deviations' -> 0 ->> 'metric' FROM backtest_results WHERE id = ?",
            String.class,
            saved.getId());

    assertThat(loaded.getReportId()).isEqualTo(reportId);
    assertThat(loaded.getAccountId()).isEqualTo(ACCOUNT_ID);
    assertThat(loaded.getLabel()).isEqualTo("external");
    assertThat(loaded.getThreshold()).isEqualTo(new BigDecimal("0.300000"));
    assertThat(loaded.getThreshold().scale()).isEqualTo(6);
    assertThat(loaded.isPassed()).isTrue();
    assertThat(loaded.getComparedAt()).isEqualTo(COMPARED_AT_NEW);
    assertThat(JSON.readTree(loaded.getPayload())).isEqualTo(JSON.readTree(PAYLOAD));
    assertThat(payloadColumnType).isEqualTo("jsonb");
    assertThat(metricFromJson).isEqualTo("cagr");
  }

  @Test
  void allowsResultWithoutReport() {
    accounts.saveAndFlush(new AccountEntity(ACCOUNT_ID, CREATED_AT));
    BacktestResultEntity saved =
        backtests.saveAndFlush(
            new BacktestResultEntity(
                null, ACCOUNT_ID, "standalone", THRESHOLD, false, COMPARED_AT_NEW, PAYLOAD));

    entityManager.clear();

    assertThat(backtests.findById(saved.getId()).orElseThrow().getReportId()).isNull();
  }

  @Test
  void clearsReportReferenceWhenReportIsDeleted() {
    long reportId = persistAccountAndReport();
    BacktestResultEntity saved =
        backtests.saveAndFlush(
            new BacktestResultEntity(
                reportId, ACCOUNT_ID, "cascade", THRESHOLD, true, COMPARED_AT_NEW, PAYLOAD));

    jdbcTemplate.update("DELETE FROM reports WHERE id = ?", reportId);

    entityManager.clear();

    assertThat(backtests.findById(saved.getId()).orElseThrow().getReportId()).isNull();
  }

  @Test
  void listsAccountResultsNewestFirst() {
    accounts.saveAndFlush(new AccountEntity(ACCOUNT_ID, CREATED_AT));
    long olderId = persist(COMPARED_AT_OLD, "older").getId();
    long newerId = persist(COMPARED_AT_NEW, "newer").getId();

    entityManager.clear();

    assertThat(backtests.findByAccountIdOrderByComparedAtDescIdDesc(ACCOUNT_ID))
        .extracting(BacktestResultEntity::getId)
        .containsExactly(newerId, olderId);
  }

  @Test
  void rejectsUnknownReportReference() {
    accounts.saveAndFlush(new AccountEntity(ACCOUNT_ID, CREATED_AT));

    assertThatThrownBy(
            () ->
                backtests.saveAndFlush(
                    new BacktestResultEntity(
                        -1L, ACCOUNT_ID, "dangling", THRESHOLD, true, COMPARED_AT_NEW, PAYLOAD)))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  private long persistAccountAndReport() {
    accounts.saveAndFlush(new AccountEntity(ACCOUNT_ID, CREATED_AT));
    return reports
        .saveAndFlush(
            new ReportEntity(
                ACCOUNT_ID,
                "october",
                LocalDate.of(2026, 10, 1),
                LocalDate.of(2026, 10, 31),
                COMPARED_AT_NEW,
                "{\"label\":\"october\"}"))
        .getId();
  }

  private BacktestResultEntity persist(Instant comparedAt, String label) {
    return backtests.saveAndFlush(
        new BacktestResultEntity(
            null, ACCOUNT_ID, label, THRESHOLD, true, comparedAt, PAYLOAD.replace("cagr", label)));
  }
}
