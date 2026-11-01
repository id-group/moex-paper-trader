package ru.moex.papertrader.bks.contract;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

/** Ответ лимитов (схема {@code LimitResponse}). */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record LimitResponse(
    List<DepoLimitResponse> depoLimit,
    List<FutureHoldingResponse> futureHolding,
    List<MoneyLimitResponse> moneyLimits,
    List<FuturesLimitResponse> futuresLimits) {}
