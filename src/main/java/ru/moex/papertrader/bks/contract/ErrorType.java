package ru.moex.papertrader.bks.contract;

/** Тип ошибки общего контракта БКС ({@code ErrorResponse.type}). */
public enum ErrorType {
  VALIDATION_ERROR,
  RESOURCE_EXHAUSTED,
  USER_BLOCKED,
  BAD_REQUEST,
  NOT_FOUND,
  UNAUTHORIZED,
  FORBIDDEN,
  CONFLICT,
  INTERNAL_SERVER_ERROR,
  SESSION_NOT_FOUND_ERROR,
  SESSION_EXPIRED_ERROR,
  SESSION_FAILED_ERROR,
  USER_NOT_FOUND
}
