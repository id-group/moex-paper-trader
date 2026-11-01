package ru.moex.papertrader.infra.services.exception;

/** Конфликт состояния (контракт БКС: HTTP 422). */
public class ConflictException extends RuntimeException {

  public ConflictException(String message) {
    super(message);
  }
}
