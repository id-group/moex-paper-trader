package ru.moex.papertrader.infra.algoapi.dto;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Свеча AlgoPack из ответа {@code .../securities/{ticker}/candles.json}.
 *
 * <p>Поля соответствуют колонкам API: {@code open}, {@code close}, {@code high}, {@code low},
 * {@code value} (объём в валюте), {@code volume} (объём в лотах), {@code begin}, {@code end}. Время
 * в ответе — московское (UTC+3), в core транслируется в UTC.
 *
 * <p>Допустимые интервалы API: 1, 10, 60, 24, 7, 31, 4 (4 — квартал).
 *
 * <p>Документация: <a
 * href="https://moexalgo.github.io/docs/api/real-time-market-data-акции">Real-time market data —
 * Акции</a>.
 *
 * @param begin начало интервала свечи
 * @param end конец интервала свечи
 * @param open цена открытия
 * @param close цена закрытия
 * @param high максимум
 * @param low минимум
 * @param value объём в валюте
 * @param volume объём в лотах
 */
public record AlgoPackCandleDto(
    Instant begin,
    Instant end,
    BigDecimal open,
    BigDecimal close,
    BigDecimal high,
    BigDecimal low,
    BigDecimal value,
    BigDecimal volume) {}
