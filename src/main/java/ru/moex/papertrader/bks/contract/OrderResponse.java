package ru.moex.papertrader.bks.contract;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

/** Ответ получения статуса заявки (схема {@code OrderResponse}). */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record OrderResponse(
    String clientOrderId, String originalClientOrderId, List<OrderData> data) {}
