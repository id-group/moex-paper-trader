package ru.moex.papertrader.core.domain;

import java.math.BigDecimal;

/** Инструмент, торгуемый на Мосбирже: тикер, режим торгов и параметры лота. */
public record Instrument(
    String ticker,
    String classCode,
    String type,
    String shortName,
    String displayName,
    String isin,
    BigDecimal lotSize,
    BigDecimal minimumStep,
    String currency) {

  public Instrument {
    ticker = requireText(ticker, "ticker");
    classCode = requireText(classCode, "classCode");
    type = requireText(type, "type");
    currency = requireText(currency, "currency");
    if (lotSize == null || lotSize.signum() <= 0) {
      throw new IllegalArgumentException("lotSize должен быть положительным");
    }
    if (minimumStep == null || minimumStep.signum() <= 0) {
      throw new IllegalArgumentException("minimumStep должен быть положительным");
    }
  }

  private static String requireText(String value, String name) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(name + " не может быть пустым");
    }
    return value;
  }
}
