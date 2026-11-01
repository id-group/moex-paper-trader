package ru.moex.papertrader.bks.contract;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.OffsetDateTime;
import java.util.List;

/** Тело запроса {@code POST /api/v1/trades/search} (схема {@code TradesSearchRequest}). */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TradesSearchRequest(
    String side,
    List<Long> tradeNums,
    List<Long> orderNums,
    List<String> tickers,
    List<String> classCodes,
    OffsetDateTime startDateTime,
    OffsetDateTime endDateTime) {}
