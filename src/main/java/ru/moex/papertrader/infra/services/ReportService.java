package ru.moex.papertrader.infra.services;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import ru.moex.papertrader.core.reporting.BacktestInput;
import ru.moex.papertrader.core.reporting.ReportPayload;

/**
 * Генерация, хранение и выгрузка отчётов прогона.
 *
 * <p>Отчёт собирается из {@code nav_daily}, {@code fills}, {@code orders} и {@code instruments} за
 * указанный период, сохраняется в {@code reports} и экспортируется в JSON/CSV. Сравнение с
 * бэктестом необязательно: при его наличии результат пишется в {@code backtest_results}.
 */
public interface ReportService {

  /**
   * Формирует отчёт за период и сохраняет его.
   *
   * @param accountId счёт; {@code null} — счёт из {@code emulator.portfolio.account}
   * @param label метка прогона; может быть {@code null}
   * @param from первый день периода включительно
   * @param to последний день периода включительно
   * @param backtest эталонный бэктест; {@code null} — без сравнения
   */
  StoredReport generate(
      String accountId, String label, LocalDate from, LocalDate to, BacktestInput backtest);

  /** Сохранённые отчёты; {@code accountId = null} — по всем счетам. */
  List<ReportSummary> list(String accountId);

  /** Сохранённый отчёт по идентификатору. */
  Optional<StoredReport> find(long id);

  /** CSV-журнал сохранённого отчёта за его период. */
  String journalCsv(long id);

  /**
   * Сохранённый отчёт.
   *
   * @param files пути экспортированных файлов: {@code json} и {@code csv}
   */
  record StoredReport(long id, ReportPayload payload, Map<String, String> files) {
    public StoredReport {
      files = Map.copyOf(files);
    }
  }

  /** Краткая карточка сохранённого отчёта для списка. */
  record ReportSummary(
      long id,
      String accountId,
      String label,
      LocalDate periodFrom,
      LocalDate periodTo,
      Instant generatedAt) {}
}
