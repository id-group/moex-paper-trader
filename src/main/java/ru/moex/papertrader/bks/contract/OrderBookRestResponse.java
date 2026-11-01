package ru.moex.papertrader.bks.contract;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.OffsetDateTime;
import java.util.List;

/** Ответ стакана (схема {@code OrderBookRestResponse}). */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record OrderBookRestResponse(
    Integer depth,
    OffsetDateTime dateTime,
    String ticker,
    String classCode,
    Integer bidVolume,
    Integer askVolume,
    List<Volume> bids,
    List<Volume> asks) {}
