package ru.moex.papertrader.bks.contract;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.math.BigDecimal;

/** Уровень стакана (схема {@code Volume}). */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record Volume(BigDecimal price, Long quantity) {}
