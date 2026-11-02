package ru.moex.papertrader.infra.mapper;

import java.math.BigDecimal;
import java.util.Objects;
import org.springframework.stereotype.Component;
import ru.moex.papertrader.core.domain.Instrument;
import ru.moex.papertrader.core.domain.Order;
import ru.moex.papertrader.infra.model.OrderEntity;

/**
 * Маппинг заявки: сущность {@code orders} ↔ доменный {@link Order}.
 *
 * <p>Ручной маппер, а не MapStruct: у {@code Order} приватный конструктор, а состояние жизненного
 * цикла задаётся только доменными операциями. Счёт и инструмент заявки — ссылки слоя БД, передаются
 * параметрами.
 *
 * <p>Домен восстанавливает заявку в любом статусе через {@code Order.restore}; противоречия в
 * строке БД сообщаются как {@link IllegalArgumentException}, а не теряются молча.
 */
@Component
public class OrderMapper {

  public OrderEntity toEntity(Order order, String accountId, Long instrumentId) {
    Objects.requireNonNull(order, "order");
    requireText(accountId, "accountId");
    Objects.requireNonNull(instrumentId, "instrumentId");
    return new OrderEntity(
        order.orderId(),
        order.orderNum(),
        order.clientOrderId(),
        accountId,
        instrumentId,
        order.side(),
        order.orderType(),
        order.timeInForce(),
        order.quantity(),
        MappingScales.price(order.price()),
        order.clientCode(),
        order.createdAt(),
        order.updatedAt(),
        order.status(),
        order.executedQuantity(),
        MappingScales.price(order.averagePrice()));
  }

  public Order toDomain(OrderEntity entity, Instrument instrument) {
    Objects.requireNonNull(entity, "entity");
    Objects.requireNonNull(instrument, "instrument");
    return Order.restore(
        entity.getOrderNum(),
        entity.getClientOrderId(),
        entity.getOrderId(),
        instrument,
        entity.getSide(),
        entity.getOrderType(),
        entity.getTimeInForce(),
        entity.getQuantity(),
        normalizePrice(entity.getPrice()),
        entity.getClientCode(),
        entity.getCreatedAt(),
        entity.getUpdatedAt(),
        entity.getStatus(),
        entity.getExecutedQuantity(),
        normalizePrice(entity.getAveragePrice()));
  }

  /**
   * Убирает хвостовые нули масштаба схемы ({@code NUMERIC(19,6)}) при чтении из БД.
   *
   * <p>Иначе цена {@code 285.500000} уходила бы в домен и в JSON БКС как {@code "285.500000"}
   * вместо {@code "285.5"}. Нули и {@code null} остаются как есть; запись по-прежнему приводит
   * значение к масштабу схемы через {@link MappingScales}.
   */
  private static BigDecimal normalizePrice(BigDecimal value) {
    if (value == null || value.signum() == 0) {
      return value;
    }
    BigDecimal stripped = value.stripTrailingZeros();
    return stripped.scale() < 0 ? stripped.setScale(0) : stripped;
  }

  private static void requireText(String value, String name) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(name + " не может быть пустым");
    }
  }
}
