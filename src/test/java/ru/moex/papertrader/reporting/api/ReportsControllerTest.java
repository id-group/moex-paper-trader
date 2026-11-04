package ru.moex.papertrader.reporting.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import ru.moex.papertrader.core.marketdata.Clock;
import ru.moex.papertrader.core.reporting.PerformanceMetrics;
import ru.moex.papertrader.core.reporting.ReportPayload;
import ru.moex.papertrader.infra.services.ReportService;
import ru.moex.papertrader.testutil.FixedClock;

class ReportsControllerTest {

  private static final Clock CLOCK = FixedClock.at(Instant.parse("2026-11-04T12:00:00Z"));
  private static final LocalDate FROM = LocalDate.of(2026, 11, 1);
  private static final LocalDate TO = LocalDate.of(2026, 11, 2);

  private final ReportService reportService = mock(ReportService.class);
  private MockMvc mvc;

  @BeforeEach
  void setUp() {
    mvc =
        MockMvcBuilders.standaloneSetup(new ReportsController(reportService))
            .setControllerAdvice(new InternalReportExceptionHandler(CLOCK))
            .build();
  }

  @Test
  void generateReturnsStoredReportAndFiles() throws Exception {
    when(reportService.generate(eq("ACC-1"), eq("run"), eq(FROM), eq(TO), any()))
        .thenReturn(
            new ReportService.StoredReport(
                7,
                payload("ACC-1", "run"),
                Map.of("json", "report-7.json", "csv", "report-7.csv")));

    mvc.perform(
            post("/api/internal/reports")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"accountId\":\"ACC-1\",\"label\":\"run\",\"from\":\"2026-11-01\","
                        + "\"to\":\"2026-11-02\"}"))
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.id").value(7))
        .andExpect(jsonPath("$.report.accountId").value("ACC-1"))
        .andExpect(jsonPath("$.report.label").value("run"))
        .andExpect(jsonPath("$.files.json").value("report-7.json"));
  }

  @Test
  void invalidPeriodIsRejectedBeforeServiceCall() throws Exception {
    mvc.perform(
            post("/api/internal/reports")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"accountId\":\"ACC-1\",\"from\":\"2026-11-02\",\"to\":\"2026-11-01\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.status").value(400))
        .andExpect(jsonPath("$.message").value("to не может быть раньше from"));

    verify(reportService, never()).generate(any(), any(), any(), any(), any());
  }

  @Test
  void missingReportReturnsInternalNotFound() throws Exception {
    when(reportService.find(99)).thenReturn(Optional.empty());

    mvc.perform(get("/api/internal/reports/99"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.status").value(404))
        .andExpect(jsonPath("$.message").value("Отчёт 99 не найден"));
  }

  @Test
  void journalCsvIsServedAsAttachment() throws Exception {
    when(reportService.find(7)).thenReturn(Optional.of(stored(7)));
    when(reportService.journalCsv(7)).thenReturn("header\n");

    mvc.perform(get("/api/internal/reports/7/journal.csv"))
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith(MediaType.parseMediaType("text/csv")))
        .andExpect(
            header().string("Content-Disposition", "attachment; filename=\"report-7-journal.csv\""))
        .andExpect(content().string("header\n"));
  }

  @Test
  void listMapsSummaries() throws Exception {
    when(reportService.list("ACC-1"))
        .thenReturn(
            List.of(
                new ReportService.ReportSummary(
                    7, "ACC-1", "run", FROM, TO, Instant.parse("2026-11-04T12:00:00Z"))));

    mvc.perform(get("/api/internal/reports").param("accountId", "ACC-1"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].id").value(7))
        .andExpect(jsonPath("$[0].accountId").value("ACC-1"))
        .andExpect(jsonPath("$[0].periodFrom").value("2026-11-01"));
  }

  private static ReportService.StoredReport stored(long id) {
    return new ReportService.StoredReport(
        id, payload("ACC-1", "run"), Map.of("json", "report.json", "csv", "report.csv"));
  }

  private static ReportPayload payload(String account, String label) {
    return new ReportPayload(
        account,
        label,
        Instant.parse("2026-11-04T12:00:00Z"),
        FROM,
        TO,
        List.of(),
        new PerformanceMetrics(null, null, null, null, null, 0),
        List.of(),
        BigDecimal.ZERO,
        BigDecimal.ZERO,
        List.of(),
        null,
        List.of(),
        null);
  }
}
