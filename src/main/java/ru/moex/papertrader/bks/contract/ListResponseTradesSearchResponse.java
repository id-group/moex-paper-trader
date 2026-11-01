package ru.moex.papertrader.bks.contract;

import java.util.List;

/** Ответ со списком сделок (схема {@code ListResponseTradesSearchResponse}). */
public record ListResponseTradesSearchResponse(
    List<TradesSearchResponse> records, long totalRecords, int totalPages) {}
