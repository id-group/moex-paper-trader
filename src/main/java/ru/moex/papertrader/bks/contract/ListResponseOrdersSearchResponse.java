package ru.moex.papertrader.bks.contract;

import java.util.List;

/** Ответ со списком заявок (схема {@code ListResponseOrdersSearchResponse}). */
public record ListResponseOrdersSearchResponse(
    List<OrdersSearchResponse> records, long totalRecords, int totalPages) {}
