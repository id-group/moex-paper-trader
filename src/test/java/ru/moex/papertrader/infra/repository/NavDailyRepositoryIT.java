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
import ru.moex.papertrader.PostgresContainerSupport;
import ru.moex.papertrader.infra.model.AccountEntity;
import ru.moex.papertrader.infra.model.NavDailyEntity;

/** Ежедневный NAV: чтение по паре (счёт, дата) и уникальность этой пары. */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class NavDailyRepositoryIT extends PostgresContainerSupport {

  /** Уникальный счёт: БД общая для всех тестов JVM (см. OrderRepositoryIT). */
  private static final String ACCOUNT_ID = "IT-" + UUID.randomUUID();

  private static final LocalDate NAV_DATE = LocalDate.of(2026, 11, 2);
  private static final Instant NOW = Instant.parse("2026-11-02T18:45:00Z");
  private static final String HOLDINGS =
      "[{\"ticker\":\"SBER\",\"quantity\":10,\"closingPrice\":285.500000}]";

  @Autowired private AccountRepository accounts;
  @Autowired private NavDailyRepository navDaily;
  @Autowired private EntityManager entityManager;

  @Test
  void savesAndReadsSnapshotPreservingMoneyScale() {
    accounts.saveAndFlush(new AccountEntity(ACCOUNT_ID, NOW));
    navDaily.saveAndFlush(
        new NavDailyEntity(
            ACCOUNT_ID,
            NAV_DATE,
            NOW,
            new BigDecimal("1000000.00"),
            new BigDecimal("2.86"),
            new BigDecimal("-1200.00"),
            new BigDecimal("2855.00"),
            new BigDecimal("1001652.14"),
            HOLDINGS));
    entityManager.clear();

    NavDailyEntity loaded = navDaily.findByAccountIdAndNavDate(ACCOUNT_ID, NAV_DATE).orElseThrow();

    assertThat(loaded.getAsOf()).isEqualTo(NOW);
    assertThat(loaded.getCash()).isEqualTo(new BigDecimal("1000000.00"));
    assertThat(loaded.getCommission()).isEqualTo(new BigDecimal("2.86"));
    assertThat(loaded.getVariationMargin()).isEqualTo(new BigDecimal("-1200.00"));
    assertThat(loaded.getMarketValue()).isEqualTo(new BigDecimal("2855.00"));
    assertThat(loaded.getNav()).isEqualTo(new BigDecimal("1001652.14"));
    assertThat(loaded.getCash().scale()).isEqualTo(2);
    assertThat(loaded.getHoldingsJson()).isEqualTo(HOLDINGS);
    assertThat(navDaily.findByAccountIdOrderByNavDateAsc(ACCOUNT_ID)).hasSize(1);
  }

  @Test
  void rejectsSecondSnapshotForSameAccountAndDate() {
    accounts.saveAndFlush(new AccountEntity(ACCOUNT_ID, NOW));
    navDaily.saveAndFlush(snapshot(new BigDecimal("1000000.00")));

    assertThatThrownBy(() -> navDaily.saveAndFlush(snapshot(new BigDecimal("1000001.00"))))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  private static NavDailyEntity snapshot(BigDecimal cash) {
    return new NavDailyEntity(
        ACCOUNT_ID,
        NAV_DATE,
        NOW,
        cash,
        new BigDecimal("2.86"),
        new BigDecimal("-1200.00"),
        new BigDecimal("2855.00"),
        new BigDecimal("1001652.14"),
        HOLDINGS);
  }
}
