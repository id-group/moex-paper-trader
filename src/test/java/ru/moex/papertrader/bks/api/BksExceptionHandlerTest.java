package ru.moex.papertrader.bks.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import ru.moex.papertrader.bks.contract.ErrorDetail;
import ru.moex.papertrader.bks.contract.ErrorResponse;
import ru.moex.papertrader.bks.contract.ErrorType;
import ru.moex.papertrader.infra.services.exception.LiquidityExceededException;
import ru.moex.papertrader.testutil.FixedClock;

/** Отображение превышения ADV в контракт ошибки БКС. */
class BksExceptionHandlerTest {

  private final BksExceptionHandler handler =
      new BksExceptionHandler(
          new BksErrorFactory(FixedClock.at(Instant.parse("2026-11-04T10:00:00Z"))));

  @Test
  void liquidityExceededMapsTo429ResourceExhausted() {
    ResponseEntity<ErrorResponse> response =
        handler.liquidityExceeded(
            new LiquidityExceededException("Объём 60 превышает 5% ADV (1000)"));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
    ErrorResponse body = response.getBody();
    assertThat(body).isNotNull();
    assertThat(body.type()).isEqualTo(ErrorType.RESOURCE_EXHAUSTED);
    assertThat(body.errors())
        .extracting(ErrorDetail::type)
        .containsExactly(ErrorType.RESOURCE_EXHAUSTED.name());
    assertThat(body.errors().get(0).payload()).containsKey("message");
  }
}
