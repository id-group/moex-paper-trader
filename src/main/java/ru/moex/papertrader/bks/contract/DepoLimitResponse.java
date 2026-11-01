package ru.moex.papertrader.bks.contract;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

/** Лимит по бумаге (схема {@code DepoLimitResponse}). */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record DepoLimitResponse(
    String ticker,
    String classCode,
    String exchange,
    BigDecimal averagePrice,
    LimitQuantity quantity,
    LimitQuantity quantityBatch,
    String instrumentType,
    OffsetDateTime loadDate,
    BigDecimal lockedBuyValue,
    BigDecimal lockedSellValue,
    BigDecimal lockedBuyQuantity,
    BigDecimal lockedSellQuantity) {}
