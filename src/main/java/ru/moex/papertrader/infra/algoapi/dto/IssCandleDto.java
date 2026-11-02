package ru.moex.papertrader.infra.algoapi.dto;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Свеча MOEX ISS из ответа {@code .../securities/{secid}/candles.json}.
 *
 * <p>Поля соответствуют колонкам API: {@code open}, {@code close}, {@code high}, {@code low},
 * {@code value}, {@code volume}, {@code begin}, {@code end}. Время — московское, в core
 * транслируется в UTC.
 *
 * <p>Документация: <a href="https://iss.moex.com/iss/reference/409">ISS: свечи инструмента</a>.
 *
 * @param begin начало интервала
 * @param end конец интервала
 * @param open цена открытия
 * @param close цена закрытия
 * @param high максимум
 * @param low минимум
 * @param value объём в валюте
 * @param volume объём в лотах
 */
public record IssCandleDto(
    Instant begin,
    Instant end,
    BigDecimal open,
    BigDecimal close,
    BigDecimal high,
    BigDecimal low,
    BigDecimal value,
    BigDecimal volume) {}
