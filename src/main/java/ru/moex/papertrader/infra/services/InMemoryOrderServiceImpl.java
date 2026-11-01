package ru.moex.papertrader.infra.services;

import java.time.Clock;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.stereotype.Service;
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
 * In-memory реализация хранилища заявок (временное решение, этап 3 заменит на JPA/PostgreSQL).
 *
 * <p>Биржевой идентификатор формируется по контракту БКС: {@code ГГММДД-КодКласса-Номер}.
 */
@Service
public class InMemoryOrderServiceImpl implements OrderService {

  private static final ZoneId MOSCOW = ZoneId.of("Europe/Moscow");
  private static final DateTimeFormatter ORDER_ID_DATE =
      DateTimeFormatter.ofPattern("yyyyMMdd").withZone(MOSCOW);
  private static final long ORDER_ID_SEQUENCE_BASE = 796_285_400_000L;
  private static final List<String> DEFAULT_SORT = List.of("orderDateTime,asc");

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
      String exchangeOrderId =
          ORDER_ID_DATE.format(clock.instant())
              + "-"
              + instrument.classCode()
              + "-"
              + (ORDER_ID_SEQUENCE_BASE + orderNum);
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
    filtered.sort(comparator(sortSpecs));
    return paginate(filtered, page, size);
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

  private Comparator<Order> comparator(List<String> sortSpecs) {
    List<String> specs = sortSpecs == null || sortSpecs.isEmpty() ? DEFAULT_SORT : sortSpecs;
    Comparator<Order> comparator = null;
    for (String spec : specs) {
      String[] parts = spec.split(",", -1);
      if (parts.length != 2) {
        throw new InvalidRequestException("Некорректная сортировка: " + spec);
      }
      Comparator<Order> current = fieldComparator(parts[0].trim());
      String direction = parts[1].trim();
      if ("desc".equalsIgnoreCase(direction)) {
        current = current.reversed();
      } else if (!"asc".equalsIgnoreCase(direction)) {
        throw new InvalidRequestException("Некорректное направление сортировки: " + direction);
      }
      comparator = comparator == null ? current : comparator.thenComparing(current);
    }
    return comparator == null ? Comparator.comparingLong(Order::orderNum) : comparator;
  }

  private Comparator<Order> fieldComparator(String field) {
    return switch (field) {
      case "orderDateTime" -> Comparator.comparing(Order::createdAt);
      case "updateDateTime" -> Comparator.comparing(Order::updatedAt);
      case "ticker" -> Comparator.comparing(order -> order.instrument().ticker());
      case "classCode" -> Comparator.comparing(order -> order.instrument().classCode());
      case "orderType" -> Comparator.comparing(order -> order.orderType().code());
      case "side" -> Comparator.comparing(order -> order.side().code());
      default -> throw new InvalidRequestException("Неподдерживаемое поле сортировки: " + field);
    };
  }

  private PageResult<Order> paginate(List<Order> orders, int page, int size) {
    long totalRecords = orders.size();
    int totalPages = (int) ((totalRecords + size - 1) / size);
    long offset = (long) page * size;
    if (offset >= totalRecords) {
      return new PageResult<>(List.of(), totalRecords, totalPages);
    }
    int fromIndex = (int) offset;
    int toIndex = (int) Math.min(offset + size, totalRecords);
    return new PageResult<>(orders.subList(fromIndex, toIndex), totalRecords, totalPages);
  }
}
