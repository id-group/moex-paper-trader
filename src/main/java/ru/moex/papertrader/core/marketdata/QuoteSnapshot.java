package ru.moex.papertrader.core.marketdata;

import java.time.Instant;
import java.util.Objects;

/** Снапшот котировки (BBO/last). */
public record QuoteSnapshot(Instant timestamp, Quote quote) implements MarketDataSnapshot {

  public QuoteSnapshot {
    Objects.requireNonNull(quote, "quote");
  }

  @Override
  public String ticker() {
    return quote.ticker();
  }

  @Override
  public SnapshotKind kind() {
    return SnapshotKind.QUOTE;
  }
}
