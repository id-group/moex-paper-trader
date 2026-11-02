package ru.moex.papertrader.bks.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;
import ru.moex.papertrader.bks.contract.ErrorResponse;
import ru.moex.papertrader.bks.contract.ErrorType;
import ru.moex.papertrader.core.marketdata.Clock;
import ru.moex.papertrader.core.marketdata.SystemClock;
import ru.moex.papertrader.testutil.FixedClock;

/** {@link BksErrorFactory} берёт момент ошибки из порта {@code Clock}, а не системных часов. */
class BksErrorFactoryTest {

  @Test
  void timestampComesFromClock() {
    Clock fixed = FixedClock.at(Instant.parse("2026-11-02T12:00:00Z"));
    BksErrorFactory factory = new BksErrorFactory(fixed);

    ErrorResponse response = factory.error(ErrorType.BAD_REQUEST, "field", "message");

    assertThat(response.timestamp()).isEqualTo(fixed.instant().toEpochMilli());
  }

  @Test
  void buildsFullErrorContract() {
    BksErrorFactory factory = new BksErrorFactory(new SystemClock());

    ErrorResponse response =
        factory.error(ErrorType.VALIDATION_ERROR, "refresh_token", "неверный токен");

    assertThat(response.type()).isEqualTo(ErrorType.VALIDATION_ERROR);
    assertThat(response.traceId()).isNotBlank();
    assertThat(response.errors()).hasSize(1);
    assertThat(response.errors().get(0).type()).isEqualTo("VALIDATION_ERROR");
    assertThat(response.errors().get(0).field()).isEqualTo("refresh_token");
    assertThat(response.displayOptions()).isEmpty();
  }

  @Test
  void nullMessageProducesNullPayload() {
    BksErrorFactory factory = new BksErrorFactory(new SystemClock());

    ErrorResponse response = factory.error(ErrorType.INTERNAL_SERVER_ERROR, null, null);

    assertThat(response.errors()).hasSize(1);
    assertThat(response.errors().get(0).payload()).isNull();
  }
}
