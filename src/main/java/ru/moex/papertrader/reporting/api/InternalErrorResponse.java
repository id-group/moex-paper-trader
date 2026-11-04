package ru.moex.papertrader.reporting.api;

import java.time.Instant;

/** Ошибка внутреннего API отчётов (не контракт БКС). */
public record InternalErrorResponse(Instant timestamp, int status, String message) {}
