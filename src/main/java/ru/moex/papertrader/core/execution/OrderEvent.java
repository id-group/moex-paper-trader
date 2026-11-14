package ru.moex.papertrader.core.execution;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import ru.moex.papertrader.core.domain.OrderStatus;

/**
 * Событие жизненного цикла заявки.
 *
 * <p>{@code previousStatus} для {@link OrderEventType#CREATED} равен {@code null}; {@code price}
 * заполняется для событий исполнения, {@code reason} — для отмены, отклонения и истечения срока.
 *
 * <p>Семантика {@code quantity} по типам (договорённость 2026-11-13, docs/order-events.md):
 *
 * <ul>
 *   <li>{@code CREATED} — объём заявки;
 *   <li>{@code PARTIALLY_FILLED} — размер этого фишла;
 *   <li>терминальные ({@code FILLED}, {@code CANCELED}, {@code REJECTED}, {@code EXPIRED}) —
 *       накопленный {@code executed_quantity} заявки (0, если ничего не исполнено).
 * </ul>
 */
public record OrderEvent(
    String orderId,
    OrderEventType type,
    OrderStatus previousStatus,
    OrderStatus status,
    Long quantity,
    BigDecimal price,
    String reason,
    Instant occurredAt) {

  public OrderEvent {
    if (orderId == null || orderId.isBlank()) {
      throw new IllegalArgumentException("orderId обязателен");
    }
    Objects.requireNonNull(type, "type");
    Objects.requireNonNull(status, "status");
    Objects.requireNonNull(occurredAt, "occurredAt");
    if (quantity != null && quantity < 0) {
      throw new IllegalArgumentException("quantity не может быть отрицательным");
    }
    if (price != null && price.signum() <= 0) {
      throw new IllegalArgumentException("price должен быть положительным");
    }
  }
}
