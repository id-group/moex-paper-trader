package ru.moex.papertrader.infra.services;

import java.time.Clock;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import ru.moex.papertrader.core.domain.Instrument;
import ru.moex.papertrader.core.domain.NewOrder;
import ru.moex.papertrader.core.domain.Order;
import ru.moex.papertrader.core.domain.OrderIdType;
import ru.moex.papertrader.core.domain.OrderSearchCriteria;
import ru.moex.papertrader.core.domain.PageResult;
import ru.moex.papertrader.core.marketdata.MarketDataProvider;
import ru.moex.papertrader.infra.services.exception.ConflictException;
import ru.moex.papertrader.infra.services.exception.InvalidRequestException;
import ru.moex.papertrader.infra.services.exception.ResourceNotFoundException;

/**
 * Эталонная in-memory реализация хранилища заявок этапа 2.
 *
 * <p>В приложении не используется: бин {@link OrderService} предоставляет {@link
 * JpaOrderServiceImpl}. Класс сохранён как образец поведения и предмет unit-тестов.
 *
 * <p>Биржевой идентификатор формируется по контракту БКС: {@code ГГММДД-КодКласса-Номер}.
 */
public class InMemoryOrderServiceImpl implements OrderService {

  private final Map<UUID, Order> byClientOrderId = new ConcurrentHashMap<>();
  private final Map<String, Order> byExchangeOrderId = new ConcurrentHashMap<>();
  private final AtomicLong orderSequence = new AtomicLong();
  private final Object mutationLock = new Object();

  private final Clock clock;
  private final MarketDataProvider marketDataProvider;
  private final PortfolioProperties portfolioProperties;

  public InMemoryOrderServiceImpl(
      Clock clock, MarketDataProvider marketDataProvider, PortfolioProperties portfolioProperties) {
    this.clock = clock;
    this.marketDataProvider = marketDataProvider;
    this.portfolioProperties = portfolioProperties;
  }

  @Override
  public Order place(NewOrder request) {
    if (request.clientOrderId() == null) {
      throw new InvalidRequestException("clientOrderId обязателен");
    }
    Instrument instrument =
        marketDataProvider
            .instrument(request.ticker(), request.classCode())
            .orElseThrow(
                () ->
                    new ResourceNotFoundException(
                        "Инструмент "
                            + request.ticker()
                            + "/"
                            + request.classCode()
                            + " не найден"));
    synchronized (mutationLock) {
      if (byClientOrderId.containsKey(request.clientOrderId())) {
        throw new ConflictException(
            "Заявка с clientOrderId " + request.clientOrderId() + " уже существует");
      }
      long orderNum = orderSequence.incrementAndGet();
      String exchangeOrderId = OrderServiceSupport.exchangeOrderId(clock, instrument, orderNum);
      Order order =
          Order.accept(
              request,
              instrument,
              exchangeOrderId,
              orderNum,
              portfolioProperties.account(),
              clock.instant());
      byClientOrderId.put(order.clientOrderId(), order);
      byExchangeOrderId.put(order.orderId(), order);
      return order;
    }
  }

  @Override
  public Order get(OrderIdType idType, String orderId) {
    return resolve(idType, orderId);
  }

  @Override
  public Order cancel(OrderIdType idType, String orderId) {
    synchronized (mutationLock) {
      Order order = resolve(idType, orderId);
      order.cancel(clock.instant());
      return order;
    }
  }

  @Override
  public PageResult<Order> search(
      OrderSearchCriteria criteria, int page, int size, List<String> sortSpecs) {
    List<Order> filtered = new ArrayList<>();
    for (Order order : byClientOrderId.values()) {
      if (criteria.matches(order)) {
        filtered.add(order);
      }
    }
    filtered.sort(OrderServiceSupport.comparator(sortSpecs));
    return OrderServiceSupport.paginate(filtered, page, size);
  }

  @Override
  public List<Order> activeOrders() {
    return byClientOrderId.values().stream()
        .filter(order -> order.status().isActive())
        .sorted(Comparator.comparingLong(Order::orderNum))
        .toList();
  }

  private Order resolve(OrderIdType idType, String orderId) {
    if (orderId == null || orderId.isBlank()) {
      throw new InvalidRequestException("orderId обязателен");
    }
    Order order =
        idType == OrderIdType.CLIENT
            ? byClientOrderId.get(parseClientOrderId(orderId))
            : byExchangeOrderId.get(orderId);
    if (order == null) {
      throw new ResourceNotFoundException("Заявка " + orderId + " не найдена");
    }
    return order;
  }

  private UUID parseClientOrderId(String value) {
    try {
      return UUID.fromString(value);
    } catch (IllegalArgumentException exception) {
      throw new InvalidRequestException("orderId должен быть UUID при orderIdType=1", exception);
    }
  }
}
