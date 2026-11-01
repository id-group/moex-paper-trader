package ru.moex.papertrader.bks.contract;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import java.util.Map;

/** Единый контракт ошибки БКС (ADR-6). */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponse(
    long timestamp,
    String traceId,
    ErrorType type,
    List<ErrorDetail> errors,
    Map<String, Object> displayOptions) {}
