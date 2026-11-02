package ru.moex.papertrader.infra.algoapi;

/**
 * Ошибка обращения к внешнему API рыночных данных (AlgoPack или MOEX ISS): сеть, таймаут,
 * неожиданный HTTP-статус или некорректный JSON. Исходная причина — в {@code cause}; исходные
 * исключения Spring/Jackson наружу не выходят, чтобы вызывающий слой зависел только от этого типа.
 */
public class AlgoApiException extends RuntimeException {

  public AlgoApiException(String message) {
    super(message);
  }

  public AlgoApiException(String message, Throwable cause) {
    super(message, cause);
  }
}
