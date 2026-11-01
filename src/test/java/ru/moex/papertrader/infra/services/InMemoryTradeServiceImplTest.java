package ru.moex.papertrader.infra.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import ru.moex.papertrader.TestInstruments;
import ru.moex.papertrader.core.domain.PageResult;
import ru.moex.papertrader.core.domain.Side;
import ru.moex.papertrader.core.domain.Trade;
import ru.moex.papertrader.core.domain.TradeSearchCriteria;
import ru.moex.papertrader.infra.services.exception.InvalidRequestException;

class InMemoryTradeServiceImplTest {

  private static final Instant NOW = Instant.parse("2026-11-01T10:00:00Z");

  private final InMemoryTradeServiceImpl service = new InMemoryTradeServiceImpl();

  @Test
  void registerAssignsSequentialTradeNumbers() {
    Trade first = service.register(trade("SBER", Side.BUY, "285.50", 10, NOW));
    Trade second = service.register(trade("GAZP", Side.SELL, "165.20", 5, NOW.plusSeconds(1)));

    assertThat(first.tradeNum()).isEqualTo(1);
    assertThat(second.tradeNum()).isEqualTo(2);
  }

  @Test
  void searchFiltersByTickerAndSide() {
    service.register(trade("SBER", Side.BUY, "285.50", 10, NOW));
    service.register(trade("SBER", Side.SELL, "286.50", 10, NOW.plusSeconds(1)));
    service.register(trade("GAZP", Side.BUY, "165.20", 5, NOW.plusSeconds(2)));

    PageResult<Trade> result =
        service.search(
            new TradeSearchCriteria(null, null, Side.BUY, null, null, Set.of("SBER"), null),
            0,
            10,
            List.of());

    assertThat(result.totalRecords()).isEqualTo(1);
    assertThat(result.items().get(0).price()).isEqualByComparingTo("285.50");
  }

  @Test
  void searchSupportsPaginationAndSorting() {
    service.register(trade("SBER", Side.BUY, "285.50", 10, NOW));
    service.register(trade("SBER", Side.BUY, "286.50", 10, NOW.plusSeconds(1)));
    service.register(trade("SBER", Side.BUY, "287.50", 10, NOW.plusSeconds(2)));

    PageResult<Trade> page =
        service.search(
            new TradeSearchCriteria(null, null, null, null, null, null, null),
            1,
            2,
            List.of("tradeDateTime,desc"));

    assertThat(page.totalRecords()).isEqualTo(3);
    assertThat(page.totalPages()).isEqualTo(2);
    assertThat(page.items()).hasSize(1);
    assertThat(page.items().get(0).price()).isEqualByComparingTo("285.50");
  }

  @Test
  void invalidSortDirectionReturnsBadRequest() {
    TradeSearchCriteria criteria =
        new TradeSearchCriteria(null, null, null, null, null, null, null);

    assertThatThrownBy(() -> service.search(criteria, 0, 10, List.of("ticker,up")))
        .isInstanceOf(InvalidRequestException.class);
  }

  private static Trade trade(
      String ticker, Side side, String price, long quantity, Instant executedAt) {
    return new Trade(
        0,
        1,
        "PAPER-0001",
        ticker.equals("SBER") ? TestInstruments.sber() : TestInstruments.gazp(),
        side,
        new BigDecimal(price),
        quantity,
        BigDecimal.ZERO,
        executedAt);
  }
}
