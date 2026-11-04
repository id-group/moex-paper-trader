package ru.moex.papertrader.infra.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Сохранённый отчёт по счёту за период (этап 6).
 *
 * <p>{@code payload} — канонический JSON доменного {@code ReportPayload}: кривая NAV, метрики,
 * журнал сделок и разбивка по стратегиям. Отдельные таблицы под точки кривой и строки журнала не
 * вводятся: форма отчёта задаётся доменом, а не схемой БД (ср. {@link MarketDataSnapshotEntity}).
 */
@Entity
@Table(name = "reports")
public class ReportEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "account_id", nullable = false, length = 64)
  private String accountId;

  /** Произвольная метка отчёта; {@code null} — метки нет. */
  @Column(name = "label", length = 128)
  private String label;

  @Column(name = "period_from", nullable = false)
  private LocalDate periodFrom;

  @Column(name = "period_to", nullable = false)
  private LocalDate periodTo;

  @Column(name = "generated_at", nullable = false)
  private Instant generatedAt;

  /** JSONB-полезная нагрузка: сериализованный {@code ReportPayload}. */
  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "payload", nullable = false, columnDefinition = "jsonb")
  private String payload;

  /** Конструктор для JPA. */
  protected ReportEntity() {}

  public ReportEntity(
      String accountId,
      String label,
      LocalDate periodFrom,
      LocalDate periodTo,
      Instant generatedAt,
      String payload) {
    this.accountId = accountId;
    this.label = label;
    this.periodFrom = periodFrom;
    this.periodTo = periodTo;
    this.generatedAt = generatedAt;
    this.payload = payload;
  }

  public Long getId() {
    return id;
  }

  public String getAccountId() {
    return accountId;
  }

  public String getLabel() {
    return label;
  }

  public LocalDate getPeriodFrom() {
    return periodFrom;
  }

  public LocalDate getPeriodTo() {
    return periodTo;
  }

  public Instant getGeneratedAt() {
    return generatedAt;
  }

  public String getPayload() {
    return payload;
  }
}
