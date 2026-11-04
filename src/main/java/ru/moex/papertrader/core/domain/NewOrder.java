package ru.moex.papertrader.core.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Заявка, принятая от клиента, до валидации и присвоения биржевого идентификатора.
 *
 * <p>{@code expiryDate} обязателен для {@link TimeInForce#GOOD_TILL_DATE} и игнорируется для
 * остальных режимов.
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
    LocalDate expiryDate) {

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
}
