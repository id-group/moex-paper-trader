package ru.moex.papertrader.infra.services;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.List;
import ru.moex.papertrader.core.domain.Order;
import ru.moex.papertrader.core.domain.PortfolioSnapshot;
import ru.moex.papertrader.core.domain.Side;

/**
 * Эталон правил этапа 2: только акции, без ГО фьючерсов.
 *
 * <p>В приложении не используется: бин {@link PortfolioService} — {@link JpaPortfolioServiceImpl}.
 * Класс сохранён как исполняемая спецификация прежнего поведения (кэш из настроек и блокировка
 * акционных BUY-заявок) и остаётся объектом unit-тестов.
 */
public class PortfolioServiceImpl implements PortfolioService {

  private final OrderService orderService;
  private final PortfolioProperties properties;
  private final Clock clock;

  public PortfolioServiceImpl(
      OrderService orderService, PortfolioProperties properties, Clock clock) {
    this.orderService = orderService;
    this.properties = properties;
    this.clock = clock;
  }

  @Override
  public PortfolioSnapshot snapshot() {
    BigDecimal lockedCash =
        orderService.activeOrders().stream()
            .filter(order -> order.side() == Side.BUY)
            .filter(order -> order.price() != null)
            .map(PortfolioServiceImpl::orderValue)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    return new PortfolioSnapshot(
        properties.account(),
        properties.currency(),
        properties.initialCash(),
        lockedCash,
        List.of(),
        clock.instant());
  }

  private static BigDecimal orderValue(Order order) {
    return order.price().multiply(BigDecimal.valueOf(order.quantity()));
  }
}
