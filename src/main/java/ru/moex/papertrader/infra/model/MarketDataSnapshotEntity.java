package ru.moex.papertrader.infra.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Снапшот потока рыночных данных (RECORD/REPLAY).
 *
 * <p>{@code payload} — JSON-представление доменного снапшота конкретного типа {@code kind}: {@code
 * QuoteSnapshot}, {@code OrderBookEvent} или {@code TradeEvent}. Отдельные колонки под котировку,
 * стакан и сделку не вводятся: форма события задаётся доменом, а не схемой БД.
 */
@Entity
@Table(name = "market_data_snapshots")
public class MarketDataSnapshotEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "ts", nullable = false)
  private Instant ts;

  @Column(name = "kind", nullable = false, length = 16)
  private String kind;

  @Column(name = "ticker", nullable = false, length = 32)
  private String ticker;

  @Column(name = "class_code", nullable = false, length = 8)
  private String classCode;

  /** JSONB-полезная нагрузка: сериализованный доменный снапшот. */
  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "payload", nullable = false)
  private String payload;

  /** Конструктор для JPA. */
  protected MarketDataSnapshotEntity() {}

  public MarketDataSnapshotEntity(
      Instant ts, String kind, String ticker, String classCode, String payload) {
    this.ts = ts;
    this.kind = kind;
    this.ticker = ticker;
    this.classCode = classCode;
    this.payload = payload;
  }

  public Long getId() {
    return id;
  }

  public Instant getTs() {
    return ts;
  }

  public String getKind() {
    return kind;
  }

  public String getTicker() {
    return ticker;
  }

  public String getClassCode() {
    return classCode;
  }

  public String getPayload() {
    return payload;
  }
}
