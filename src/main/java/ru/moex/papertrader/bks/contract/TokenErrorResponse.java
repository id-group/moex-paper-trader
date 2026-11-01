package ru.moex.papertrader.bks.contract;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/** Ошибка Keycloak-эндпоинта авторизации (HTTP 400, схема {@code invalid_grant}). */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record TokenErrorResponse(
    String error, @JsonProperty("error_description") String errorDescription) {}
