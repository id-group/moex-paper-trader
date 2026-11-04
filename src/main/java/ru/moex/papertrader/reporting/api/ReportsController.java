package ru.moex.papertrader.reporting.api;

import java.time.LocalDate;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ru.moex.papertrader.infra.services.ReportService;
import ru.moex.papertrader.infra.services.exception.InvalidRequestException;
import ru.moex.papertrader.infra.services.exception.ResourceNotFoundException;

/**
 * Брокер-нейтральный внутренний API отчётов.
 *
 * <p>Не является частью контракта БКС и не требует брокерского токена: эндпоинты предназначены для
 * доверенного контура (роутер/администратор). В продакшене доступ должен ограничиваться сетевой
 * политикой; отдельная аутентификация — кандидат на следующий этап.
 */
@RestController
@RequestMapping("/api/internal/reports")
public class ReportsController {

  private final ReportService reportService;

  public ReportsController(ReportService reportService) {
    this.reportService = reportService;
  }

  /** Формирует отчёт за период, сохраняет его и экспортирует JSON/CSV. */
  @PostMapping
  public ReportResponse generate(@RequestBody GenerateReportRequest request) {
    if (request == null) {
      throw new InvalidRequestException("Тело запроса обязательно");
    }
    LocalDate from = request.from();
    LocalDate to = request.to();
    if (from == null || to == null) {
      throw new InvalidRequestException("from и to обязательны");
    }
    if (to.isBefore(from)) {
      throw new InvalidRequestException("to не может быть раньше from");
    }
    ReportService.StoredReport stored =
        reportService.generate(request.accountId(), request.label(), from, to, request.backtest());
    return toResponse(stored);
  }

  /** Список сохранённых отчётов; фильтр по счёту необязателен. */
  @GetMapping
  public List<ReportSummaryResponse> list(
      @RequestParam(name = "accountId", required = false) String accountId) {
    return reportService.list(accountId).stream()
        .map(
            summary ->
                new ReportSummaryResponse(
                    summary.id(),
                    summary.accountId(),
                    summary.label(),
                    summary.periodFrom(),
                    summary.periodTo(),
                    summary.generatedAt()))
        .toList();
  }

  /** Сохранённый отчёт по идентификатору. */
  @GetMapping("/{id}")
  public ReportResponse get(@PathVariable long id) {
    return reportService
        .find(id)
        .map(ReportsController::toResponse)
        .orElseThrow(() -> new ResourceNotFoundException("Отчёт " + id + " не найден"));
  }

  /** CSV-журнал сделок сохранённого отчёта. */
  @GetMapping(value = "/{id}/journal.csv", produces = "text/csv;charset=UTF-8")
  public ResponseEntity<String> journalCsv(@PathVariable long id) {
    if (reportService.find(id).isEmpty()) {
      throw new ResourceNotFoundException("Отчёт " + id + " не найден");
    }
    String csv = reportService.journalCsv(id);
    return ResponseEntity.ok()
        .header(
            HttpHeaders.CONTENT_DISPOSITION,
            "attachment; filename=\"report-" + id + "-journal.csv\"")
        .contentType(new MediaType("text", "csv", java.nio.charset.StandardCharsets.UTF_8))
        .body(csv);
  }

  private static ReportResponse toResponse(ReportService.StoredReport stored) {
    return new ReportResponse(stored.id(), stored.payload(), stored.files());
  }
}
