package ru.moex.papertrader.core.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Заявка, принятая от клиента, до валидации и присвоения биржевого идентификатора.
 *
 * <p>{@code expiryDate} обязателен для {@link TimeInForce#GOOD_TILL_DATE} и игнорируется для
 * остальных режимов. {@code strategyId} — необязательная метка роутера для отчётности; пустая
 * строка нормализуется в {@code null}, длина ограничена 64 символами (колонка {@code
 * orders.strategy_id}).
 */
public record NewOrder(
    UUID clientOrderId,
    String ticker,
    String classCode,
    Side side,
    OrderType orderType,
    TimeInForce timeInForce,
    long quantity,
    BigDecimal price,
    LocalDate expiryDate,
    String strategyId) {

  public NewOrder {
    strategyId = normalizeStrategy(strategyId);
  }

  /** Заявка без срока действия — для вызовов, которым GTD не нужен. */
  public NewOrder(
      UUID clientOrderId,
      String ticker,
      String classCode,
      Side side,
      OrderType orderType,
      TimeInForce timeInForce,
      long quantity,
      BigDecimal price) {
    this(clientOrderId, ticker, classCode, side, orderType, timeInForce, quantity, price, null);
  }

  /** Заявка со сроком действия, но без метки стратегии. */
  public NewOrder(
      UUID clientOrderId,
      String ticker,
      String classCode,
      Side side,
      OrderType orderType,
      TimeInForce timeInForce,
      long quantity,
      BigDecimal price,
      LocalDate expiryDate) {
    this(
        clientOrderId,
        ticker,
        classCode,
        side,
        orderType,
        timeInForce,
        quantity,
        price,
        expiryDate,
        null);
  }

  private static String normalizeStrategy(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    String trimmed = value.trim();
    if (trimmed.length() > 64) {
      throw new IllegalArgumentException("strategyId не может быть длиннее 64 символов");
    }
    return trimmed;
  }
}
