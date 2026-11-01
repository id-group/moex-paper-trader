package ru.moex.papertrader.core.domain;

import java.math.BigDecimal;
import java.time.Instant;

/** Исполненная сделка (на этапе 2 сделки не создаются — их породит матчинг этапа 5). */
public record Trade(
    long tradeNum,
    long orderNum,
    String clientCode,
    Instrument instrument,
    Side side,
    BigDecimal price,
    long quantity,
    BigDecimal commission,
    Instant executedAt) {}
