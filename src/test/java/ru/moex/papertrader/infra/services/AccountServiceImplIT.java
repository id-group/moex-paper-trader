package ru.moex.papertrader.infra.services;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;
import ru.moex.papertrader.PostgresContainerSupport;
import ru.moex.papertrader.infra.model.AccountBalanceEntity;
import ru.moex.papertrader.infra.repository.AccountBalanceRepository;
import ru.moex.papertrader.infra.repository.AccountRepository;

/**
 * Счёт PAPER-подобного портфеля: создание строк accounts/account_balances и идемпотентность.
 *
 * <p>Тест транзакционный и откатывается; счёт задаётся отдельным от других тестов значением, чтобы
 * не зависеть от данных общей БД прогона.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Transactional
@Import(FixedClockTestConfiguration.class)
@TestPropertySource(properties = "emulator.portfolio.account=IT-SEED-ACC-1")
class AccountServiceImplIT extends PostgresContainerSupport {

  @Autowired private AccountService accountService;
  @Autowired private AccountRepository accounts;
  @Autowired private AccountBalanceRepository balances;
  @Autowired private PortfolioProperties properties;
  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private EntityManager entityManager;

  @Test
  void ensureAccountCreatesAccountAndBalance() {
    deleteConfiguredAccount();
    flushAndClear();

    accountService.ensureAccount();
    flushAndClear();

    assertThat(accounts.findById(properties.account())).isPresent();
    AccountBalanceEntity balance = configuredBalance();
    assertThat(balance.getCurrency()).isEqualTo(properties.currency());
    assertThat(balance.getCash()).isEqualByComparingTo(properties.initialCash());
    assertThat(balance.getLocked()).isEqualByComparingTo("0");
    assertThat(balance.getUpdatedAt()).isEqualTo(FixedClockTestConfiguration.NOW);
  }

  @Test
  void repeatedEnsureAccountKeepsExistingBalance() {
    accountService.ensureAccount();
    jdbcTemplate.update(
        "UPDATE account_balances SET cash = ?, locked = ? WHERE account_id = ? AND currency = ?",
        new BigDecimal("42.00"),
        new BigDecimal("7.00"),
        properties.account(),
        properties.currency());

    accountService.ensureAccount();
    flushAndClear();

    AccountBalanceEntity balance = configuredBalance();
    assertThat(balance.getCash()).isEqualByComparingTo("42.00");
    assertThat(balance.getLocked()).isEqualByComparingTo("7.00");
  }

  @Test
  void lockAccountCreatesUnknownAccountAndItsBalance() {
    String accountId = "IT-" + UUID.randomUUID();

    accountService.lockAccount(accountId);
    flushAndClear();

    assertThat(accounts.findById(accountId)).isPresent();
    AccountBalanceEntity balance =
        balances.findByAccountIdAndCurrency(accountId, properties.currency()).orElseThrow();
    assertThat(balance.getCash()).isEqualByComparingTo(properties.initialCash());
    assertThat(balance.getLocked()).isEqualByComparingTo("0");
  }

  private AccountBalanceEntity configuredBalance() {
    return balances
        .findByAccountIdAndCurrency(properties.account(), properties.currency())
        .orElseThrow();
  }

  private void deleteConfiguredAccount() {
    jdbcTemplate.update("DELETE FROM account_balances WHERE account_id = ?", properties.account());
    jdbcTemplate.update("DELETE FROM accounts WHERE account_id = ?", properties.account());
  }

  private void flushAndClear() {
    entityManager.flush();
    entityManager.clear();
  }
}
