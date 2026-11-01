package ru.moex.papertrader.bks.contract;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

/** Событие заявки (схема {@code OrderData}). */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record OrderData(
    String messageType,
    String orderStatus,
    String executionType,
    BigDecimal orderQuantity,
    BigDecimal executedQuantity,
    BigDecimal lastQuantity,
    BigDecimal remainedQuantity,
    String ticker,
    String classCode,
    String side,
    String orderType,
    BigDecimal averagePrice,
    String orderId,
    String executionId,
    BigDecimal price,
    String currency,
    String clientCode,
    OffsetDateTime transactionTime,
    LocalDate tradeDate,
    String orderNumber,
    BigDecimal accruedCoupon,
    BigDecimal executionValue,
    BigDecimal commission,
    String securityExchange,
    String rejectReason,
    Integer timeInForce,
    String expiryDate,
    BigDecimal stopPrice,
    BigDecimal takePrice,
    Integer visible,
    BigDecimal spread,
    Integer spreadType,
    BigDecimal offset,
    Integer offsetType) {}
