package ru.moex.papertrader.infra.algoapi;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.moex.papertrader.core.domain.Instrument;
import ru.moex.papertrader.core.marketdata.CandleSeries;
import ru.moex.papertrader.core.marketdata.MarketDataProvider;
import ru.moex.papertrader.core.marketdata.OrderBookSnapshot;
import ru.moex.papertrader.core.marketdata.Quote;
import ru.moex.papertrader.core.marketdata.TimeFrame;
import ru.moex.papertrader.core.marketdata.Trade;
import ru.moex.papertrader.core.marketdata.TradingDay;
import ru.moex.papertrader.core.marketdata.TradingSession;

/**
 * Провайдер с резервированием: основной источник (AlgoPack) и запасной (MOEX ISS).
 *
 * <p>Котировки и справочник инструментов переключаются на запасной источник и при пустом
 * результате, и при ошибке; стакан, свечи, сделки и ADV — только при ошибке. Торговый календарь
 * берётся у основного источника.
 */
public class FallbackMarketDataProvider implements MarketDataProvider {

  private static final Logger log = LoggerFactory.getLogger(FallbackMarketDataProvider.class);

  private final MarketDataProvider primary;
  private final MarketDataProvider fallback;

  public FallbackMarketDataProvider(MarketDataProvider primary, MarketDataProvider fallback) {
    this.primary = primary;
    this.fallback = fallback;
  }

  @Override
  public List<Instrument> instruments(Collection<String> tickers) {
    try {
      List<Instrument> instruments = primary.instruments(tickers);
      if (!instruments.isEmpty()) {
        return instruments;
      }
    } catch (RuntimeException e) {
      log.warn("Основной источник справочника недоступен: {}", e.getMessage());
    }
    return fallback.instruments(tickers);
  }

  @Override
  public Optional<Instrument> instrument(String ticker, String classCode) {
    try {
      Optional<Instrument> instrument = primary.instrument(ticker, classCode);
      if (instrument.isPresent()) {
        return instrument;
      }
    } catch (RuntimeException e) {
      log.warn("Основной источник справочника недоступен: {}", e.getMessage());
    }
    return fallback.instrument(ticker, classCode);
  }

  @Override
  public Optional<Quote> quote(Instrument instrument) {
    try {
      Optional<Quote> quote = primary.quote(instrument);
      if (quote.isPresent()) {
        return quote;
      }
    } catch (RuntimeException e) {
      log.warn("Основной источник котировок недоступен: {}", e.getMessage());
    }
    return fallback.quote(instrument);
  }

  @Override
  public OrderBookSnapshot orderBook(Instrument instrument, int depth) {
    try {
      return primary.orderBook(instrument, depth);
    } catch (RuntimeException e) {
      log.warn("Основной источник стакана недоступен: {}", e.getMessage());
      return fallback.orderBook(instrument, depth);
    }
  }

  @Override
  public CandleSeries candles(
      Instrument instrument, Instant from, Instant to, TimeFrame timeFrame) {
    try {
      return primary.candles(instrument, from, to, timeFrame);
    } catch (RuntimeException e) {
      log.warn("Основной источник свечей недоступен: {}", e.getMessage());
      return fallback.candles(instrument, from, to, timeFrame);
    }
  }

  @Override
  public List<Trade> trades(Instrument instrument, Instant from, Instant to) {
    try {
      return primary.trades(instrument, from, to);
    } catch (RuntimeException e) {
      log.warn("Основной источник сделок недоступен: {}", e.getMessage());
      return fallback.trades(instrument, from, to);
    }
  }

  @Override
  public BigDecimal adv(Instrument instrument) {
    try {
      return primary.adv(instrument);
    } catch (RuntimeException e) {
      log.warn("Основной источник ADV недоступен: {}", e.getMessage());
      return fallback.adv(instrument);
    }
  }

  @Override
  public TradingSession tradingSession(Instant at) {
    return primary.tradingSession(at);
  }

  @Override
  public List<TradingDay> tradingDays(Instant from, Instant to) {
    return primary.tradingDays(from, to);
  }
}
