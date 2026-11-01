package ru.moex.papertrader.core.domain;

/** Нарушение семантических правил заявки (контракт БКС: HTTP 422). */
public class InvalidOrderException extends RuntimeException {

  public InvalidOrderException(String message) {
    super(message);
  }
}
