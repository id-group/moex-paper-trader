package ru.moex.papertrader.infra.services;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import ru.moex.papertrader.core.domain.Order;
import ru.moex.papertrader.core.domain.OrderIdType;
import ru.moex.papertrader.core.domain.OrderStatus;
import ru.moex.papertrader.core.domain.OrderType;
import ru.moex.papertrader.core.domain.TimeInForce;
import ru.moex.papertrader.core.execution.MatchResult;
import ru.moex.papertrader.core.execution.MatchingEngine;
import ru.moex.papertrader.core.marketdata.Clock;
import ru.moex.papertrader.core.marketdata.MarketDataProvider;
import ru.moex.papertrader.core.marketdata.OrderBookSnapshot;
import ru.moex.papertrader.core.marketdata.TradingSession;

/**
 * Матчинг заявок по стакану и обслуживание жизненного цикла.
 *
 * <p>Исполнение не оборачивается в общую транзакцию: сделки прохода применяются атомарно сервисом
 * {@link FillService#fillAll}, а отказ превращается в {@code REJECTED} отдельной транзакцией. Так
 * сбой заявки не откатывает уже исполненные части и не блокирует остальные заявки прохода.
 */
@Service
public class ExecutionServiceImpl implements ExecutionService {

  private static final Logger log = LoggerFactory.getLogger(ExecutionServiceImpl.class);
  private static final ZoneId MOSCOW = ZoneId.of("Europe/Moscow");

  private final OrderService orderService;
  private final FillService fillService;
  private final MarketDataProvider marketDataProvider;
  private final MatchingEngine matchingEngine;
  private final Clock clock;
  private final ExecutionProperties properties;

  public ExecutionServiceImpl(
      OrderService orderService,
      FillService fillService,
      MarketDataProvider marketDataProvider,
      MatchingEngine matchingEngine,
      Clock clock,
      ExecutionProperties properties) {
    this.orderService = orderService;
    this.fillService = fillService;
    this.marketDataProvider = marketDataProvider;
    this.matchingEngine = matchingEngine;
    this.clock = clock;
    this.properties = properties;
  }

  @Override
  public Order execute(OrderIdType idType, String orderId) {
    Order order = orderService.get(idType, orderId);
    if (!isExecutable(order.status())) {
      return order;
    }

    TradingSession session = marketDataProvider.tradingSession(clock.instant());
    if (!session.open()) {
      // FOK и маркет исполняются немедленно; вне сессии немедленное исполнение невозможно.
      if (order.timeInForce() == TimeInForce.FILL_OR_KILL
          || order.orderType() == OrderType.MARKET) {
        return reject(order, "Сессия закрыта: заявка не может быть исполнена немедленно");
      }
      return order;
    }

    OrderBookSnapshot book =
        marketDataProvider.orderBook(order.instrument(), properties.orderBookDepth());
    MatchResult result =
        matchingEngine.match(order, book, marketDataProvider.adv(order.instrument()));
    if (result.rejected()) {
      return reject(order, result.rejectionReason().orElse("Заявка отклонена матчингом"));
    }
    if (result.fills().isEmpty()) {
      return order;
    }

    try {
      fillService.fillAll(OrderIdType.EXCHANGE, order.orderId(), result.fills());
    } catch (RuntimeException exception) {
      log.warn("Исполнение заявки {} не удалось: {}", order.orderId(), exception.getMessage());
      return reject(order, failureReason(exception));
    }

    Order executed = orderService.get(OrderIdType.EXCHANGE, order.orderId());
    if (result.remainderAction() == MatchResult.RemainderAction.CANCEL
        && executed.status().isActive()) {
      return orderService.cancel(
          OrderIdType.EXCHANGE,
          order.orderId(),
          "Маркет-остаток не исполнен: недостаточный объём в стакане");
    }
    return executed;
  }

  @Override
  public int tick() {
    Instant now = clock.instant();
    TradingSession session = marketDataProvider.tradingSession(now);
    LocalDate today = now.atZone(MOSCOW).toLocalDate();
    List<Order> activeOrders = orderService.activeOrders();
    int processed = 0;
    for (Order order : activeOrders) {
      if (!isExecutable(order.status())) {
        continue;
      }
      try {
        if (isExpired(order, today, session)) {
          orderService.expire(OrderIdType.EXCHANGE, order.orderId());
          processed++;
          continue;
        }
        if (session.open()) {
          execute(OrderIdType.EXCHANGE, order.orderId());
          processed++;
        }
      } catch (RuntimeException exception) {
        log.warn(
            "Проход исполнения: заявка {} не обработана: {}",
            order.orderId(),
            exception.getMessage());
      }
    }
    return processed;
  }

  /** Снятие по сроку: DAY — в конце сессии, GTD — после expiryDate (или на закрытии этого дня). */
  private static boolean isExpired(Order order, LocalDate today, TradingSession session) {
    if (!isExecutable(order.status())) {
      return false;
    }
    return switch (order.timeInForce()) {
      case DAY -> !session.open();
      case GOOD_TILL_DATE -> {
        LocalDate expiry = order.expiryDate();
        yield expiry != null
            && (today.isAfter(expiry) || (!session.open() && !today.isBefore(expiry)));
      }
      case FILL_OR_KILL -> false;
    };
  }

  private Order reject(Order order, String reason) {
    try {
      return orderService.reject(OrderIdType.EXCHANGE, order.orderId(), reason);
    } catch (RuntimeException exception) {
      log.warn("Не удалось отклонить заявку {}: {}", order.orderId(), exception.getMessage());
      return cancelFallback(order, reason);
    }
  }

  /** Заявка уже частично исполнена и не может быть отклонена — снимаем остаток. */
  private Order cancelFallback(Order order, String reason) {
    try {
      Order current = orderService.get(OrderIdType.EXCHANGE, order.orderId());
      if (current.status().isActive()) {
        return orderService.cancel(OrderIdType.EXCHANGE, order.orderId(), reason);
      }
      return current;
    } catch (RuntimeException exception) {
      log.warn("Состояние заявки {} недоступно: {}", order.orderId(), exception.getMessage());
      return order;
    }
  }

  private static boolean isExecutable(OrderStatus status) {
    return status == OrderStatus.NEW || status == OrderStatus.PARTIALLY_FILLED;
  }

  private static String failureReason(RuntimeException exception) {
    String message = exception.getMessage();
    return message == null || message.isBlank()
        ? "Ошибка исполнения: " + exception.getClass().getSimpleName()
        : message;
  }
}
