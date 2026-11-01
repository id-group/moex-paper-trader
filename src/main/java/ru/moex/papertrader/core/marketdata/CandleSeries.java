package ru.moex.papertrader.core.marketdata;

import java.time.Instant;
import java.util.List;

/** Ряд свечей инструмента за запрошенный период. */
public record CandleSeries(
    String ticker,
    String classCode,
    Instant start,
    Instant end,
    TimeFrame timeFrame,
    List<Candle> bars) {

  public CandleSeries {
    bars = List.copyOf(bars);
  }
}
