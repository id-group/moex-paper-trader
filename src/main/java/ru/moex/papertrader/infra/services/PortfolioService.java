package ru.moex.papertrader.infra.services;

import ru.moex.papertrader.core.domain.PortfolioSnapshot;

/** Срез виртуального портфеля. */
public interface PortfolioService {

  PortfolioSnapshot snapshot();
}
