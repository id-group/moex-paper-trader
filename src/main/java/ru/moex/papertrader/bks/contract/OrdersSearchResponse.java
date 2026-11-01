package ru.moex.papertrader.bks.contract;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

/** Запись списка заявок (схема {@code OrdersSearchResponse}). */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record OrdersSearchResponse(
    Long orderNum,
    String orderId,
    String clientCode,
    OffsetDateTime executionDateTime,
    BigDecimal executedValue,
    OffsetDateTime orderDateTime,
    LocalDate tradeDate,
    OffsetDateTime updateDateTime,
    String ticker,
    String classCode,
    BigDecimal takePrice,
    BigDecimal stopPrice,
    BigDecimal price,
    String settlementCurrency,
    BigDecimal orderQuantity,
    BigDecimal remainedQuantity,
    BigDecimal executedQuantity,
    String rejectReason,
    BigDecimal averagePrice,
    BigDecimal calculationVolume,
    BigDecimal contractSum,
    Integer orderStatus,
    Integer orderType,
    Integer side,
    BigDecimal orderQuantityLots,
    BigDecimal remainedQuantityLots,
    BigDecimal executedQuantityLots,
    String linkedOrder,
    String stopOrder,
    BigDecimal visible,
    Integer marketTakeProfit,
    Integer marketStopLoss,
    BigDecimal positionPriceStop,
    BigDecimal positionPriceLimit) {}
