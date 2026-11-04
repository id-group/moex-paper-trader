package ru.moex.papertrader.infra.services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import ru.moex.papertrader.core.domain.Instrument;
import ru.moex.papertrader.core.domain.NewOrder;
import ru.moex.papertrader.core.domain.Order;
import ru.moex.papertrader.core.domain.OrderIdType;
import ru.moex.papertrader.core.execution.LiquidityDecision;
import ru.moex.papertrader.core.execution.LiquidityPolicy;
import ru.moex.papertrader.core.marketdata.MarketDataProvider;
import ru.moex.papertrader.infra.services.exception.LiquidityExceededException;

/**
 * Постановка заявки: предпроверка ликвидности до сохранения и немедленная попытка исполнения.
 *
 * <p>Заявка сохраняется всегда, даже если исполнить её сразу не удалось: ошибка исполнения
 * переводит её в {@code REJECTED}, а не возвращает клиенту 4xx/5xx. Исключение — превышение ADV при
 * политике {@code REJECT}: такая заявка отклоняется до сохранения с HTTP 429.
 */
@Service
public class OrderPlacementService {

  private static final Logger log = LoggerFactory.getLogger(OrderPlacementService.class);

  private final OrderService orderService;
  private final ExecutionService executionService;
  private final MarketDataProvider marketDataProvider;
  private final LiquidityPolicy liquidityPolicy;

  public OrderPlacementService(
      OrderService orderService,
      ExecutionService executionService,
      MarketDataProvider marketDataProvider,
      LiquidityPolicy liquidityPolicy) {
    this.orderService = orderService;
    this.executionService = executionService;
    this.marketDataProvider = marketDataProvider;
    this.liquidityPolicy = liquidityPolicy;
  }

  public Order place(NewOrder request) {
    requireLiquidity(request);
    Order order = orderService.place(request);
    try {
      return executionService.execute(OrderIdType.EXCHANGE, order.orderId());
    } catch (RuntimeException exception) {
      log.warn(
          "Заявка {} сохранена, но немедленное исполнение не удалось: {}",
          order.orderId(),
          exception.getMessage());
      return current(order);
    }
  }

  /** Предпроверка ADV: превышение порога при политике REJECT не доходит до сохранения заявки. */
  private void requireLiquidity(NewOrder request) {
    if (request.quantity() <= 0) {
      return;
    }
    marketDataProvider
        .instrument(request.ticker(), request.classCode())
        .map(instrument -> decision(request, instrument))
        .filter(decision -> decision.action() == LiquidityDecision.Action.REJECT)
        .ifPresent(
            decision -> {
              throw new LiquidityExceededException(decision.reason());
            });
  }

  private LiquidityDecision decision(NewOrder request, Instrument instrument) {
    return liquidityPolicy.evaluate(request.quantity(), marketDataProvider.adv(instrument));
  }

  private Order current(Order order) {
    try {
      return orderService.get(OrderIdType.EXCHANGE, order.orderId());
    } catch (RuntimeException exception) {
      return order;
    }
  }
}
