package ru.moex.papertrader.core.marketdata;

import java.time.Instant;

/** Состояние торговой сессии (на этапе 2 — упрощение конфигом, этап 4 — торговый календарь). */
public record TradingSession(int id, String name, boolean open, Instant nextSessionAt) {}
