package ru.moex.papertrader.bks.api;

import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.moex.papertrader.bks.adapter.BksPortfolioMapper;
import ru.moex.papertrader.bks.contract.LimitResponse;
import ru.moex.papertrader.bks.contract.Portfolio;
import ru.moex.papertrader.core.domain.PortfolioSnapshot;
import ru.moex.papertrader.infra.services.PortfolioService;

/** Портфель и лимиты БКС. */
@RestController
@RequestMapping("${emulator.brokers.bks.base-path:/api/bks}/v1")
public class BksPortfolioController {

  private final PortfolioService portfolioService;
  private final BksPortfolioMapper portfolioMapper;

  public BksPortfolioController(
      PortfolioService portfolioService, BksPortfolioMapper portfolioMapper) {
    this.portfolioService = portfolioService;
    this.portfolioMapper = portfolioMapper;
  }

  @GetMapping("/portfolio")
  public List<Portfolio> portfolio() {
    PortfolioSnapshot snapshot = portfolioService.snapshot();
    return portfolioMapper.toPortfolio(snapshot);
  }

  @GetMapping("/limits")
  public LimitResponse limits() {
    PortfolioSnapshot snapshot = portfolioService.snapshot();
    return portfolioMapper.toLimits(snapshot);
  }
}
