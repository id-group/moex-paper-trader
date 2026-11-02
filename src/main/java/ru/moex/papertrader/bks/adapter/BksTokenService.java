package ru.moex.papertrader.bks.adapter;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import ru.moex.papertrader.core.marketdata.Clock;

/**
 * Обмен refresh-токена на access-токен (ADR-7): refresh-токены задаются конфигурацией (значения —
 * из переменных окружения), access-токен — opaque, TTL 24 ч по умолчанию.
 */
@Service
public class BksTokenService {

  private static final Logger log = LoggerFactory.getLogger(BksTokenService.class);
  private static final String GRANT_TYPE = "refresh_token";
  private static final String WRITE_CLIENT = "trade-api-write";
  private static final String READ_CLIENT = "trade-api-read";

  private final BksProperties properties;
  private final Clock clock;
  private final Map<String, BksProperties.RefreshToken> refreshTokens;
  private final Map<String, BksAccessSession> accessTokens = new ConcurrentHashMap<>();

  public BksTokenService(BksProperties properties, Clock clock) {
    this.properties = properties;
    this.clock = clock;
    this.refreshTokens = buildRefreshTokens(properties);
  }

  /**
   * Проверяет refresh-токен и выдаёт access-токен.
   *
   * @throws BksInvalidGrantException если grant_type, client_id или refresh-токен неверны
   */
  public BksTokenIssue exchange(String clientId, String refreshToken, String grantType) {
    if (!GRANT_TYPE.equals(grantType)) {
      throw new BksInvalidGrantException("grant_type должен быть refresh_token");
    }
    if (!WRITE_CLIENT.equals(clientId) && !READ_CLIENT.equals(clientId)) {
      throw new BksInvalidGrantException("Неизвестный client_id");
    }
    // Неизменяемая карта бросает NPE на get(null), поэтому пустой токен отсекаем заранее
    if (refreshToken == null || refreshToken.isBlank()) {
      throw new BksInvalidGrantException("refresh_token не должен быть пустым");
    }
    BksProperties.RefreshToken configured = refreshTokens.get(refreshToken);
    if (configured == null || !clientId.equals(configured.clientId())) {
      throw new BksInvalidGrantException("Неверный refresh_token или client_id");
    }
    BksRights rights = WRITE_CLIENT.equals(clientId) ? BksRights.TRADE : BksRights.READ_ONLY;
    Instant now = clock.instant();
    // resolve() удаляет только запрошенный токен, поэтому чистим карту при каждой выдаче,
    // иначе сессии, по которым никто не пришёл, остаются навсегда
    accessTokens.values().removeIf(s -> !s.expiresAt().isAfter(now));
    Instant expiresAt = now.plus(properties.auth().accessTokenTtl());
    String accessToken = "bks-" + UUID.randomUUID();
    BksAccessSession session = new BksAccessSession(accessToken, clientId, rights, expiresAt);
    accessTokens.put(accessToken, session);
    log.debug("Выдан access-токен для client_id={} (права {})", clientId, rights);
    return new BksTokenIssue(
        accessToken,
        properties.auth().accessTokenTtl().toSeconds(),
        properties.auth().refreshTokenTtl().toSeconds(),
        refreshToken,
        "bearer",
        "0",
        UUID.randomUUID().toString(),
        clientId);
  }

  /** Находит действующий access-токен; просроченный токен удаляется. */
  public Optional<BksAccessSession> resolve(String accessToken) {
    if (accessToken == null || accessToken.isBlank()) {
      return Optional.empty();
    }
    BksAccessSession session = accessTokens.get(accessToken);
    if (session == null) {
      return Optional.empty();
    }
    if (!session.expiresAt().isAfter(clock.instant())) {
      accessTokens.remove(accessToken);
      return Optional.empty();
    }
    return Optional.of(session);
  }

  /** Количество удерживаемых сессий; только для тестов. */
  int activeSessionCount() {
    return accessTokens.size();
  }

  private static Map<String, BksProperties.RefreshToken> buildRefreshTokens(
      BksProperties properties) {
    Map<String, BksProperties.RefreshToken> tokens = new LinkedHashMap<>();
    for (BksProperties.RefreshToken token : properties.auth().refreshTokens()) {
      if (token != null && token.token() != null && !token.token().isBlank()) {
        tokens.put(token.token(), token);
      }
    }
    return Map.copyOf(tokens);
  }
}
