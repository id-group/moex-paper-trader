package ru.moex.papertrader.core.marketdata;

import java.math.BigDecimal;
import java.time.Instant;

/** Свеча таймфрейма. */
public record Candle(
    Instant time,
    BigDecimal open,
    BigDecimal close,
    BigDecimal high,
    BigDecimal low,
    BigDecimal volume) {}
