package ru.moex.papertrader.bks.contract;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

/** Держание фьючерсов (схема {@code FutureHoldingResponse}). */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record FutureHoldingResponse(
    String ticker,
    String classCode,
    String exchange,
    BigDecimal cbplPlanned,
    BigDecimal varMargin,
    BigDecimal positionValue,
    BigDecimal totalNet,
    OffsetDateTime executionDate,
    BigDecimal totalVarMargin,
    BigDecimal realVarMargin,
    BigDecimal averagePrice,
    String instrumentType,
    OffsetDateTime tradeDate) {}
