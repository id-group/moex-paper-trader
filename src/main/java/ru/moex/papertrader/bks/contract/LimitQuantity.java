package ru.moex.papertrader.bks.contract;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.math.BigDecimal;

/** Количество актива в разрезе срока расчётов (схема quantity/quantityBatch в {@code limits}). */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record LimitQuantity(String type, BigDecimal value) {}
