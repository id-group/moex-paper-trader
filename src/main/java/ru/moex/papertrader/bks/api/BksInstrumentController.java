package ru.moex.papertrader.bks.api;

import java.time.Clock;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.moex.papertrader.bks.adapter.BksMarketDataMapper;
import ru.moex.papertrader.bks.contract.Instrument;
import ru.moex.papertrader.bks.contract.TickersRequest;
import ru.moex.papertrader.bks.contract.TradingStatusResponse;
import ru.moex.papertrader.core.marketdata.MarketDataProvider;
import ru.moex.papertrader.infra.services.exception.InvalidRequestException;

/** Справочник инструментов и статус торгов БКС. */
@RestController
@RequestMapping("${emulator.brokers.bks.base-path:/api/bks}/v1")
public class BksInstrumentController {

  private final MarketDataProvider marketDataProvider;
  private final BksMarketDataMapper marketDataMapper;
  private final Clock clock;

  public BksInstrumentController(
      MarketDataProvider marketDataProvider, BksMarketDataMapper marketDataMapper, Clock clock) {
    this.marketDataProvider = marketDataProvider;
    this.marketDataMapper = marketDataMapper;
    this.clock = clock;
  }

  @PostMapping("/instruments/by-tickers")
  public List<Instrument> byTickers(@RequestBody TickersRequest request) {
    List<String> tickers = request.tickers();
    if (tickers == null || tickers.isEmpty()) {
      throw new InvalidRequestException("tickers обязателен и не может быть пустым");
    }
    for (String ticker : tickers) {
      if (ticker == null || ticker.isBlank()) {
        throw new InvalidRequestException("ticker не может быть пустым");
      }
    }
    return marketDataProvider.instruments(tickers).stream()
        .map(marketDataMapper::toInstrument)
        .toList();
  }

  @GetMapping("/trading-schedule/status")
  public TradingStatusResponse tradingStatus() {
    return marketDataMapper.toTradingStatus(marketDataProvider.tradingSession(clock.instant()));
  }
}
