package ru.moex.papertrader.infra.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;

/** Позиция счёта по инструменту: одна строка на пару (счёт, инструмент). */
@Entity
@Table(name = "positions")
public class PositionEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "account_id", nullable = false, length = 64)
  private String accountId;

  @Column(name = "instrument_id", nullable = false)
  private Long instrumentId;

  @Column(name = "quantity", nullable = false)
  private long quantity;

  @Column(name = "average_price", nullable = false, precision = 19, scale = 6)
  private BigDecimal averagePrice;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  /** Конструктор для JPA. */
  protected PositionEntity() {}

  public PositionEntity(
      String accountId,
      Long instrumentId,
      long quantity,
      BigDecimal averagePrice,
      Instant updatedAt) {
    this.accountId = accountId;
    this.instrumentId = instrumentId;
    this.quantity = quantity;
    this.averagePrice = averagePrice;
    this.updatedAt = updatedAt;
  }

  public Long getId() {
    return id;
  }

  public String getAccountId() {
    return accountId;
  }

  public Long getInstrumentId() {
    return instrumentId;
  }

  public long getQuantity() {
    return quantity;
  }

  public BigDecimal getAveragePrice() {
    return averagePrice;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }
}
