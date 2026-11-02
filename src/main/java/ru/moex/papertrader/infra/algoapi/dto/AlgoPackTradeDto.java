package ru.moex.papertrader.infra.algoapi.dto;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Сделка AlgoPack из ответа {@code .../securities/{ticker}/trades.json}.
 *
 * <p>Поля соответствуют колонкам API: {@code TRADENO}, {@code TRADETIME}, {@code BOARDID}, {@code
 * SECID}, {@code PRICE}, {@code QUANTITY}, {@code VALUE}, {@code BUYSELL}, {@code TRADINGSESSION}.
 * Время — {@code SYSTIME} (дата и время) с запасным вариантом {@code TRADEDATE} + {@code
 * TRADETIME}; в core транслируется в UTC.
 *
 * <p>Документация: <a
 * href="https://moexalgo.github.io/docs/api/real-time-market-data-акции">Real-time market data —
 * Акции</a>.
 *
 * @param tradeNo номер сделки
 * @param tradeTime время сделки
 * @param boardId режим торгов
 * @param secId код инструмента
 * @param price цена
 * @param quantity количество в штуках
 * @param value объём сделки в валюте
 * @param buySell сторона агрессора: {@code B} — покупка, {@code S} — продажа (может быть пустой)
 * @param tradingSession код торговой сессии
 */
public record AlgoPackTradeDto(
    long tradeNo,
    Instant tradeTime,
    String boardId,
    String secId,
    BigDecimal price,
    long quantity,
    BigDecimal value,
    String buySell,
    String tradingSession) {}
