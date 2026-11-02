package ru.moex.papertrader.core.marketdata;

import java.time.Instant;
import java.util.Objects;

/** Снапшот стакана. */
public record OrderBookEvent(Instant timestamp, OrderBookSnapshot orderBook)
    implements MarketDataSnapshot {

  public OrderBookEvent {
    Objects.requireNonNull(orderBook, "orderBook");
  }

  @Override
  public String ticker() {
    return orderBook.ticker();
  }

  @Override
  public SnapshotKind kind() {
    return SnapshotKind.ORDER_BOOK;
  }
}
