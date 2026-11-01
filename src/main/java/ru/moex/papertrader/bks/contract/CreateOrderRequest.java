package ru.moex.papertrader.bks.contract;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.math.BigDecimal;

/** Тело запроса {@code POST /api/v1/orders}. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CreateOrderRequest(
    String clientOrderId,
    String side,
    String orderType,
    String timeInForce,
    String expiryDate,
    Long orderQuantity,
    String ticker,
    String classCode,
    BigDecimal price,
    BigDecimal stopPrice,
    BigDecimal takePrice,
    Integer visible,
    Integer executionTakeProfitType,
    Integer executionStopLossType,
    BigDecimal spread,
    Integer spreadType,
    BigDecimal offset,
    Integer offsetType) {}
