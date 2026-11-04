package ru.moex.papertrader.infra.mapper;

import java.util.Objects;
import org.springframework.stereotype.Component;
import ru.moex.papertrader.core.execution.OrderEvent;
import ru.moex.papertrader.infra.model.OrderEventEntity;

/** Маппинг события жизненного цикла заявки в строку {@code order_events}. */
@Component
public class OrderEventMapper {

  /**
   * Длина колонки {@code order_events.reason}: длинные тексты ошибок обрезаются, а не ломают
   * запись.
   */
  private static final int REASON_MAX_LENGTH = 512;

  public OrderEventEntity toEntity(OrderEvent event) {
    Objects.requireNonNull(event, "event");
    return new OrderEventEntity(
        event.orderId(),
        event.type(),
        event.previousStatus(),
        event.status(),
        event.quantity(),
        MappingScales.price(event.price()),
        reason(event.reason()),
        event.occurredAt());
  }

  private static String reason(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    return value.length() <= REASON_MAX_LENGTH ? value : value.substring(0, REASON_MAX_LENGTH);
  }
}
