package ru.moex.papertrader.infra.algoapi.dto;

import java.math.BigDecimal;

/**
 * Инструмент MOEX ISS из блока {@code securities} ответа {@code
 * .../boards/{board}/securities.json}.
 *
 * <p>Поля соответствуют колонкам API: {@code SECID}, {@code BOARDID}, {@code SHORTNAME}, {@code
 * SECNAME}, {@code ISIN}, {@code LOTSIZE}, {@code MINSTEP}, {@code CURRENCYID}.
 *
 * <p>Документация: <a href="https://iss.moex.com/iss/reference/353">ISS: инструменты по режиму
 * торгов</a>.
 *
 * @param secId код инструмента
 * @param boardId режим торгов
 * @param shortName короткое наименование
 * @param secName полное наименование
 * @param isin ISIN (может отсутствовать)
 * @param lotSize размер лота в штуках
 * @param minStep минимальный шаг цены
 * @param currencyId код валюты ISS ({@code SUR} — рубль)
 */
public record IssSecurityDto(
    String secId,
    String boardId,
    String shortName,
    String secName,
    String isin,
    BigDecimal lotSize,
    BigDecimal minStep,
    String currencyId) {}
