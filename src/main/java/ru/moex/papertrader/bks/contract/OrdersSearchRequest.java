package ru.moex.papertrader.bks.contract;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.OffsetDateTime;
import java.util.List;

/** Тело запроса {@code POST /api/v1/orders/search} (схема {@code OrdersSearchRequest}). */
@JsonIgnoreProperties(ignoreUnknown = true)
public record OrdersSearchRequest(
    OffsetDateTime startDateTime,
    OffsetDateTime endDateTime,
    Integer side,
    List<Integer> orderStatus,
    List<Integer> orderTypes,
    List<Long> orderNums,
    List<String> tickers,
    List<String> classCodes) {}
