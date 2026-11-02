package ru.moex.papertrader.core.marketdata;

import java.time.Instant;
import java.time.LocalDate;

/**
 * Торговый день календаря МБ. Для неторговых дней {@code openUtc} и {@code closeUtc} — {@code
 * null}.
 */
public record TradingDay(LocalDate date, boolean trading, Instant openUtc, Instant closeUtc) {}
