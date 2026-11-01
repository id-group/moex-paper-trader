package ru.moex.papertrader.bks.adapter;

import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Настройки адаптера БКС: префикс монтирования, токены и лимиты. */
@ConfigurationProperties(prefix = "emulator.brokers.bks")
public record BksProperties(String basePath, Auth auth, RateLimit rateLimit) {

  public BksProperties {
    basePath = basePath == null || basePath.isBlank() ? "/api/bks" : basePath;
    auth = auth == null ? new Auth(null, null, null) : auth;
    rateLimit = rateLimit == null ? new RateLimit(false, 10) : rateLimit;
  }

  /** Параметры авторизации Keycloak-подобного эндпоинта. */
  public record Auth(
      Duration accessTokenTtl, Duration refreshTokenTtl, List<RefreshToken> refreshTokens) {

    public Auth {
      accessTokenTtl = accessTokenTtl == null ? Duration.ofHours(24) : accessTokenTtl;
      refreshTokenTtl = refreshTokenTtl == null ? Duration.ofDays(90) : refreshTokenTtl;
      refreshTokens = refreshTokens == null ? List.of() : List.copyOf(refreshTokens);
    }
  }

  /** Refresh-токен и его клиент (trade-api-write / trade-api-read). */
  public record RefreshToken(String token, String clientId) {}

  /** Заложенная конфигурация rate limit (эмуляция 429 — этап 5). */
  public record RateLimit(boolean enabled, int requestsPerSecond) {}
}
