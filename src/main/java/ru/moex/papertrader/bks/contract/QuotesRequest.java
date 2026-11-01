package ru.moex.papertrader.bks.contract;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

/** Тело запроса {@code POST /api/v1/quotes} (схема {@code QuotesRequest}). */
@JsonIgnoreProperties(ignoreUnknown = true)
public record QuotesRequest(List<InstrumentRef> instruments) {}
