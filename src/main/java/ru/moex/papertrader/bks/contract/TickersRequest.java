package ru.moex.papertrader.bks.contract;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

/** Тело запроса {@code POST /api/v1/instruments/by-tickers} (схема {@code TickersRequest}). */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TickersRequest(List<String> tickers) {}
