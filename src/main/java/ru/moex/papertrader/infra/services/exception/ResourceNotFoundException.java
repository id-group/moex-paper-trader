package ru.moex.papertrader.infra.services.exception;

/** Запрошенный ресурс не найден (контракт БКС: HTTP 404). */
public class ResourceNotFoundException extends RuntimeException {

  public ResourceNotFoundException(String message) {
    super(message);
  }
}
