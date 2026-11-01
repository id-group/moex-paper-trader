package ru.moex.papertrader.core.marketdata;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** Снимок стакана заявок. */
public record OrderBookSnapshot(
    String ticker,
    String classCode,
    int depth,
    Instant dateTime,
    List<Level> bids,
    List<Level> asks) {

  public OrderBookSnapshot {
    bids = List.copyOf(bids);
    asks = List.copyOf(asks);
  }

  public long bidVolume() {
    return bids.stream().mapToLong(Level::quantity).sum();
  }

  public long askVolume() {
    return asks.stream().mapToLong(Level::quantity).sum();
  }

  /** Уровень стакана: цена и количество в штуках. */
  public record Level(BigDecimal price, long quantity) {}
}
