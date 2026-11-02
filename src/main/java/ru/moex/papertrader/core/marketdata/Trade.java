package ru.moex.papertrader.core.marketdata;

import java.math.BigDecimal;
import java.time.Instant;

/** Сделка (тик) инструмента: время, цена, количество в штуках и направление. */
public record Trade(
    Instant ts,
    String ticker,
    String classCode,
    BigDecimal price,
    long quantity,
    TradeDirection direction) {}
