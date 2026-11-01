package ru.moex.papertrader.core.domain;

import java.time.Instant;
import java.util.Set;

/** Фильтр поиска заявок (аналог {@code OrdersSearchRequest} контракта). */
public record OrderSearchCriteria(
    Instant from,
    Instant to,
    Side side,
    Set<OrderStatus> statuses,
    Set<OrderType> orderTypes,
    Set<Long> orderNums,
    Set<String> tickers,
    Set<String> classCodes) {

  public OrderSearchCriteria {
    statuses = statuses == null ? Set.of() : Set.copyOf(statuses);
    orderTypes = orderTypes == null ? Set.of() : Set.copyOf(orderTypes);
    orderNums = orderNums == null ? Set.of() : Set.copyOf(orderNums);
    tickers = tickers == null ? Set.of() : Set.copyOf(tickers);
    classCodes = classCodes == null ? Set.of() : Set.copyOf(classCodes);
  }

  public boolean matches(Order order) {
    if (from != null && order.createdAt().isBefore(from)) {
      return false;
    }
    if (to != null && order.createdAt().isAfter(to)) {
      return false;
    }
    if (side != null && order.side() != side) {
      return false;
    }
    if (!statuses.isEmpty() && !statuses.contains(order.status())) {
      return false;
    }
    if (!orderTypes.isEmpty() && !orderTypes.contains(order.orderType())) {
      return false;
    }
    if (!orderNums.isEmpty() && !orderNums.contains(order.orderNum())) {
      return false;
    }
    if (!tickers.isEmpty() && !tickers.contains(order.instrument().ticker())) {
      return false;
    }
    return classCodes.isEmpty() || classCodes.contains(order.instrument().classCode());
  }
}
