package ru.moex.papertrader.bks.contract;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

/** Свеча (схема {@code BarResponse}). */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record BarResponse(
    OffsetDateTime time,
    BigDecimal open,
    BigDecimal close,
    BigDecimal high,
    BigDecimal low,
    BigDecimal volume) {}
