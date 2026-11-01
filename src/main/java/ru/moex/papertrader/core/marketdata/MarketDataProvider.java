package ru.moex.papertrader.core.marketdata;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import ru.moex.papertrader.core.domain.Instrument;

/** Порт рыночных данных (реализация этапа 2 — заглушка на конфиге; LIVE/RECORD/REPLAY — этап 4). */
public interface MarketDataProvider {

  /** Находит инструменты по тикерам; неизвестные тикеры пропускаются. */
  List<Instrument> instruments(Collection<String> tickers);

  /** Ищет инструмент по паре тикер + режим торгов. */
  Optional<Instrument> instrument(String ticker, String classCode);

  /** Котировка инструмента, если инструмент известен провайдеру. */
  Optional<Quote> quote(Instrument instrument);

  /** Стакан заданной глубины. */
  OrderBookSnapshot orderBook(Instrument instrument, int depth);

  /** Свечи за период. */
  CandleSeries candles(Instrument instrument, Instant from, Instant to, TimeFrame timeFrame);

  /** Состояние торговой сессии на момент времени. */
  TradingSession tradingSession(Instant at);
}
