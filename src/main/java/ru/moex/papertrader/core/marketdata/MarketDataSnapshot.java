package ru.moex.papertrader.core.marketdata;

import java.time.Instant;

/**
 * Снапшот входящего потока рыночных данных. Реализации — {@link QuoteSnapshot}, {@link
 * OrderBookEvent}, {@link TradeEvent}; в RECORD такие снапшоты пишутся в хранилище, в REPLAY —
 * воспроизводятся по виртуальному таймлайну.
 */
public sealed interface MarketDataSnapshot permits QuoteSnapshot, OrderBookEvent, TradeEvent {

  /** Момент события (для REPLAY — позиция на виртуальном таймлайне). */
  Instant timestamp();

  /** Тикер инструмента. */
  String ticker();

  /** Тип снапшота. */
  SnapshotKind kind();
}
