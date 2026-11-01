package ru.moex.papertrader.infra.services;

import java.math.BigDecimal;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Настройки виртуального портфеля (брокер-независимые). */
@ConfigurationProperties(prefix = "emulator.portfolio")
public record PortfolioProperties(String account, BigDecimal initialCash, String currency) {}
