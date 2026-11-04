package ru.moex.papertrader.infra.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Результат сравнения прогона эмулятора с внешним бэктестом (этап 6).
 *
 * <p>{@code payload} — JSON с входными данными бэктеста и таблицей отклонений метрик. {@code
 * reportId} обязателен только для сравнения, привязанного к сохранённому отчёту; при удалении
 * отчёта ссылка обнуляется миграцией ({@code ON DELETE SET NULL}).
 */
@Entity
@Table(name = "backtest_results")
public class BacktestResultEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  /** Ссылка на сохранённый отчёт; {@code null} — сравнение вне отчёта. */
  @Column(name = "report_id")
  private Long reportId;

  @Column(name = "account_id", nullable = false, length = 64)
  private String accountId;

  @Column(name = "label", nullable = false, length = 128)
  private String label;

  /** Порог относительного отклонения метрик, доли единицы ({@code 0.30} — 30%). */
  @Column(name = "threshold", nullable = false, precision = 9, scale = 6)
  private BigDecimal threshold;

  @Column(name = "passed", nullable = false)
  private boolean passed;

  @Column(name = "compared_at", nullable = false)
  private Instant comparedAt;

  /** JSONB-полезная нагрузка: вход бэктеста и таблица отклонений. */
  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "payload", nullable = false, columnDefinition = "jsonb")
  private String payload;

  /** Конструктор для JPA. */
  protected BacktestResultEntity() {}

  public BacktestResultEntity(
      Long reportId,
      String accountId,
      String label,
      BigDecimal threshold,
      boolean passed,
      Instant comparedAt,
      String payload) {
    this.reportId = reportId;
    this.accountId = accountId;
    this.label = label;
    this.threshold = threshold;
    this.passed = passed;
    this.comparedAt = comparedAt;
    this.payload = payload;
  }

  public Long getId() {
    return id;
  }

  public Long getReportId() {
    return reportId;
  }

  public String getAccountId() {
    return accountId;
  }

  public String getLabel() {
    return label;
  }

  public BigDecimal getThreshold() {
    return threshold;
  }

  public boolean isPassed() {
    return passed;
  }

  public Instant getComparedAt() {
    return comparedAt;
  }

  public String getPayload() {
    return payload;
  }
}
