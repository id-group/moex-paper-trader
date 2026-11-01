package ru.moex.papertrader.bks.api;

import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ru.moex.papertrader.bks.adapter.BksOrderMapper;
import ru.moex.papertrader.bks.contract.CancelOrderRequest;
import ru.moex.papertrader.bks.contract.CancelOrderResponse;
import ru.moex.papertrader.bks.contract.CreateOrderRequest;
import ru.moex.papertrader.bks.contract.ListResponseOrdersSearchResponse;
import ru.moex.papertrader.bks.contract.OrderResponse;
import ru.moex.papertrader.bks.contract.OrdersSearchRequest;
import ru.moex.papertrader.bks.contract.OrdersSearchResponse;
import ru.moex.papertrader.bks.contract.ShortOrderResponse;
import ru.moex.papertrader.core.domain.Order;
import ru.moex.papertrader.core.domain.OrderIdType;
import ru.moex.papertrader.core.domain.OrderSearchCriteria;
import ru.moex.papertrader.core.domain.PageResult;
import ru.moex.papertrader.infra.services.OrderService;

/** Заявки БКС: создание, отмена, статус и поиск (sync и async варианты). */
@RestController
@RequestMapping("${emulator.brokers.bks.base-path:/api/bks}/v1")
public class BksOrderController {

  private final OrderService orderService;
  private final BksOrderMapper orderMapper;

  public BksOrderController(OrderService orderService, BksOrderMapper orderMapper) {
    this.orderService = orderService;
    this.orderMapper = orderMapper;
  }

  @PostMapping("/orders")
  public ShortOrderResponse create(@RequestBody CreateOrderRequest request) {
    Order order = orderService.place(orderMapper.toNewOrder(request));
    return orderMapper.toShortOrderResponse(order);
  }

  @PostMapping("/orders/sync")
  public OrderResponse createSync(@RequestBody CreateOrderRequest request) {
    Order order = orderService.place(orderMapper.toNewOrder(request));
    return orderMapper.toOrderResponse(order);
  }

  @PostMapping("/orders/cancel")
  public CancelOrderResponse cancel(@RequestBody CancelOrderRequest request) {
    String requestClientOrderId =
        orderMapper.parseUuid(request.clientOrderId(), "clientOrderId").toString();
    OrderIdType idType = orderMapper.parseOrderIdType(request.orderIdType());
    orderService.cancel(idType, request.orderId());
    return orderMapper.toCancelOrderResponse(requestClientOrderId);
  }

  @PostMapping("/orders/cancel/sync")
  public OrderResponse cancelSync(@RequestBody CancelOrderRequest request) {
    orderMapper.parseUuid(request.clientOrderId(), "clientOrderId");
    OrderIdType idType = orderMapper.parseOrderIdType(request.orderIdType());
    Order order = orderService.cancel(idType, request.orderId());
    return orderMapper.toOrderResponse(order);
  }

  @GetMapping("/orders")
  public OrderResponse status(
      @RequestParam(name = "orderIdType", defaultValue = "1") String orderIdType,
      @RequestParam(name = "orderId") String orderId) {
    Order order = orderService.get(orderMapper.parseOrderIdType(orderIdType), orderId);
    return orderMapper.toOrderResponse(order);
  }

  @PostMapping("/orders/search")
  public ListResponseOrdersSearchResponse search(
      @RequestBody OrdersSearchRequest request,
      @RequestParam(name = "page", defaultValue = "0") int page,
      @RequestParam(name = "size", defaultValue = "50") int size,
      @RequestParam(name = "sort", required = false) List<String> sort) {
    BksPagination.validate(page, size);
    OrderSearchCriteria criteria = orderMapper.toCriteria(request);
    if (request.orderTypes() != null
        && !request.orderTypes().isEmpty()
        && criteria.orderTypes().isEmpty()) {
      return new ListResponseOrdersSearchResponse(List.of(), 0, 0);
    }
    PageResult<Order> result = orderService.search(criteria, page, size, sort);
    List<OrdersSearchResponse> records =
        result.items().stream().map(orderMapper::toSearchResponse).toList();
    return new ListResponseOrdersSearchResponse(
        records, result.totalRecords(), result.totalPages());
  }
}
