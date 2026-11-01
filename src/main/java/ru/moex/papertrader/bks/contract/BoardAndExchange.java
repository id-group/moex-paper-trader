package ru.moex.papertrader.bks.contract;

import com.fasterxml.jackson.annotation.JsonInclude;

/** Биржа и режим торгов инструмента (схема {@code BoardAndExchange}). */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record BoardAndExchange(String classCode, String exchange) {}
