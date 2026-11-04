package ru.moex.papertrader.reporting.api;

import java.time.LocalDate;
import ru.moex.papertrader.core.reporting.BacktestInput;

/**
 * Запрос на формирование отчёта (внутренний API, не контракт БКС).
 *
 * @param accountId счёт; {@code null} — счёт из конфигурации портфеля
 * @param label метка прогона; может быть {@code null}
 * @param from первый день периода включительно
 * @param to последний день периода включительно
 * @param backtest эталонный бэктест; {@code null} — без сравнения
 */
public record GenerateReportRequest(
    String accountId, String label, LocalDate from, LocalDate to, BacktestInput backtest) {}
