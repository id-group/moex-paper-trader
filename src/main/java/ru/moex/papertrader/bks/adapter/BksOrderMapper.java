package ru.moex.papertrader.bks.adapter;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;
import ru.moex.papertrader.bks.contract.CancelOrderResponse;
import ru.moex.papertrader.bks.contract.CreateOrderRequest;
import ru.moex.papertrader.bks.contract.OrderData;
import ru.moex.papertrader.bks.contract.OrderResponse;
import ru.moex.papertrader.bks.contract.OrdersSearchRequest;
import ru.moex.papertrader.bks.contract.OrdersSearchResponse;
import ru.moex.papertrader.bks.contract.ShortOrderResponse;
import ru.moex.papertrader.core.domain.NewOrder;
import ru.moex.papertrader.core.domain.Order;
import ru.moex.papertrader.core.domain.OrderIdType;
import ru.moex.papertrader.core.domain.OrderSearchCriteria;
import ru.moex.papertrader.core.domain.OrderStatus;
import ru.moex.papertrader.core.domain.OrderType;
import ru.moex.papertrader.core.domain.Side;
import ru.moex.papertrader.core.domain.TimeInForce;
import ru.moex.papertrader.infra.services.exception.InvalidRequestException;

/** Маппинг заявок между контрактом БКС и доменом core. */
@Component
public class BksOrderMapper {

  private static final ZoneId MOSCOW = ZoneId.of("Europe/Moscow");
  private static final ZoneOffset UTC = ZoneOffset.UTC;
  private static final String MESSAGE_TYPE = "ORDER_STATUS";
  private static final String SECURITY_EXCHANGE = "MOEX";

  public NewOrder toNewOrder(CreateOrderRequest request) {
    if (request == null) {
      throw new InvalidRequestException("Тело запроса обязательно");
    }
    UUID clientOrderId = requireUuid(request.clientOrderId(), "clientOrderId");
    Side side =
        Side.fromCode(request.side())
            .orElseThrow(() -> new InvalidRequestException("side должен быть \"1\" или \"2\""));
    OrderType orderType =
        OrderType.fromCode(request.orderType())
            .orElseThrow(() -> new InvalidRequestException("orderType должен быть \"1\"..\"5\""));
    TimeInForce timeInForce =
        request.timeInForce() == null || request.timeInForce().isBlank()
            ? TimeInForce.DAY
            : TimeInForce.fromCode(request.timeInForce())
                .orElseThrow(
                    () -> new InvalidRequestException("timeInForce должен быть \"1\"..\"3\""));
    if (request.orderQuantity() == null) {
      throw new InvalidRequestException("orderQuantity обязателен");
    }
    if (request.ticker() == null || request.ticker().isBlank()) {
      throw new InvalidRequestException("ticker обязателен");
    }
    if (request.classCode() == null || request.classCode().isBlank()) {
      throw new InvalidRequestException("classCode обязателен");
    }
    return new NewOrder(
        clientOrderId,
        request.ticker(),
        request.classCode(),
        side,
        orderType,
        timeInForce,
        request.orderQuantity(),
        request.price());
  }

  public UUID parseUuid(String value, String field) {
    return requireUuid(value, field);
  }

  public OrderIdType parseOrderIdType(String code) {
    if (code == null || code.isBlank()) {
      return OrderIdType.CLIENT;
    }
    return OrderIdType.fromCode(code)
        .orElseThrow(() -> new InvalidRequestException("orderIdType должен быть \"1\" или \"2\""));
  }

  public OrderResponse toOrderResponse(Order order) {
    return new OrderResponse(order.clientOrderId().toString(), null, List.of(toOrderData(order)));
  }

  public ShortOrderResponse toShortOrderResponse(Order order) {
    return new ShortOrderResponse(order.clientOrderId().toString(), "OK");
  }

  public CancelOrderResponse toCancelOrderResponse(String requestClientOrderId) {
    return new CancelOrderResponse(requestClientOrderId, "OK");
  }

  public OrdersSearchResponse toSearchResponse(Order order) {
    BigDecimal quantity = BigDecimal.valueOf(order.quantity());
    BigDecimal remained = BigDecimal.valueOf(order.quantity() - order.executedQuantity());
    BigDecimal lotSize = order.instrument().lotSize();
    return new OrdersSearchResponse(
        order.orderNum(),
        order.orderId(),
        order.clientCode(),
        null,
        BigDecimal.ZERO,
        offset(order.createdAt()),
        order.createdAt().atZone(MOSCOW).toLocalDate(),
        offset(order.updatedAt()),
        order.instrument().ticker(),
        order.instrument().classCode(),
        null,
        null,
        order.price(),
        order.instrument().currency(),
        quantity,
        remained,
        BigDecimal.valueOf(order.executedQuantity()),
        null,
        order.averagePrice(),
        null,
        null,
        searchOrderStatus(order.status()),
        searchOrderType(order.orderType()),
        Integer.parseInt(order.side().code()),
        lots(quantity, lotSize),
        lots(remained, lotSize),
        lots(BigDecimal.valueOf(order.executedQuantity()), lotSize),
        null,
        null,
        null,
        null,
        null,
        null,
        null);
  }

  public OrderSearchCriteria toCriteria(OrdersSearchRequest request) {
    if (request == null) {
      return new OrderSearchCriteria(null, null, null, null, null, null, null, null);
    }
    return new OrderSearchCriteria(
        request.startDateTime() == null ? null : request.startDateTime().toInstant(),
        request.endDateTime() == null ? null : request.endDateTime().toInstant(),
        toSide(request.side()),
        toStatuses(request.orderStatus()),
        toOrderTypes(request.orderTypes()),
        request.orderNums() == null ? Set.of() : new LinkedHashSet<>(request.orderNums()),
        request.tickers() == null ? Set.of() : new LinkedHashSet<>(request.tickers()),
        request.classCodes() == null ? Set.of() : new LinkedHashSet<>(request.classCodes()));
  }

  private OrderData toOrderData(Order order) {
    BigDecimal quantity = BigDecimal.valueOf(order.quantity());
    BigDecimal remained = BigDecimal.valueOf(order.quantity() - order.executedQuantity());
    return new OrderData(
        MESSAGE_TYPE,
        order.status().code(),
        executionType(order.status()),
        quantity,
        BigDecimal.valueOf(order.executedQuantity()),
        BigDecimal.ZERO,
        remained,
        order.instrument().ticker(),
        order.instrument().classCode(),
        order.side().code(),
        order.orderType().code(),
        order.averagePrice(),
        order.orderId(),
        null,
        order.price(),
        order.instrument().currency(),
        order.clientCode(),
        offset(order.updatedAt()),
        order.createdAt().atZone(MOSCOW).toLocalDate(),
        String.valueOf(order.orderNum()),
        null,
        null,
        null,
        SECURITY_EXCHANGE,
        null,
        Integer.parseInt(order.timeInForce().code()),
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null);
  }

  private static Side toSide(Integer code) {
    if (code == null) {
      return null;
    }
    return switch (code) {
      case 1 -> Side.BUY;
      case 2 -> Side.SELL;
      default -> throw new InvalidRequestException("side должен быть 1 или 2");
    };
  }

  private static Set<OrderStatus> toStatuses(List<Integer> codes) {
    if (codes == null || codes.isEmpty()) {
      return Set.of();
    }
    Set<OrderStatus> statuses = new LinkedHashSet<>();
    for (Integer code : codes) {
      if (code == null) {
        throw new InvalidRequestException("orderStatus не может содержать null");
      }
      switch (code) {
        case 1 -> statuses.add(OrderStatus.CANCELED);
        case 2 -> statuses.add(OrderStatus.FILLED);
        case 3 -> statuses.addAll(activeStatuses());
        default -> throw new InvalidRequestException("orderStatus должен быть 1..3");
      }
    }
    return statuses;
  }

  private static Set<OrderStatus> activeStatuses() {
    Set<OrderStatus> statuses = new LinkedHashSet<>();
    for (OrderStatus status : OrderStatus.values()) {
      if (status.isActive() || status == OrderStatus.REPLACED) {
        statuses.add(status);
      }
    }
    return statuses;
  }

  private static Set<OrderType> toOrderTypes(List<Integer> codes) {
    if (codes == null || codes.isEmpty()) {
      return Set.of();
    }
    Set<OrderType> types = new LinkedHashSet<>();
    for (Integer code : codes) {
      if (code == null) {
        throw new InvalidRequestException("orderTypes не может содержать null");
      }
      switch (code) {
        case 1 -> types.add(OrderType.MARKET);
        case 2 -> types.add(OrderType.LIMIT);
        default -> {
          // Неподдерживаемые типы отфильтровываются в контроллере (пустая страница).
        }
      }
    }
    return types;
  }

  private static int searchOrderStatus(OrderStatus status) {
    return switch (status) {
      case FILLED -> 2;
      case CANCELED, REJECTED -> 1;
      default -> 3;
    };
  }

  private static Integer searchOrderType(OrderType type) {
    return switch (type) {
      case MARKET -> 1;
      case LIMIT -> 2;
      default -> null;
    };
  }

  private static String executionType(OrderStatus status) {
    return switch (status) {
      case NEW -> "0";
      case PARTIALLY_FILLED -> "1";
      case FILLED -> "2";
      case CANCELED -> "4";
      case REPLACED -> "5";
      case CANCELING -> "6";
      case REJECTED -> "8";
      case REPLACING -> "9";
      case PENDING_NEW -> "10";
    };
  }

  private static BigDecimal lots(BigDecimal quantity, BigDecimal lotSize) {
    return quantity.divide(lotSize, 0, RoundingMode.DOWN);
  }

  private static OffsetDateTime offset(Instant instant) {
    return OffsetDateTime.ofInstant(instant, UTC);
  }

  private static UUID requireUuid(String value, String field) {
    if (value == null || value.isBlank()) {
      throw new InvalidRequestException(field + " обязателен");
    }
    try {
      return UUID.fromString(value);
    } catch (IllegalArgumentException exception) {
      throw new InvalidRequestException(field + " должен быть UUID", exception);
    }
  }
}
