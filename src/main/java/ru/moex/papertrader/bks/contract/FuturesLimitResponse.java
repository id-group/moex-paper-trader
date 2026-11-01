package ru.moex.papertrader.bks.contract;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

/** Лимит по фьючерсам (схема {@code FuturesLimitResponse}). */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record FuturesLimitResponse(
    String currencyCode,
    String exchange,
    BigDecimal accruedint,
    BigDecimal cbpLimit,
    BigDecimal cbplUsed,
    BigDecimal cbplPlanned,
    BigDecimal cbplUsedForOrders,
    BigDecimal cbplUsedForPositions,
    BigDecimal optionsPremium,
    String instrumentType,
    OffsetDateTime loadDate,
    BigDecimal varMargin,
    BigDecimal realVarMargin) {}
