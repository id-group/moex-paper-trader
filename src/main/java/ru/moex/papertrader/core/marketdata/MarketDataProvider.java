package ru.moex.papertrader.core.marketdata;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import ru.moex.papertrader.core.domain.Instrument;

/**
 * Порт рыночных данных. Реализации: заглушка этапа 2 (infra), клиенты AlgoPack/MOEX ISS (этап 4,
 * infra/algoapi), запись RECORD и воспроизведение REPLAY (этап 4, core/marketdata + infra).
 */
public interface MarketDataProvider extends TradingCalendar {

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
  @Override
  TradingSession tradingSession(Instant at);

  /**
   * Сделки инструмента в диапазоне [from, to] (включительно), по возрастанию времени. Реализация по
   * умолчанию — пустой список (для провайдеров без истории сделок).
   */
  default List<Trade> trades(Instrument instrument, Instant from, Instant to) {
    return List.of();
  }

  /**
   * Средний дневной объём торгов (ADV) в штуках за последние 30 торговых дней — нужен этапу 5 для
   * расчёта ликвидности; 0 — данных нет.
   */
  default BigDecimal adv(Instrument instrument) {
    return BigDecimal.ZERO;
  }

  /** Торговые дни диапазона; по умолчанию — пустой календарь. */
  @Override
  default List<TradingDay> tradingDays(Instant from, Instant to) {
    return List.of();
  }
}
