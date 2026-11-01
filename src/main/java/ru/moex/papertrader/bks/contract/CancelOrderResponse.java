package ru.moex.papertrader.bks.contract;

/** Ответ отмены заявки (схема {@code CancelOrderResponse}). */
public record CancelOrderResponse(String clientOrderId, String status) {}
