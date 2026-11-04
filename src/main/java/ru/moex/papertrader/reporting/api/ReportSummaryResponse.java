package ru.moex.papertrader.reporting.api;

import java.time.Instant;
import java.time.LocalDate;

/** Карточка сохранённого отчёта для списка. */
public record ReportSummaryResponse(
    long id,
    String accountId,
    String label,
    LocalDate periodFrom,
    LocalDate periodTo,
    Instant generatedAt) {}
