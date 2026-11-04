package ru.moex.papertrader.reporting.api;

import java.util.Map;
import ru.moex.papertrader.core.reporting.ReportPayload;

/**
 * Ответ внутреннего API отчётов.
 *
 * @param id идентификатор сохранённого отчёта
 * @param report канонический отчёт
 * @param files пути файлов экспорта ({@code json}, {@code csv})
 */
public record ReportResponse(long id, ReportPayload report, Map<String, String> files) {}
