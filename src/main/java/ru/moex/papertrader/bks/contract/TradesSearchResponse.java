package ru.moex.papertrader.bks.contract;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

/** Запись списка сделок (схема {@code TradesSearchResponse}). */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record TradesSearchResponse(
    Long orderNum,
    String ticker,
    Long tradeNum,
    String clientCode,
    String classCode,
    String settlementCurrency,
    String baseCurrency,
    String priceCurrency,
    String side,
    String instrumentType,
    Integer dealType,
    OffsetDateTime tradeDateTime,
    BigDecimal price,
    BigDecimal volume,
    BigDecimal go,
    BigDecimal contractAmount,
    LocalDate settleDate,
    BigDecimal tradeQuantity,
    BigDecimal tradeQuantityLots) {}
