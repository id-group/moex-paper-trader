package ru.moex.papertrader.core.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
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

  /** Москва: срок GTD задаётся календарной датой торгового дня. */
  private static final ZoneId MOSCOW = ZoneId.of("Europe/Moscow");

  private final long orderNum;
  private final UUID clientOrderId;
  private final String orderId;
  private final Instrument instrument;
  private final Side side;
  private final OrderType orderType;
  private final TimeInForce timeInForce;
  private final long quantity;
  private final BigDecimal price;
  private final LocalDate expiryDate;
  private final String clientCode;
  private final String strategyId;
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
      LocalDate expiryDate,
      String clientCode,
      String strategyId,
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
    this.expiryDate = expiryDate;
    this.clientCode = clientCode;
    this.strategyId = strategyId;
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
      // TODO(этап 5): orderType 3–5 (алгоритмическая GTD, iceberg, условная) — заглушка по
      //  брифу; реализация потребует отдельной модели исполнения.
      throw new InvalidOrderException(
          "orderType "
              + request.orderType().code()
              + " ("
              + request.orderType()
              + ") не реализован: NOT_IMPLEMENTED");
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
    LocalDate expiryDate = request.expiryDate();
    if (request.timeInForce() == TimeInForce.GOOD_TILL_DATE) {
      if (expiryDate == null) {
        throw new InvalidOrderException("expiryDate обязателен для timeInForce=GTD");
      }
      if (expiryDate.isBefore(now.atZone(MOSCOW).toLocalDate())) {
        throw new InvalidOrderException("expiryDate не может быть в прошлом");
      }
    } else {
      // Срок действия относится только к GTD: для DAY/FOK поле контракта игнорируется.
      expiryDate = null;
    }
    // Кратность лоту проверяется последней: сначала — структурные ошибки контракта
    // (orderType, timeInForce, price, expiryDate), затем уже семантика объёма.
    requireWholeLots(request.quantity(), instrument);
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
        expiryDate,
        clientCode,
        request.strategyId(),
        now);
  }

  /**
   * Требует, чтобы объём заявки состоял из целых лотов: биржа и брокер не выставляют на рынок
   * дробные лоты, а {@code orderQuantity} контракта БКС — количество в штуках.
   *
   * <p>{@code lotSize <= 1} (фьючерсы, инструменты с лотом из одной бумаги) проверку проходят
   * автоматически.
   */
  private static void requireWholeLots(long quantity, Instrument instrument) {
    BigDecimal lotSize = instrument.lotSize();
    if (lotSize == null || lotSize.compareTo(BigDecimal.ONE) <= 0) {
      return;
    }
    BigDecimal lots = BigDecimal.valueOf(quantity);
    if (lots.remainder(lotSize).signum() != 0) {
      throw new InvalidOrderException(
          "orderQuantity "
              + quantity
              + " должен быть кратен размеру лота "
              + lotSize.toPlainString()
              + " по инструменту "
              + instrument.ticker());
    }
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
      BigDecimal averagePrice,
      LocalDate expiryDate,
      String strategyId) {
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
    if (timeInForce == TimeInForce.GOOD_TILL_DATE) {
      if (expiryDate == null) {
        throw new IllegalArgumentException("expiryDate обязателен для timeInForce=GTD");
      }
    } else if (expiryDate != null) {
      throw new IllegalArgumentException(
          "expiryDate допустим только для timeInForce=GTD, а не " + timeInForce);
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
            expiryDate,
            clientCode,
            strategyId,
            createdAt);
    order.updatedAt = updatedAt;
    order.status = status;
    order.executedQuantity = executedQuantity;
    order.averagePrice = averagePrice;
    return order;
  }

  /** Восстановление без метки стратегии — для сохранённых заявок до этапа 6. */
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
      BigDecimal averagePrice,
      LocalDate expiryDate) {
    return restore(
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
        createdAt,
        updatedAt,
        status,
        executedQuantity,
        averagePrice,
        expiryDate,
        null);
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

  /**
   * Отклоняет заявку, которая не может быть исполнена (FOK без полного объёма, ликвидность,
   * недостаток средств). Отклонение фиксируется в журнале {@code order_events}.
   */
  public void reject(Instant at) {
    if (status != OrderStatus.NEW && status != OrderStatus.PENDING_NEW) {
      throw new InvalidOrderException(
          "Заявка " + orderId + " не может быть отклонена в статусе " + status.code());
    }
    status = OrderStatus.REJECTED;
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

  /** Срок действия GTD; {@code null} для DAY и FOK. */
  public LocalDate expiryDate() {
    return expiryDate;
  }

  public String clientCode() {
    return clientCode;
  }

  /** Метка стратегии роутера для отчётности; {@code null} — заявка без метки. */
  public String strategyId() {
    return strategyId;
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
