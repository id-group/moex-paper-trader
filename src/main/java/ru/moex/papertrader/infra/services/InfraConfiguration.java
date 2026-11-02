package ru.moex.papertrader.infra.services;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import ru.moex.papertrader.infra.algoapi.AlgoPackProperties;
import ru.moex.papertrader.infra.algoapi.IssProperties;

/** Регистрация настроек инфраструктурных сервисов и HTTP-клиентов рыночных данных. */
@Configuration
@EnableConfigurationProperties({
  PortfolioProperties.class,
  StubMarketDataProperties.class,
  MarketDataProperties.class,
  ReplayProperties.class,
  AlgoPackProperties.class,
  IssProperties.class
})
public class InfraConfiguration {}
