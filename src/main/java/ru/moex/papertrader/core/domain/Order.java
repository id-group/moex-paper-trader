package ru.moex.papertrader.core.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Заявка эмулятора: клиентский UUID, биржевой идентификатор и статус жизненного цикла.
 *
 * <p>Исполнение на этапе 2 виртуальное (заявка принимается и хранится); сделки и переходы в
 * «исполнена» появятся на этапе 5.
 */
public final class Order {

  private final long orderNum;
  private final UUID clientOrderId;
  private final String orderId;
  private final Instrument instrument;
  private final Side side;
  private final OrderType orderType;
  private final TimeInForce timeInForce;
  private final long quantity;
  private final BigDecimal price;
  private final String clientCode;
  private final Instant createdAt;

  private Instant updatedAt;
  private OrderStatus status;
  private long executedQuantity;
  private BigDecimal averagePrice;

  private Order(
      long orderNum,
      UUID clientOrderId,
      String orderId,
      Instrument instrument,
      Side side,
      OrderType orderType,
      TimeInForce timeInForce,
      long quantity,
      BigDecimal price,
      String clientCode,
      Instant createdAt) {
    this.orderNum = orderNum;
    this.clientOrderId = clientOrderId;
    this.orderId = orderId;
    this.instrument = instrument;
    this.side = side;
    this.orderType = orderType;
    this.timeInForce = timeInForce;
    this.quantity = quantity;
    this.price = price;
    this.clientCode = clientCode;
    this.createdAt = createdAt;
    this.updatedAt = createdAt;
    this.status = OrderStatus.NEW;
    this.executedQuantity = 0;
  }

  /**
   * Принимает заявку и присваивает ей начальный статус {@link OrderStatus#NEW}.
   *
   * @throws InvalidOrderException если нарушены семантические правила контракта
   */
  public static Order accept(
      NewOrder request,
      Instrument instrument,
      String orderId,
      long orderNum,
      String clientCode,
      Instant now) {
    Objects.requireNonNull(request, "request");
    Objects.requireNonNull(instrument, "instrument");
    if (request.quantity() <= 0) {
      throw new InvalidOrderException("orderQuantity должен быть больше 0");
    }
    if (!request.orderType().isSupported()) {
      throw new InvalidOrderException(
          "orderType " + request.orderType().code() + " пока не поддерживается");
    }
    if (!request.timeInForce().isSupported()) {
      throw new InvalidOrderException(
          "timeInForce " + request.timeInForce().code() + " пока не поддерживается");
    }
    BigDecimal effectivePrice = request.price();
    if (request.orderType() == OrderType.LIMIT) {
      if (effectivePrice == null || effectivePrice.signum() <= 0) {
        throw new InvalidOrderException("price обязателен для лимитной заявки");
      }
    } else {
      effectivePrice = null;
    }
    return new Order(
        orderNum,
        request.clientOrderId(),
        orderId,
        instrument,
        request.side(),
        request.orderType(),
        request.timeInForce(),
        request.quantity(),
        effectivePrice,
        clientCode,
        now);
  }

  /** Отменяет активную заявку; повторная отмена запрещена. */
  public void cancel(Instant at) {
    if (!status.canBeCancelled()) {
      throw new InvalidOrderException(
          "Заявка " + orderId + " не может быть отменена в статусе " + status.code());
    }
    status = OrderStatus.CANCELED;
    updatedAt = at;
  }

  public long orderNum() {
    return orderNum;
  }

  public UUID clientOrderId() {
    return clientOrderId;
  }

  public String orderId() {
    return orderId;
  }

  public Instrument instrument() {
    return instrument;
  }

  public Side side() {
    return side;
  }

  public OrderType orderType() {
    return orderType;
  }

  public TimeInForce timeInForce() {
    return timeInForce;
  }

  public long quantity() {
    return quantity;
  }

  public BigDecimal price() {
    return price;
  }

  public String clientCode() {
    return clientCode;
  }

  public Instant createdAt() {
    return createdAt;
  }

  public Instant updatedAt() {
    return updatedAt;
  }

  public OrderStatus status() {
    return status;
  }

  public long executedQuantity() {
    return executedQuantity;
  }

  public BigDecimal averagePrice() {
    return averagePrice;
  }
}
