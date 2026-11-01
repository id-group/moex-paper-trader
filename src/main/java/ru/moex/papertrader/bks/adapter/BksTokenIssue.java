package ru.moex.papertrader.bks.adapter;

/** Пара токенов, выдаваемая эндпоинтом авторизации. */
public record BksTokenIssue(
    String accessToken,
    long expiresIn,
    long refreshExpiresIn,
    String refreshToken,
    String tokenType,
    String notBeforePolicy,
    String sessionState,
    String scope) {}
