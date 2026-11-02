package ru.moex.papertrader.core.marketdata;

import java.time.Instant;
import java.util.Objects;

/** Снапшот сделки. */
public record TradeEvent(Instant timestamp, Trade trade) implements MarketDataSnapshot {

  public TradeEvent {
    Objects.requireNonNull(trade, "trade");
  }

  @Override
  public String ticker() {
    return trade.ticker();
  }

  @Override
  public SnapshotKind kind() {
    return SnapshotKind.TRADE;
  }
}
