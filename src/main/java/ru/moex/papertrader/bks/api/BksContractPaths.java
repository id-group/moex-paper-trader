package ru.moex.papertrader.bks.api;

/**
 * Пути боевого контракта БКС Trade API ({@code trade-api.bcs.ru}): помимо изолированного префикса
 * {@code ${emulator.brokers.bks.base-path}/v1} операции открыты и по боевому {@code /api/v1/**} —
 * клиенты, написанные строго по контракту ({@code docs/bcs-api}), работают без перемонтирования
 * путей (решение владельца, 2026-11-13).
 */
final class BksContractPaths {

  /** Боевой префикс бизнес-операций контракта БКС Trade API. */
  static final String API_V1_PREFIX = "/api/v1";

  /** Боевой путь обмена refresh-токена (у корня хоста, вне {@code /api/v1}). */
  static final String TOKEN_PATH =
      "/trade-api-keycloak/realms/tradeapi/protocol/openid-connect/token";

  private BksContractPaths() {}
}
