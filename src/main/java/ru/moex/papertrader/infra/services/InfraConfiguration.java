package ru.moex.papertrader.infra.services;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** Регистрация настроек инфраструктурных сервисов. */
@Configuration
@EnableConfigurationProperties({PortfolioProperties.class, StubMarketDataProperties.class})
public class InfraConfiguration {}
