package ru.moex.papertrader.bks.contract;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

/** Котировка инструмента (схема {@code MarketDataResponse}). */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record MarketDataResponse(
    String ticker,
    String classCode,
    OffsetDateTime dateTime,
    Integer securityTradingStatus,
    String currency,
    BigDecimal bid,
    BigDecimal offer,
    BigDecimal open,
    BigDecimal close,
    BigDecimal high,
    BigDecimal low,
    BigDecimal theoreticalPrice,
    BigDecimal last,
    BigDecimal bidYield,
    BigDecimal offerYield,
    BigDecimal change,
    BigDecimal changeRate) {}
