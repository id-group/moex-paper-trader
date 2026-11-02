package ru.moex.papertrader.bks.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;

class BksTokenServiceTest {

  private static final Instant NOW = Instant.parse("2026-11-01T00:00:00Z");

  @Test
  void exchangesTradeRefreshTokenForAccessToken() {
    BksTokenService service = service(Duration.ofHours(24));

    BksTokenIssue issue = service.exchange("trade-api-write", "trade-refresh", "refresh_token");

    assertThat(issue.accessToken()).startsWith("bks-");
    assertThat(issue.expiresIn()).isEqualTo(86_400);
    assertThat(issue.refreshExpiresIn()).isEqualTo(7_776_000);
    assertThat(issue.tokenType()).isEqualTo("bearer");
    assertThat(issue.notBeforePolicy()).isEqualTo("0");
    assertThat(issue.scope()).isEqualTo("trade-api-write");
    assertThat(service.resolve(issue.accessToken()))
        .get()
        .extracting(BksAccessSession::rights)
        .isEqualTo(BksRights.TRADE);
  }

  @Test
  void readClientGetsReadOnlyRights() {
    BksTokenService service = service(Duration.ofHours(24));

    BksTokenIssue issue = service.exchange("trade-api-read", "read-refresh", "refresh_token");

    assertThat(service.resolve(issue.accessToken()))
        .get()
        .extracting(BksAccessSession::rights)
        .isEqualTo(BksRights.READ_ONLY);
  }

  @Test
  void rejectsUnknownRefreshToken() {
    BksTokenService service = service(Duration.ofHours(24));

    assertThatThrownBy(() -> service.exchange("trade-api-write", "unknown", "refresh_token"))
        .isInstanceOf(BksInvalidGrantException.class);
  }

  @Test
  void rejectsMissingRefreshToken() {
    BksTokenService service = service(Duration.ofHours(24));

    assertThatThrownBy(() -> service.exchange("trade-api-write", null, "refresh_token"))
        .isInstanceOf(BksInvalidGrantException.class)
        .isNotInstanceOf(NullPointerException.class)
        .hasMessageContaining("refresh_token");
  }

  @Test
  void rejectsBlankRefreshToken() {
    BksTokenService service = service(Duration.ofHours(24));

    assertThatThrownBy(() -> service.exchange("trade-api-write", "   ", "refresh_token"))
        .isInstanceOf(BksInvalidGrantException.class)
        .isNotInstanceOf(NullPointerException.class)
        .hasMessageContaining("refresh_token");
  }

  @Test
  void rejectsClientMismatch() {
    BksTokenService service = service(Duration.ofHours(24));

    assertThatThrownBy(() -> service.exchange("trade-api-read", "trade-refresh", "refresh_token"))
        .isInstanceOf(BksInvalidGrantException.class);
  }

  @Test
  void rejectsUnknownClientAndGrant() {
    BksTokenService service = service(Duration.ofHours(24));

    assertThatThrownBy(() -> service.exchange("other", "trade-refresh", "refresh_token"))
        .isInstanceOf(BksInvalidGrantException.class);
    assertThatThrownBy(() -> service.exchange("trade-api-write", "trade-refresh", "password"))
        .isInstanceOf(BksInvalidGrantException.class);
  }

  @Test
  void expiredAccessTokenIsNotResolved() {
    BksTokenService service = service(Duration.ZERO);

    BksTokenIssue issue = service.exchange("trade-api-write", "trade-refresh", "refresh_token");

    assertThat(service.resolve(issue.accessToken())).isEmpty();
    assertThat(service.resolve("unknown")).isEmpty();
    assertThat(service.resolve(null)).isEmpty();
  }

  @Test
  void expiredSessionsAreRemovedOnNextExchange() {
    MutableClock clock = new MutableClock(NOW);
    BksTokenService service = service(Duration.ofHours(24), clock);

    service.exchange("trade-api-write", "trade-refresh", "refresh_token");
    assertThat(service.activeSessionCount()).isEqualTo(1);

    clock.advance(Duration.ofHours(25));
    service.exchange("trade-api-write", "trade-refresh", "refresh_token");

    assertThat(service.activeSessionCount()).isEqualTo(1);
  }

  @Test
  void keepsLiveSessionsWhenNewTokenIsIssued() {
    MutableClock clock = new MutableClock(NOW);
    BksTokenService service = service(Duration.ofHours(24), clock);

    BksTokenIssue first = service.exchange("trade-api-write", "trade-refresh", "refresh_token");
    service.exchange("trade-api-read", "read-refresh", "refresh_token");

    assertThat(service.activeSessionCount()).isEqualTo(2);
    assertThat(service.resolve(first.accessToken())).isPresent();
  }

  private static BksTokenService service(Duration accessTtl) {
    return service(accessTtl, Clock.fixed(NOW, ZoneOffset.UTC));
  }

  private static BksTokenService service(Duration accessTtl, Clock clock) {
    BksProperties properties =
        new BksProperties(
            "/api/bks",
            new BksProperties.Auth(
                accessTtl,
                Duration.ofDays(90),
                List.of(
                    new BksProperties.RefreshToken("trade-refresh", "trade-api-write"),
                    new BksProperties.RefreshToken("read-refresh", "trade-api-read"))),
            new BksProperties.RateLimit(false, 10));
    return new BksTokenService(properties, clock);
  }

  /** Управляемые часы: позволяют сдвинуть время за пределы TTL access-токена. */
  private static final class MutableClock extends Clock {

    private Instant now;

    private MutableClock(Instant now) {
      this.now = now;
    }

    void advance(Duration duration) {
      now = now.plus(duration);
    }

    @Override
    public ZoneId getZone() {
      return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
      return this;
    }

    @Override
    public Instant instant() {
      return now;
    }
  }
}
