package ru.moex.papertrader.reporting.api;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import ru.moex.papertrader.core.marketdata.Clock;
import ru.moex.papertrader.infra.services.exception.InvalidRequestException;
import ru.moex.papertrader.infra.services.exception.ResourceNotFoundException;

/**
 * Ошибки внутреннего API отчётов в собственном формате, а не в контракте БКС. Advice ограничен
 * пакетом отчётов и имеет приоритет над глобальным обработчиком БКС.
 */
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(basePackages = "ru.moex.papertrader.reporting.api")
public class InternalReportExceptionHandler {

  private static final Logger log = LoggerFactory.getLogger(InternalReportExceptionHandler.class);

  private final Clock clock;

  public InternalReportExceptionHandler(Clock clock) {
    this.clock = clock;
  }

  @ExceptionHandler({InvalidRequestException.class, IllegalArgumentException.class})
  public ResponseEntity<InternalErrorResponse> badRequest(RuntimeException exception) {
    return respond(HttpStatus.BAD_REQUEST, exception.getMessage());
  }

  @ExceptionHandler({
    HttpMessageNotReadableException.class,
    MethodArgumentTypeMismatchException.class
  })
  public ResponseEntity<InternalErrorResponse> malformedRequest(Exception exception) {
    return respond(HttpStatus.BAD_REQUEST, "Некорректный запрос: " + exception.getMessage());
  }

  @ExceptionHandler(ResourceNotFoundException.class)
  public ResponseEntity<InternalErrorResponse> notFound(ResourceNotFoundException exception) {
    return respond(HttpStatus.NOT_FOUND, exception.getMessage());
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<InternalErrorResponse> internalError(Exception exception) {
    log.error("Необработанная ошибка внутреннего API отчётов", exception);
    return respond(HttpStatus.INTERNAL_SERVER_ERROR, "Внутренняя ошибка формирования отчёта");
  }

  private ResponseEntity<InternalErrorResponse> respond(HttpStatus status, String message) {
    return ResponseEntity.status(status)
        .body(new InternalErrorResponse(clock.instant(), status.value(), message));
  }
}
