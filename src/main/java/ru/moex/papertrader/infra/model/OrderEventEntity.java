package ru.moex.papertrader.infra.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import ru.moex.papertrader.core.domain.OrderStatus;
import ru.moex.papertrader.core.execution.OrderEventType;

/** Строка журнала {@code order_events}: переход жизненного цикла заявки. */
@Entity
@Table(name = "order_events")
public class OrderEventEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "order_id", nullable = false, length = 64)
  private String orderId;

  @Enumerated(EnumType.STRING)
  @Column(name = "event_type", nullable = false, length = 32)
  private OrderEventType eventType;

  @Enumerated(EnumType.STRING)
  @Column(name = "previous_status", length = 32)
  private OrderStatus previousStatus;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 32)
  private OrderStatus status;

  @Column(name = "quantity")
  private Long quantity;

  @Column(name = "price", precision = 19, scale = 6)
  private BigDecimal price;

  @Column(name = "reason", length = 512)
  private String reason;

  @Column(name = "occurred_at", nullable = false)
  private Instant occurredAt;

  /** Конструктор для JPA. */
  protected OrderEventEntity() {}

  public OrderEventEntity(
      String orderId,
      OrderEventType eventType,
      OrderStatus previousStatus,
      OrderStatus status,
      Long quantity,
      BigDecimal price,
      String reason,
      Instant occurredAt) {
    this.orderId = orderId;
    this.eventType = eventType;
    this.previousStatus = previousStatus;
    this.status = status;
    this.quantity = quantity;
    this.price = price;
    this.reason = reason;
    this.occurredAt = occurredAt;
  }

  public Long getId() {
    return id;
  }

  public String getOrderId() {
    return orderId;
  }

  public OrderEventType getEventType() {
    return eventType;
  }

  public OrderStatus getPreviousStatus() {
    return previousStatus;
  }

  public OrderStatus getStatus() {
    return status;
  }

  public Long getQuantity() {
    return quantity;
  }

  public BigDecimal getPrice() {
    return price;
  }

  public String getReason() {
    return reason;
  }

  public Instant getOccurredAt() {
    return occurredAt;
  }
}
