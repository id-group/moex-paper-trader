package ru.moex.papertrader.infra.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import ru.moex.papertrader.TestInstruments;
import ru.moex.papertrader.core.domain.NewOrder;
import ru.moex.papertrader.core.domain.Order;
import ru.moex.papertrader.core.domain.OrderType;
import ru.moex.papertrader.core.domain.PortfolioSnapshot;
import ru.moex.papertrader.core.domain.Side;
import ru.moex.papertrader.core.domain.TimeInForce;
import ru.moex.papertrader.core.marketdata.Clock;
import ru.moex.papertrader.testutil.FixedClock;

class PortfolioServiceImplTest {

  private static final Instant NOW = Instant.parse("2026-11-01T10:00:00Z");
  private static final Clock CLOCK = FixedClock.at(NOW);

  @Test
  void locksCashOfActiveBuyLimitOrders() {
    OrderService orderService = mock(OrderService.class);
    Order buy = order(OrderType.LIMIT, Side.BUY, new BigDecimal("100"), 10);
    Order sell = order(OrderType.LIMIT, Side.SELL, new BigDecimal("200"), 5);
    Order market = order(OrderType.MARKET, Side.BUY, null, 1);
    when(orderService.activeOrders()).thenReturn(List.of(buy, sell, market));
    PortfolioService service =
        new PortfolioServiceImpl(
            orderService,
            new PortfolioProperties("PAPER-0001", new BigDecimal("1000000.00"), "RUB"),
            CLOCK);

    PortfolioSnapshot snapshot = service.snapshot();

    assertThat(snapshot.account()).isEqualTo("PAPER-0001");
    assertThat(snapshot.cash()).isEqualByComparingTo("1000000.00");
    assertThat(snapshot.lockedCash()).isEqualByComparingTo("1000");
    assertThat(snapshot.freeCash()).isEqualByComparingTo("999000");
    assertThat(snapshot.positions()).isEmpty();
    assertThat(snapshot.asOf()).isEqualTo(NOW);
  }

  @Test
  void emptyPortfolioHasNoLocks() {
    OrderService orderService = mock(OrderService.class);
    when(orderService.activeOrders()).thenReturn(List.of());
    PortfolioService service =
        new PortfolioServiceImpl(
            orderService,
            new PortfolioProperties("PAPER-0001", new BigDecimal("500"), "RUB"),
            CLOCK);

    assertThat(service.snapshot().lockedCash()).isEqualByComparingTo("0");
  }

  private static Order order(OrderType type, Side side, BigDecimal price, long quantity) {
    return Order.accept(
        new NewOrder(
            UUID.randomUUID(), "SBER", "TQBR", side, type, TimeInForce.DAY, quantity, price),
        TestInstruments.sber(),
        "id",
        1,
        "PAPER-0001",
        NOW);
  }
}
