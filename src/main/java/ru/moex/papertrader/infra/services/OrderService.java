package ru.moex.papertrader.infra.services;

import java.util.List;
import ru.moex.papertrader.core.domain.NewOrder;
import ru.moex.papertrader.core.domain.Order;
import ru.moex.papertrader.core.domain.OrderIdType;
import ru.moex.papertrader.core.domain.OrderSearchCriteria;
import ru.moex.papertrader.core.domain.PageResult;

/** Хранилище и поиск заявок (этап 2 — in-memory; этап 3 — JPA). */
public interface OrderService {

  Order place(NewOrder request);

  Order get(OrderIdType idType, String orderId);

  Order cancel(OrderIdType idType, String orderId);

  PageResult<Order> search(
      OrderSearchCriteria criteria, int page, int size, List<String> sortSpecs);

  List<Order> activeOrders();
}
