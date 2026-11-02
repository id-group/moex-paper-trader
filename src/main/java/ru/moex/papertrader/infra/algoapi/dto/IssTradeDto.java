package ru.moex.papertrader.infra.algoapi.dto;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Сделка MOEX ISS из ответа {@code .../securities/{secid}/trades.json}.
 *
 * <p>Поля соответствуют колонкам API: {@code TRADENO}, {@code TRADETIME}, {@code SECID}, {@code
 * PRICE}, {@code QUANTITY}, {@code VALUE}, {@code BOARDID}, {@code SYSTIME}, {@code BUYSELL},
 * {@code TRADEDATE}.
 *
 * <p>Документация: <a href="https://iss.moex.com/iss/reference/323">ISS: сделки инструмента</a>.
 *
 * @param tradeNo номер сделки
 * @param tradeTime время сделки
 * @param secId код инструмента
 * @param price цена
 * @param quantity количество в штуках
 * @param value объём сделки в валюте
 * @param boardId режим торгов
 * @param buySell сторона агрессора: {@code B} — покупка, {@code S} — продажа
 */
public record IssTradeDto(
    long tradeNo,
    Instant tradeTime,
    String secId,
    BigDecimal price,
    long quantity,
    BigDecimal value,
    String boardId,
    String buySell) {}
