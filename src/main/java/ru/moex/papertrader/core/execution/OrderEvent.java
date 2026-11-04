package ru.moex.papertrader.core.execution;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import ru.moex.papertrader.core.domain.OrderStatus;

/**
 * Событие жизненного цикла заявки.
 *
 * <p>{@code previousStatus} для {@link OrderEventType#CREATED} равен {@code null}; {@code quantity}
 * и {@code price} заполняются для событий исполнения, {@code reason} — для отмены, отклонения и
 * истечения срока.
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
    if (quantity != null && quantity <= 0) {
      throw new IllegalArgumentException("quantity должен быть больше 0");
    }
    if (price != null && price.signum() <= 0) {
      throw new IllegalArgumentException("price должен быть положительным");
    }
  }
}
