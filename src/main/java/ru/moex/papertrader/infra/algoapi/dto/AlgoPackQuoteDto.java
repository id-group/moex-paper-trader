package ru.moex.papertrader.infra.algoapi.dto;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Котировка AlgoPack из ответа {@code .../securities/{ticker}.json}: блок {@code marketdata} плюс
 * {@code PREVPRICE} из блока {@code securities}.
 *
 * <p>Схема ответа — табличная ISS-подобная ({@code metadata/columns/data}), а не плоский JSON:
 * {@code columns/data} разбирает клиент. Поля соответствуют колонкам API: {@code SECID}, {@code
 * BOARDID}, {@code BID}, {@code OFFER}, {@code OPEN}, {@code HIGH}, {@code LOW}, {@code LAST},
 * {@code LASTCHANGE}, {@code LASTCHANGEPRCNT}, {@code TRADINGSTATUS}, {@code UPDATETIME}.
 *
 * <p>Документация: <a
 * href="https://moexalgo.github.io/docs/api/real-time-market-data-акции">Real-time market data —
 * Акции</a>.
 *
 * @param secId код инструмента
 * @param boardId режим торгов
 * @param bid лучшая цена покупки
 * @param offer лучшая цена продажи
 * @param open цена открытия
 * @param high максимум дня
 * @param low минимум дня
 * @param last цена последней сделки
 * @param prevPrice цена закрытия предыдущего дня
 * @param change изменение к предыдущему закрытию ({@code LASTCHANGE})
 * @param changeRate изменение в процентах ({@code LASTCHANGEPRCNT})
 * @param tradingStatus строковый статус торгов API (в core транслируется в числовой код)
 * @param updatedAt время обновления ({@code SYSTIME} или {@code UPDATETIME})
 */
public record AlgoPackQuoteDto(
    String secId,
    String boardId,
    BigDecimal bid,
    BigDecimal offer,
    BigDecimal open,
    BigDecimal high,
    BigDecimal low,
    BigDecimal last,
    BigDecimal prevPrice,
    BigDecimal change,
    BigDecimal changeRate,
    String tradingStatus,
    Instant updatedAt) {}
