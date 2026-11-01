package ru.moex.papertrader.bks.api;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import ru.moex.papertrader.bks.contract.ErrorResponse;
import ru.moex.papertrader.bks.contract.ErrorType;
import ru.moex.papertrader.core.domain.InvalidOrderException;
import ru.moex.papertrader.infra.services.exception.ConflictException;
import ru.moex.papertrader.infra.services.exception.InvalidRequestException;
import ru.moex.papertrader.infra.services.exception.ResourceNotFoundException;

/** Переводит исключения в {@link ErrorResponse} с HTTP-кодами контракта БКС. */
@RestControllerAdvice
public class BksExceptionHandler {

  private static final Logger log = LoggerFactory.getLogger(BksExceptionHandler.class);
  private static final HttpStatus UNPROCESSABLE = HttpStatus.valueOf(422);

  private final BksErrorFactory errorFactory;

  public BksExceptionHandler(BksErrorFactory errorFactory) {
    this.errorFactory = errorFactory;
  }

  @ExceptionHandler(InvalidRequestException.class)
  public ResponseEntity<ErrorResponse> badRequest(InvalidRequestException exception) {
    return respond(
        HttpStatus.BAD_REQUEST,
        errorFactory.error(ErrorType.VALIDATION_ERROR, null, exception.getMessage()));
  }

  @ExceptionHandler({
    MethodArgumentTypeMismatchException.class,
    MissingServletRequestParameterException.class,
    HttpMessageNotReadableException.class
  })
  public ResponseEntity<ErrorResponse> malformedRequest(Exception exception) {
    return respond(
        HttpStatus.BAD_REQUEST,
        errorFactory.error(ErrorType.VALIDATION_ERROR, null, exception.getMessage()));
  }

  @ExceptionHandler({InvalidOrderException.class, ConflictException.class})
  public ResponseEntity<ErrorResponse> unprocessable(RuntimeException exception) {
    return respond(
        UNPROCESSABLE,
        errorFactory.error(ErrorType.VALIDATION_ERROR, null, exception.getMessage()));
  }

  @ExceptionHandler(ResourceNotFoundException.class)
  public ResponseEntity<ErrorResponse> notFound(ResourceNotFoundException exception) {
    return respond(
        HttpStatus.NOT_FOUND,
        errorFactory.error(ErrorType.NOT_FOUND, null, exception.getMessage()));
  }

  @ExceptionHandler(NoResourceFoundException.class)
  public ResponseEntity<ErrorResponse> unknownPath(NoResourceFoundException exception) {
    return respond(
        HttpStatus.NOT_FOUND, errorFactory.error(ErrorType.NOT_FOUND, null, "Эндпоинт не найден"));
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<ErrorResponse> internalError(Exception exception) {
    log.error("Необработанная ошибка API БКС", exception);
    return respond(
        HttpStatus.INTERNAL_SERVER_ERROR,
        errorFactory.error(ErrorType.INTERNAL_SERVER_ERROR, null, "Внутренняя ошибка эмулятора"));
  }

  private static ResponseEntity<ErrorResponse> respond(HttpStatus status, ErrorResponse body) {
    return ResponseEntity.status(status).body(body);
  }
}
