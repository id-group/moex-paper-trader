package ru.moex.papertrader.infra.repository;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import ru.moex.papertrader.PostgresContainerSupport;
import ru.moex.papertrader.infra.model.AccountBalanceEntity;
import ru.moex.papertrader.infra.model.AccountEntity;

/** Сохранение и чтение счёта вместе с денежными остатками. */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class AccountRepositoryIT extends PostgresContainerSupport {

  /** Уникальный счёт: БД общая для всех тестов JVM, PAPER-0001 занимают BKS-тесты. */
  private static final String ACCOUNT_ID = "IT-" + UUID.randomUUID();

  private static final Instant NOW = Instant.parse("2026-11-02T10:00:00Z");

  @Autowired private AccountRepository accounts;
  @Autowired private AccountBalanceRepository balances;
  @Autowired private EntityManager entityManager;

  @Test
  void savesAndReadsAccount() {
    accounts.saveAndFlush(new AccountEntity(ACCOUNT_ID, NOW));
    entityManager.clear();

    AccountEntity loaded = accounts.findById(ACCOUNT_ID).orElseThrow();

    assertThat(loaded.getAccountId()).isEqualTo(ACCOUNT_ID);
    assertThat(loaded.getCreatedAt()).isEqualTo(NOW);
  }

  @Test
  void savesAndReadsBalancePreservingMoneyScale() {
    accounts.saveAndFlush(new AccountEntity(ACCOUNT_ID, NOW));
    balances.saveAndFlush(
        new AccountBalanceEntity(
            ACCOUNT_ID, "RUB", new BigDecimal("1500000.00"), new BigDecimal("250000.00"), NOW));
    entityManager.clear();

    AccountBalanceEntity loaded =
        balances.findByAccountIdAndCurrency(ACCOUNT_ID, "RUB").orElseThrow();

    assertThat(loaded.getCash()).isEqualTo(new BigDecimal("1500000.00"));
    assertThat(loaded.getLocked()).isEqualTo(new BigDecimal("250000.00"));
    assertThat(loaded.getCash().scale()).isEqualTo(2);
    assertThat(loaded.getUpdatedAt()).isEqualTo(NOW);
    assertThat(balances.findByAccountId(ACCOUNT_ID)).hasSize(1);
  }
}
