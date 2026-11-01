package ru.moex.papertrader.core.domain;

import java.math.BigDecimal;

/** Позиция по инструменту в срезе портфеля. */
public record Position(
    Instrument instrument, long quantity, BigDecimal averagePrice, BigDecimal currentPrice) {}
