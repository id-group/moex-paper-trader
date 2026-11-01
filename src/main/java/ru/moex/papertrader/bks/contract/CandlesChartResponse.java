package ru.moex.papertrader.bks.contract;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.OffsetDateTime;
import java.util.List;

/** Ответ графика свечей (схема {@code CandlesChartResponse}). */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record CandlesChartResponse(
    String ticker,
    String classCode,
    OffsetDateTime startDate,
    OffsetDateTime endDate,
    String timeFrame,
    List<BarResponse> bars) {}
