package ru.moex.papertrader.bks.adapter;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;
import ru.moex.papertrader.bks.contract.BarResponse;
import ru.moex.papertrader.bks.contract.BoardAndExchange;
import ru.moex.papertrader.bks.contract.CandlesChartResponse;
import ru.moex.papertrader.bks.contract.Instrument;
import ru.moex.papertrader.bks.contract.MarketDataResponse;
import ru.moex.papertrader.bks.contract.OrderBookRestResponse;
import ru.moex.papertrader.bks.contract.QuotesListResponse;
import ru.moex.papertrader.bks.contract.TradingStatusResponse;
import ru.moex.papertrader.bks.contract.Volume;
import ru.moex.papertrader.core.marketdata.Candle;
import ru.moex.papertrader.core.marketdata.CandleSeries;
import ru.moex.papertrader.core.marketdata.OrderBookSnapshot;
import ru.moex.papertrader.core.marketdata.Quote;
import ru.moex.papertrader.core.marketdata.TradingSession;
import tools.jackson.databind.json.JsonMapper;

/** Маппинг рыночных данных и справочника в контракт БКС. */
@Component
public class BksMarketDataMapper {

  private static final String EXCHANGE = "MOEX";
  private static final ZoneOffset UTC = ZoneOffset.UTC;

  private final JsonMapper jsonMapper;

  public BksMarketDataMapper(JsonMapper jsonMapper) {
    this.jsonMapper = jsonMapper;
  }

  public QuotesListResponse toQuotes(List<Quote> quotes) {
    return new QuotesListResponse(quotes.stream().map(this::toMarketData).toList());
  }

  public OrderBookRestResponse toOrderBook(OrderBookSnapshot book) {
    return new OrderBookRestResponse(
        book.depth(),
        OffsetDateTime.ofInstant(book.dateTime(), UTC),
        book.ticker(),
        book.classCode(),
        Math.toIntExact(book.bidVolume()),
        Math.toIntExact(book.askVolume()),
        book.bids().stream().map(BksMarketDataMapper::toVolume).toList(),
        book.asks().stream().map(BksMarketDataMapper::toVolume).toList());
  }

  public CandlesChartResponse toCandles(CandleSeries series) {
    return new CandlesChartResponse(
        series.ticker(),
        series.classCode(),
        OffsetDateTime.ofInstant(series.start(), UTC),
        OffsetDateTime.ofInstant(series.end(), UTC),
        series.timeFrame().name(),
        series.bars().stream().map(BksMarketDataMapper::toBar).toList());
  }

  public TradingStatusResponse toTradingStatus(TradingSession session) {
    return new TradingStatusResponse(
        session.id(),
        session.name(),
        session.open() ? "OPEN" : "CLOSE",
        OffsetDateTime.ofInstant(session.nextSessionAt(), UTC));
  }

  /**
   * Справочник БКС содержит 75 полей; заполненные значения собираются в карту и приводятся к
   * record-контракту. Это осознанное исключение из ручного маппинга — поля-заглушки остаются null.
   */
  public Instrument toInstrument(ru.moex.papertrader.core.domain.Instrument instrument) {
    Map<String, Object> values = new LinkedHashMap<>();
    values.put("ticker", instrument.ticker());
    values.put("boards", List.of(new BoardAndExchange(instrument.classCode(), EXCHANGE)));
    values.put("shortName", instrument.shortName());
    values.put("displayName", instrument.displayName());
    values.put("type", instrument.type());
    values.put("isin", instrument.isin());
    values.put("tradingCurrency", instrument.currency());
    values.put("scale", Math.max(0, instrument.minimumStep().scale()));
    values.put("minimumStep", instrument.minimumStep());
    values.put("instrumentType", instrument.type());
    values.put("settlementCurrency", instrument.currency());
    values.put("lotSize", instrument.lotSize());
    values.put("isQualifiedOnly", false);
    values.put("isCanShort", true);
    values.put("availableForUnqualified", true);
    values.put("currencyNominal", instrument.currency());
    values.put("isBcsProduct", false);
    values.put("isBlocked", false);
    values.put("primaryBoard", instrument.classCode());
    values.put("secondaryBoards", List.of());
    values.put("isCanMargin", true);
    values.put("isReplacementBond", false);
    values.put("amortisedMty", false);
    return jsonMapper.convertValue(values, Instrument.class);
  }

  private MarketDataResponse toMarketData(Quote quote) {
    return new MarketDataResponse(
        quote.ticker(),
        quote.classCode(),
        OffsetDateTime.ofInstant(quote.dateTime(), UTC),
        quote.tradingStatus(),
        quote.currency(),
        quote.bid(),
        quote.offer(),
        quote.open(),
        quote.close(),
        quote.high(),
        quote.low(),
        null,
        quote.last(),
        null,
        null,
        quote.change(),
        quote.changeRate());
  }

  private static Volume toVolume(OrderBookSnapshot.Level level) {
    return new Volume(level.price(), level.quantity());
  }

  private static BarResponse toBar(Candle candle) {
    return new BarResponse(
        OffsetDateTime.ofInstant(candle.time(), UTC),
        candle.open(),
        candle.close(),
        candle.high(),
        candle.low(),
        candle.volume());
  }
}
