package ru.moex.papertrader.infra.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import ru.moex.papertrader.PostgresContainerSupport;
import ru.moex.papertrader.infra.model.AccountEntity;
import ru.moex.papertrader.infra.model.ReportEntity;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Сохранённый отчёт: JSONB-полезная нагрузка, сортировка списка, ограничения счёта и периода.
 *
 * <p>Полезная нагрузка сравнивается через {@link JsonNode}: {@code jsonb} нормализует текст JSON
 * (порядок ключей и пробелы), поэтому сравнивать строку посимвольно нельзя.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class ReportRepositoryIT extends PostgresContainerSupport {

  // Уникальные счета на класс: БД общая для всех тестов JVM (см. OrderRepositoryIT).
  private static final String ACCOUNT_ID = "IT-" + UUID.randomUUID();
  private static final String OTHER_ACCOUNT_ID = "IT-" + UUID.randomUUID();

  private static final Instant CREATED_AT = Instant.parse("2026-11-03T08:00:00Z");
  private static final Instant GENERATED_AT_OLD = Instant.parse("2026-11-03T09:00:00Z");
  private static final Instant GENERATED_AT_NEW = Instant.parse("2026-11-03T12:00:00Z");
  private static final LocalDate PERIOD_FROM = LocalDate.of(2026, 10, 1);
  private static final LocalDate PERIOD_TO = LocalDate.of(2026, 10, 31);
  private static final String PAYLOAD =
      "{\"accountId\":\"account\",\"label\":\"october\",\"metrics\":{\"maxDrawdown\":0}}";

  private static final JsonMapper JSON = JsonMapper.builder().build();

  @Autowired private AccountRepository accounts;
  @Autowired private ReportRepository reports;
  @Autowired private EntityManager entityManager;
  @Autowired private JdbcTemplate jdbcTemplate;

  @Test
  void savesAndReadsReportWithJsonbPayload() {
    accounts.saveAndFlush(new AccountEntity(ACCOUNT_ID, CREATED_AT));
    ReportEntity saved =
        reports.saveAndFlush(
            new ReportEntity(
                ACCOUNT_ID, "october", PERIOD_FROM, PERIOD_TO, GENERATED_AT_NEW, PAYLOAD));

    entityManager.clear();

    ReportEntity loaded = reports.findById(saved.getId()).orElseThrow();
    String payloadColumnType =
        jdbcTemplate.queryForObject(
            "SELECT data_type FROM information_schema.columns"
                + " WHERE table_name = 'reports' AND column_name = 'payload'",
            String.class);
    String maxDrawdownFromJson =
        jdbcTemplate.queryForObject(
            "SELECT payload -> 'metrics' ->> 'maxDrawdown' FROM reports WHERE id = ?",
            String.class,
            saved.getId());

    assertThat(loaded.getId()).isNotNull();
    assertThat(loaded.getAccountId()).isEqualTo(ACCOUNT_ID);
    assertThat(loaded.getLabel()).isEqualTo("october");
    assertThat(loaded.getPeriodFrom()).isEqualTo(PERIOD_FROM);
    assertThat(loaded.getPeriodTo()).isEqualTo(PERIOD_TO);
    assertThat(loaded.getGeneratedAt()).isEqualTo(GENERATED_AT_NEW);
    assertThat(JSON.readTree(loaded.getPayload())).isEqualTo(JSON.readTree(PAYLOAD));
    assertThat(payloadColumnType).isEqualTo("jsonb");
    assertThat(maxDrawdownFromJson).isEqualTo("0");
  }

  @Test
  void allowsReportWithoutLabel() {
    accounts.saveAndFlush(new AccountEntity(ACCOUNT_ID, CREATED_AT));
    ReportEntity saved =
        reports.saveAndFlush(
            new ReportEntity(ACCOUNT_ID, null, PERIOD_FROM, PERIOD_TO, GENERATED_AT_NEW, PAYLOAD));

    entityManager.clear();

    assertThat(reports.findById(saved.getId()).orElseThrow().getLabel()).isNull();
  }

  @Test
  void listsAccountReportsNewestFirst() {
    accounts.saveAndFlush(new AccountEntity(ACCOUNT_ID, CREATED_AT));
    long olderId = persist(ACCOUNT_ID, "older", GENERATED_AT_OLD).getId();
    long newerId = persist(ACCOUNT_ID, "newer", GENERATED_AT_NEW).getId();

    entityManager.clear();

    assertThat(reports.findByAccountIdOrderByGeneratedAtDescIdDesc(ACCOUNT_ID))
        .extracting(ReportEntity::getId)
        .containsExactly(newerId, olderId);
  }

  @Test
  void listsAllReportsNewestFirstAcrossAccounts() {
    accounts.saveAndFlush(new AccountEntity(ACCOUNT_ID, CREATED_AT));
    accounts.saveAndFlush(new AccountEntity(OTHER_ACCOUNT_ID, CREATED_AT));
    long olderId = persist(ACCOUNT_ID, "older", GENERATED_AT_OLD).getId();
    long newerId = persist(OTHER_ACCOUNT_ID, "newer", GENERATED_AT_NEW).getId();

    entityManager.clear();

    List<ReportEntity> all = reports.findAllByOrderByGeneratedAtDescIdDesc();

    assertThat(all)
        .filteredOn(
            row ->
                ACCOUNT_ID.equals(row.getAccountId())
                    || OTHER_ACCOUNT_ID.equals(row.getAccountId()))
        .extracting(ReportEntity::getId)
        .containsExactly(newerId, olderId);
    assertThat(all)
        .extracting(ReportEntity::getGeneratedAt)
        .isSortedAccordingTo(Comparator.<Instant>naturalOrder().reversed());
  }

  @Test
  void rejectsReportForUnknownAccount() {
    assertThatThrownBy(
            () ->
                reports.saveAndFlush(
                    new ReportEntity(
                        "UNKNOWN-ACCOUNT",
                        "orphan",
                        PERIOD_FROM,
                        PERIOD_TO,
                        GENERATED_AT_NEW,
                        PAYLOAD)))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void rejectsPeriodEndingBeforeItStarts() {
    accounts.saveAndFlush(new AccountEntity(ACCOUNT_ID, CREATED_AT));

    assertThatThrownBy(
            () ->
                reports.saveAndFlush(
                    new ReportEntity(
                        ACCOUNT_ID, "reversed", PERIOD_TO, PERIOD_FROM, GENERATED_AT_NEW, PAYLOAD)))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  private ReportEntity persist(String accountId, String label, Instant generatedAt) {
    return reports.saveAndFlush(
        new ReportEntity(accountId, label, PERIOD_FROM, PERIOD_TO, generatedAt, payloadFor(label)));
  }

  /** Метка в JSON повторяет колонку {@code label}: payload остаётся осмысленным снапшотом. */
  private static String payloadFor(String label) {
    return PAYLOAD.replace("october", label);
  }
}
