package ru.moex.papertrader.infra.services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/** Создаёт счёт виртуального портфеля при старте приложения (идемпотентно). */
@Component
public class AccountSeedRunner implements ApplicationRunner {

  private static final Logger log = LoggerFactory.getLogger(AccountSeedRunner.class);

  private final AccountService accountService;
  private final PortfolioProperties properties;

  public AccountSeedRunner(AccountService accountService, PortfolioProperties properties) {
    this.accountService = accountService;
    this.properties = properties;
  }

  @Override
  public void run(ApplicationArguments args) {
    accountService.ensureAccount();
    log.info("Счёт виртуального портфеля {} готов", properties.account());
  }
}
