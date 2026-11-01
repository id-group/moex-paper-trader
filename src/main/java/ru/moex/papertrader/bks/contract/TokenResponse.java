package ru.moex.papertrader.bks.contract;

import com.fasterxml.jackson.annotation.JsonProperty;

/** Ответ Keycloak-эндпоинта обмена refresh-токена на access-токен. */
public record TokenResponse(
    @JsonProperty("access_token") String accessToken,
    @JsonProperty("expires_in") long expiresIn,
    @JsonProperty("refresh_expires_in") long refreshExpiresIn,
    @JsonProperty("refresh_token") String refreshToken,
    @JsonProperty("token_type") String tokenType,
    @JsonProperty("not-before-policy") String notBeforePolicy,
    @JsonProperty("session_state") String sessionState,
    String scope) {}
