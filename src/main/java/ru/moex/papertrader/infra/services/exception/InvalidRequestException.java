package ru.moex.papertrader.infra.services.exception;

/** Некорректный запрос клиента (контракт БКС: HTTP 400). */
public class InvalidRequestException extends RuntimeException {

  public InvalidRequestException(String message) {
    super(message);
  }

  public InvalidRequestException(String message, Throwable cause) {
    super(message, cause);
  }
}
