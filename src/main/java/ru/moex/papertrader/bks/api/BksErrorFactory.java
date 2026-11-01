package ru.moex.papertrader.bks.api;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;
import ru.moex.papertrader.bks.contract.ErrorDetail;
import ru.moex.papertrader.bks.contract.ErrorResponse;
import ru.moex.papertrader.bks.contract.ErrorType;

/** Сборка единого контракта ошибки БКС (ADR-6). */
@Component
public class BksErrorFactory {

  public ErrorResponse error(ErrorType type, String field, String message) {
    Map<String, Object> payload =
        message == null ? null : Map.of("message", Map.of("text", message));
    return new ErrorResponse(
        System.currentTimeMillis(),
        UUID.randomUUID().toString(),
        type,
        List.of(new ErrorDetail(type.name(), field, payload)),
        Map.of());
  }
}
