package ru.moex.papertrader.infra.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import ru.moex.papertrader.core.domain.OrderStatus;
import ru.moex.papertrader.core.domain.OrderType;
import ru.moex.papertrader.core.domain.Side;
import ru.moex.papertrader.core.domain.TimeInForce;

/** Заявка эмулятора: биржевой идентификатор {@code order_id} — естественный первичный ключ. */
@Entity
@Table(name = "orders")
public class OrderEntity {

  @Id
  @Column(name = "order_id", nullable = false, length = 64)
  private String orderId;

  @Column(name = "order_num", nullable = false)
  private long orderNum;

  @Column(name = "client_order_id", nullable = false)
  private UUID clientOrderId;

  @Column(name = "account_id", nullable = false, length = 64)
  private String accountId;

  @Column(name = "instrument_id", nullable = false)
  private Long instrumentId;

  @Enumerated(EnumType.STRING)
  @Column(name = "side", nullable = false, length = 16)
  private Side side;

  @Enumerated(EnumType.STRING)
  @Column(name = "order_type", nullable = false, length = 32)
  private OrderType orderType;

  @Enumerated(EnumType.STRING)
  @Column(name = "time_in_force", nullable = false, length = 32)
  private TimeInForce timeInForce;

  @Column(name = "quantity", nullable = false)
  private long quantity;

  @Column(name = "price", precision = 19, scale = 6)
  private BigDecimal price;

  @Column(name = "client_code", length = 64)
  private String clientCode;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 32)
  private OrderStatus status;

  @Column(name = "executed_quantity", nullable = false)
  private long executedQuantity;

  @Column(name = "average_price", precision = 19, scale = 6)
  private BigDecimal averagePrice;

  /** Конструктор для JPA. */
  protected OrderEntity() {}

  public OrderEntity(
      String orderId,
      long orderNum,
      UUID clientOrderId,
      String accountId,
      Long instrumentId,
      Side side,
      OrderType orderType,
      TimeInForce timeInForce,
      long quantity,
      BigDecimal price,
      String clientCode,
      Instant createdAt,
      Instant updatedAt,
      OrderStatus status,
      long executedQuantity,
      BigDecimal averagePrice) {
    this.orderId = orderId;
    this.orderNum = orderNum;
    this.clientOrderId = clientOrderId;
    this.accountId = accountId;
    this.instrumentId = instrumentId;
    this.side = side;
    this.orderType = orderType;
    this.timeInForce = timeInForce;
    this.quantity = quantity;
    this.price = price;
    this.clientCode = clientCode;
    this.createdAt = createdAt;
    this.updatedAt = updatedAt;
    this.status = status;
    this.executedQuantity = executedQuantity;
    this.averagePrice = averagePrice;
  }

  public String getOrderId() {
    return orderId;
  }

  public long getOrderNum() {
    return orderNum;
  }

  public UUID getClientOrderId() {
    return clientOrderId;
  }

  public String getAccountId() {
    return accountId;
  }

  public Long getInstrumentId() {
    return instrumentId;
  }

  public Side getSide() {
    return side;
  }

  public OrderType getOrderType() {
    return orderType;
  }

  public TimeInForce getTimeInForce() {
    return timeInForce;
  }

  public long getQuantity() {
    return quantity;
  }

  public BigDecimal getPrice() {
    return price;
  }

  public String getClientCode() {
    return clientCode;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }

  public OrderStatus getStatus() {
    return status;
  }

  public long getExecutedQuantity() {
    return executedQuantity;
  }

  public BigDecimal getAveragePrice() {
    return averagePrice;
  }
}
