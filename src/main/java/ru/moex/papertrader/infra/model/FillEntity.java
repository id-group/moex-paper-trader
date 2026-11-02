package ru.moex.papertrader.infra.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * Исполненная сделка. Доменный {@code Fill} четырёхполевой; ссылки на заявку, счёт и инструмент
 * добавлены на уровне infra (решение владельца по открытому вопросу этапа 3).
 */
@Entity
@Table(name = "fills")
public class FillEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "order_id", nullable = false, length = 64)
  private String orderId;

  @Column(name = "account_id", nullable = false, length = 64)
  private String accountId;

  @Column(name = "instrument_id", nullable = false)
  private Long instrumentId;

  @Column(name = "price", nullable = false, precision = 19, scale = 6)
  private BigDecimal price;

  @Column(name = "quantity", nullable = false)
  private long quantity;

  @Column(name = "commission", nullable = false, precision = 19, scale = 2)
  private BigDecimal commission;

  @Column(name = "executed_at", nullable = false)
  private Instant executedAt;

  /** Конструктор для JPA. */
  protected FillEntity() {}

  public FillEntity(
      String orderId,
      String accountId,
      Long instrumentId,
      BigDecimal price,
      long quantity,
      BigDecimal commission,
      Instant executedAt) {
    this.orderId = orderId;
    this.accountId = accountId;
    this.instrumentId = instrumentId;
    this.price = price;
    this.quantity = quantity;
    this.commission = commission;
    this.executedAt = executedAt;
  }

  public Long getId() {
    return id;
  }

  public String getOrderId() {
    return orderId;
  }

  public String getAccountId() {
    return accountId;
  }

  public Long getInstrumentId() {
    return instrumentId;
  }

  public BigDecimal getPrice() {
    return price;
  }

  public long getQuantity() {
    return quantity;
  }

  public BigDecimal getCommission() {
    return commission;
  }

  public Instant getExecutedAt() {
    return executedAt;
  }
}
