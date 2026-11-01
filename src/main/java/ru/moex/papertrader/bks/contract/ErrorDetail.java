package ru.moex.papertrader.bks.contract;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.Map;

/** Детализация ошибки контракта БКС. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorDetail(String type, String field, Map<String, Object> payload) {}
