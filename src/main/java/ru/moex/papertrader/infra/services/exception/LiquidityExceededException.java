package ru.moex.papertrader.infra.services.exception;

/**
 * Объём заявки превышает допустимую долю среднего дневного объёма (ADV), а политика ликвидности
 * настроена на {@code REJECT}. Контракт БКС отображает ошибку в HTTP 429 {@code
 * RESOURCE_EXHAUSTED}.
 */
public class LiquidityExceededException extends RuntimeException {

  public LiquidityExceededException(String message) {
    super(message);
  }
}
