package ru.moex.papertrader.core.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Заявка эмулятора: клиентский UUID, биржевой идентификатор и статус жизненного цикла.
 *
 * <p>Состояние жизненного цикла меняется только операциями домена: {@link #accept} (новая заявка),
 * {@link #applyFill} (исполнение сделки), {@link #cancel} (отмена) и {@link #restore}
 * (восстановление из сохранённого состояния).
 */
public final class Order {

  /**
   * Масштаб средней цены исполнения; совпадает с {@code VirtualPortfolio.AVERAGE_PRICE_SCALE} и
   * схемой БД {@code NUMERIC(19,6)}.
   */
  private static final int AVERAGE_PRICE_SCALE = 6;

  private static final RoundingMode ROUNDING = RoundingMode.HALF_UP;

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

  /**
   * Восстанавливает заявку из сохранённого состояния (перезапуск сервиса, чтение из БД).
   *
   * <p>Это не бизнес-операция, а десериализация состояния: противоречивые поля означают ошибку
   * данных и сообщаются через {@link IllegalArgumentException}.
   *
   * @throws IllegalArgumentException если поля противоречат инвариантам жизненного цикла
   */
  public static Order restore(
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
      Instant createdAt,
      Instant updatedAt,
      OrderStatus status,
      long executedQuantity,
      BigDecimal averagePrice) {
    Objects.requireNonNull(clientOrderId, "clientOrderId");
    Objects.requireNonNull(orderId, "orderId");
    Objects.requireNonNull(instrument, "instrument");
    Objects.requireNonNull(side, "side");
    Objects.requireNonNull(orderType, "orderType");
    Objects.requireNonNull(timeInForce, "timeInForce");
    Objects.requireNonNull(createdAt, "createdAt");
    Objects.requireNonNull(updatedAt, "updatedAt");
    Objects.requireNonNull(status, "status");
    if (quantity <= 0) {
      throw new IllegalArgumentException("quantity заявки должен быть больше 0");
    }
    if (orderType == OrderType.LIMIT) {
      if (price == null || price.signum() <= 0) {
        throw new IllegalArgumentException("price лимитной заявки должен быть положительным");
      }
    } else if (price != null) {
      // В отличие от accept, price не отбрасывается молча: строка БД с ценой у рыночной заявки
      // указывает на ошибку данных.
      throw new IllegalArgumentException("price должен быть null для заявки типа " + orderType);
    }
    if (executedQuantity < 0 || executedQuantity > quantity) {
      throw new IllegalArgumentException(
          "executedQuantity должен быть в диапазоне 0.."
              + quantity
              + ", а равен "
              + executedQuantity);
    }
    if (executedQuantity == 0 && averagePrice != null) {
      throw new IllegalArgumentException("averagePrice должен быть null при executedQuantity = 0");
    }
    if (executedQuantity > 0 && averagePrice == null) {
      throw new IllegalArgumentException("averagePrice обязателен при executedQuantity > 0");
    }
    if (status == OrderStatus.FILLED) {
      if (executedQuantity != quantity) {
        throw new IllegalArgumentException("статус FILLED требует executedQuantity == quantity");
      }
    } else if (executedQuantity == quantity) {
      throw new IllegalArgumentException(
          "executedQuantity == quantity допустим только в статусе FILLED, а не " + status);
    }
    if (status == OrderStatus.PARTIALLY_FILLED
        && (executedQuantity <= 0 || executedQuantity >= quantity)) {
      throw new IllegalArgumentException(
          "статус PARTIALLY_FILLED требует 0 < executedQuantity < quantity");
    }
    if (status == OrderStatus.NEW && executedQuantity != 0) {
      throw new IllegalArgumentException("статус NEW требует executedQuantity = 0");
    }
    Order order =
        new Order(
            orderNum,
            clientOrderId,
            orderId,
            instrument,
            side,
            orderType,
            timeInForce,
            quantity,
            price,
            clientCode,
            createdAt);
    order.updatedAt = updatedAt;
    order.status = status;
    order.executedQuantity = executedQuantity;
    order.averagePrice = averagePrice;
    return order;
  }

  /**
   * Применяет сделку к заявке: увеличивает исполненный объём, пересчитывает среднюю цену и
   * переводит заявку в {@link OrderStatus#PARTIALLY_FILLED} или {@link OrderStatus#FILLED}.
   *
   * <p>Комиссия сделки в заявке не хранится: её учитывают портфель и таблица {@code fills}.
   *
   * @throws InvalidOrderException если заявка не активна или объём сделки превышает остаток
   */
  public void applyFill(Fill fill) {
    Objects.requireNonNull(fill, "fill");
    if (status != OrderStatus.NEW && status != OrderStatus.PARTIALLY_FILLED) {
      throw new InvalidOrderException(
          "Заявка "
              + orderId
              + " не может быть исполнена в статусе "
              + status.code()
              + " ("
              + status
              + ")");
    }
    long remaining = quantity - executedQuantity;
    if (fill.quantity() <= 0) {
      throw new InvalidOrderException("Объём сделки должен быть больше 0");
    }
    if (fill.quantity() > remaining) {
      throw new InvalidOrderException(
          "Объём сделки "
              + fill.quantity()
              + " больше остатка "
              + remaining
              + " по заявке "
              + orderId);
    }
    BigDecimal previousTurnover =
        executedQuantity == 0
            ? BigDecimal.ZERO
            : averagePrice.multiply(BigDecimal.valueOf(executedQuantity));
    BigDecimal turnover =
        previousTurnover.add(fill.price().multiply(BigDecimal.valueOf(fill.quantity())));
    executedQuantity += fill.quantity();
    averagePrice =
        turnover.divide(BigDecimal.valueOf(executedQuantity), AVERAGE_PRICE_SCALE, ROUNDING);
    status = executedQuantity == quantity ? OrderStatus.FILLED : OrderStatus.PARTIALLY_FILLED;
    updatedAt = fill.executedAt();
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
