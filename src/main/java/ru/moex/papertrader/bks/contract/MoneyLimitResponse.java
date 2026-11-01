package ru.moex.papertrader.bks.contract;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

/** Лимит по деньгам (схема {@code MoneyLimitResponse}). */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record MoneyLimitResponse(
    String exchange,
    String currencyCode,
    BigDecimal locked,
    BigDecimal averagePrice,
    String instrumentType,
    LimitQuantity quantity,
    OffsetDateTime loadDate) {}
