package ru.moex.papertrader.bks.contract;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** Тело запроса {@code POST /api/v1/orders/cancel} и sync-варианта. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CancelOrderRequest(String orderIdType, String orderId, String clientOrderId) {}
