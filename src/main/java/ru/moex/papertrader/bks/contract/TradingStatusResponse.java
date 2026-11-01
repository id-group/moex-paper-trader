package ru.moex.papertrader.bks.contract;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.OffsetDateTime;

/** Состояние торговой сессии (схема {@code TradingStatusResponse}). */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record TradingStatusResponse(
    Integer tradingSessionTypeId,
    String tradingSessionType,
    String tradingSessionStatus,
    OffsetDateTime nextSessionDate) {}
