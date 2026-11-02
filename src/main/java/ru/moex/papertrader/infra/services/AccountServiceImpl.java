package ru.moex.papertrader.infra.services;

import java.math.BigDecimal;
import java.time.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.moex.papertrader.infra.repository.AccountBalanceRepository;
import ru.moex.papertrader.infra.repository.AccountRepository;

/**
 * Счёт портфеля из настроек эмулятора.
 *
 * <p>Строки создаются нативным {@code INSERT ... ON CONFLICT DO NOTHING}: параллельные контексты
 * (тесты, повторный старт) не падают на гонке, а уже накопленный баланс не перезаписывается.
 */
@Service
public class AccountServiceImpl implements AccountService {

  private static final Logger log = LoggerFactory.getLogger(AccountServiceImpl.class);

  private final AccountRepository accounts;
  private final AccountBalanceRepository balances;
  private final PortfolioProperties properties;
  private final Clock clock;

  public AccountServiceImpl(
      AccountRepository accounts,
      AccountBalanceRepository balances,
      PortfolioProperties properties,
      Clock clock) {
    this.accounts = accounts;
    this.balances = balances;
    this.properties = properties;
    this.clock = clock;
  }

  @Override
  @Transactional
  public void ensureAccount() {
    ensure(properties.account());
  }

  @Override
  @Transactional
  public void lockAccount(String accountId) {
    if (accountId == null || accountId.isBlank()) {
      throw new IllegalArgumentException("accountId не может быть пустым");
    }
    ensure(accountId);
    accounts
        .lockByAccountId(accountId)
        .orElseThrow(
            () -> new IllegalStateException("Счёт " + accountId + " не найден после создания"));
  }

  private void ensure(String accountId) {
    accounts.insertIfAbsent(accountId, clock.instant());
    balances.insertIfAbsent(
        accountId,
        properties.currency(),
        properties.initialCash(),
        BigDecimal.ZERO,
        clock.instant());
    log.debug("Счёт {} и баланс {} готовы", accountId, properties.currency());
  }
}
