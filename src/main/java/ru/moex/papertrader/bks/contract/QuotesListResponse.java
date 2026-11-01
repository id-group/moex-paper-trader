package ru.moex.papertrader.bks.contract;

import java.util.List;

/** Ответ со списком котировок (схема {@code QuotesListResponse}). */
public record QuotesListResponse(List<MarketDataResponse> records) {}
