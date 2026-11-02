package ru.moex.papertrader.core.marketdata;

/** Тип снапшота рыночных данных: единица записи RECORD и таймлайна REPLAY. */
public enum SnapshotKind {
  QUOTE,
  ORDER_BOOK,
  TRADE
}
