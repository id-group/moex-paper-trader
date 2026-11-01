package ru.moex.papertrader.bks.api;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ru.moex.papertrader.bks.adapter.BksMarketDataMapper;
import ru.moex.papertrader.bks.contract.CandlesChartResponse;
import ru.moex.papertrader.bks.contract.InstrumentRef;
import ru.moex.papertrader.bks.contract.OrderBookRestResponse;
import ru.moex.papertrader.bks.contract.QuotesListResponse;
import ru.moex.papertrader.bks.contract.QuotesRequest;
import ru.moex.papertrader.core.domain.Instrument;
import ru.moex.papertrader.core.marketdata.CandleSeries;
import ru.moex.papertrader.core.marketdata.MarketDataProvider;
import ru.moex.papertrader.core.marketdata.Quote;
import ru.moex.papertrader.core.marketdata.TimeFrame;
import ru.moex.papertrader.infra.services.exception.InvalidRequestException;
import ru.moex.papertrader.infra.services.exception.ResourceNotFoundException;

/** Рыночные данные БКС: котировки, стакан и свечи (заглушка этапа 2). */
@RestController
@RequestMapping("${emulator.brokers.bks.base-path:/api/bks}/v1")
public class BksMarketDataController {

  private static final int MAX_INSTRUMENTS_PER_QUOTES = 100;
  private static final int MAX_ORDER_BOOK_DEPTH = 20;
  private static final long MAX_CANDLES = 1_000;

  private final MarketDataProvider marketDataProvider;
  private final BksMarketDataMapper marketDataMapper;

  public BksMarketDataController(
      MarketDataProvider marketDataProvider, BksMarketDataMapper marketDataMapper) {
    this.marketDataProvider = marketDataProvider;
    this.marketDataMapper = marketDataMapper;
  }

  @PostMapping("/quotes")
  public QuotesListResponse quotes(@RequestBody QuotesRequest request) {
    List<InstrumentRef> instruments = request.instruments();
    if (instruments == null || instruments.isEmpty()) {
      throw new InvalidRequestException("instruments обязателен и не может быть пустым");
    }
    if (instruments.size() > MAX_INSTRUMENTS_PER_QUOTES) {
      throw new InvalidRequestException(
          "instruments не может содержать больше " + MAX_INSTRUMENTS_PER_QUOTES + " элементов");
    }
    List<Quote> quotes = new ArrayList<>();
    for (InstrumentRef reference : instruments) {
      if (reference == null
          || reference.ticker() == null
          || reference.ticker().isBlank()
          || reference.classCode() == null
          || reference.classCode().isBlank()) {
        throw new InvalidRequestException("ticker и classCode обязательны");
      }
      marketDataProvider
          .instrument(reference.ticker(), reference.classCode())
          .flatMap(marketDataProvider::quote)
          .ifPresent(quotes::add);
    }
    return marketDataMapper.toQuotes(quotes);
  }

  @GetMapping("/order-book")
  public OrderBookRestResponse orderBook(
      @RequestParam(name = "ticker") String ticker,
      @RequestParam(name = "classCode") String classCode,
      @RequestParam(name = "depth", defaultValue = "20") int depth) {
    if (depth < 1 || depth > MAX_ORDER_BOOK_DEPTH) {
      throw new InvalidRequestException("depth должен быть в диапазоне 1..20");
    }
    Instrument instrument = requireInstrument(ticker, classCode);
    return marketDataMapper.toOrderBook(marketDataProvider.orderBook(instrument, depth));
  }

  @GetMapping("/candles-chart")
  public CandlesChartResponse candles(
      @RequestParam(name = "classCode") String classCode,
      @RequestParam(name = "ticker") String ticker,
      @RequestParam(name = "startDate") Instant startDate,
      @RequestParam(name = "endDate") Instant endDate,
      @RequestParam(name = "timeFrame") String timeFrame) {
    TimeFrame frame =
        TimeFrame.fromCode(timeFrame)
            .orElseThrow(
                () ->
                    new InvalidRequestException(
                        "timeFrame должен быть одним из M1, M5, M15, M30, H1, H4, D, W, MN"));
    if (endDate.isBefore(startDate)) {
      throw new InvalidRequestException("endDate не может быть раньше startDate");
    }
    long bars = Duration.between(startDate, endDate).dividedBy(frame.step()) + 1;
    if (bars > MAX_CANDLES) {
      throw new InvalidRequestException(
          "Период содержит больше " + MAX_CANDLES + " свечей; уменьшите диапазон");
    }
    Instrument instrument = requireInstrument(ticker, classCode);
    CandleSeries series = marketDataProvider.candles(instrument, startDate, endDate, frame);
    return marketDataMapper.toCandles(series);
  }

  private Instrument requireInstrument(String ticker, String classCode) {
    if (ticker == null || ticker.isBlank() || classCode == null || classCode.isBlank()) {
      throw new InvalidRequestException("ticker и classCode обязательны");
    }
    return marketDataProvider
        .instrument(ticker, classCode)
        .orElseThrow(
            () ->
                new ResourceNotFoundException(
                    "Инструмент " + ticker + "/" + classCode + " не найден"));
  }
}
