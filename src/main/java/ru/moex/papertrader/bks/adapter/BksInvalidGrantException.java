package ru.moex.papertrader.bks.adapter;

/** Неверный grant/refresh-токен (Keycloak-подобный ответ {@code invalid_grant}). */
public class BksInvalidGrantException extends RuntimeException {

  public BksInvalidGrantException(String message) {
    super(message);
  }
}
