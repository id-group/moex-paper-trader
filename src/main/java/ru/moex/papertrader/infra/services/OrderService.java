package ru.moex.papertrader.infra.services;

import java.util.List;
import ru.moex.papertrader.core.domain.NewOrder;
import ru.moex.papertrader.core.domain.Order;
import ru.moex.papertrader.core.domain.OrderIdType;
import ru.moex.papertrader.core.domain.OrderSearchCriteria;
import ru.moex.papertrader.core.domain.PageResult;

/** Порт сервиса заявок (этап 2 — in-memory; этап 3 — JPA). */
public interface OrderService {

  Order place(NewOrder request);

  Order get(OrderIdType idType, String orderId);

  Order cancel(OrderIdType idType, String orderId);

  /**
   * Отменяет активную заявку с указанием причины.
   *
   * @param reason причина отмены для журнала {@code order_events}; {@code null} — без причины
   */
  Order cancel(OrderIdType idType, String orderId, String reason);

  /**
   * Отклоняет заявку, которая не может быть исполнена (FOK без полного объёма, недостаток средств,
   * ошибка исполнения).
   */
  Order reject(OrderIdType idType, String orderId, String reason);

  /**
   * Снимает активную заявку по истечении срока действия (DAY в конце сессии, GTD по {@code
   * expiryDate}). Статус заявки — {@code CANCELED}, тип события — {@code EXPIRED}.
   */
  Order expire(OrderIdType idType, String orderId);

  PageResult<Order> search(
      OrderSearchCriteria criteria, int page, int size, List<String> sortSpecs);

  List<Order> activeOrders();
}
