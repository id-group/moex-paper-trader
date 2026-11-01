package ru.moex.papertrader.bks.contract;

/** Ответ асинхронного создания заявки (схема {@code ShortOrderResponse}). */
public record ShortOrderResponse(String clientOrderId, String status) {}
