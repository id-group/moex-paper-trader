package ru.moex.papertrader.bks.contract;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** Инструмент в запросе котировок (схема {@code Instrument}). */
@JsonIgnoreProperties(ignoreUnknown = true)
public record InstrumentRef(String ticker, String classCode) {}
