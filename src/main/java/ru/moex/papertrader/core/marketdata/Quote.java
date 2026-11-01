package ru.moex.papertrader.core.marketdata;

import java.math.BigDecimal;
import java.time.Instant;

/** Котировка инструмента на момент времени. */
public record Quote(
    String ticker,
    String classCode,
    Instant dateTime,
    int tradingStatus,
    String currency,
    BigDecimal bid,
    BigDecimal offer,
    BigDecimal open,
    BigDecimal close,
    BigDecimal high,
    BigDecimal low,
    BigDecimal last,
    BigDecimal change,
    BigDecimal changeRate) {}
