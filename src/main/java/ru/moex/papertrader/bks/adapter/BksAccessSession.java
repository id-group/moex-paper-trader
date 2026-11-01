package ru.moex.papertrader.bks.adapter;

import java.time.Instant;

/** Выданный access-токен и его права. */
public record BksAccessSession(
    String accessToken, String clientId, BksRights rights, Instant expiresAt) {}
