package ru.moex.papertrader.infra.services;

import java.math.BigDecimal;
import java.util.List;
import org.springframework.stereotype.Component;
import ru.moex.papertrader.core.commission.InstrumentType;
import ru.moex.papertrader.core.domain.Instrument;
import ru.moex.papertrader.core.domain.Order;
import ru.moex.papertrader.core.domain.Side;
import ru.moex.papertrader.core.marketdata.MarketDataProvider;
import ru.moex.papertrader.core.marketdata.Quote;
import ru.moex.papertrader.core.portfolio.MarginCalculator;
import ru.moex.papertrader.core.portfolio.Money;

/**
 * Блокировки средств активными заявками — единая формула для портфельного снапшота и исполнения
 * заявок.
 *
 * <p>Акционная BUY с ценой блокирует {@code цена × остаток}; рыночная BUY без цены и любая SELL
 * акций — ничего. Фьючерсная заявка любой стороны блокирует ГО {@code |остаток| × опорная цена ×
 * ставка}; опорная цена — цена лимитной заявки, для рыночной — последняя цена {@link
 * MarketDataProvider}.
 *
 * <p>Исполнение заявки исключает её собственную блокировку из расчёта доступных средств, иначе
 * стоимость сделки учитывалась бы дважды.
 */
@Component
public class OrderLocksCalculator {

  /** Ставки ГО по умолчанию (10%, без переопределений по тикеру). */
  private static final MarginCalculator MARGIN_CALCULATOR = MarginCalculator.withDefaults();

  private final MarketDataProvider marketDataProvider;

  public OrderLocksCalculator(MarketDataProvider marketDataProvider) {
    this.marketDataProvider = marketDataProvider;
  }

  /**
   * Считает блокировки активных заявок.
   *
   * @param activeOrders активные заявки счёта
   * @param excludedOrderId заявка, чью блокировку не нужно учитывать; {@code null} — учитывать все
   */
  public OrderLocks calculate(List<Order> activeOrders, String excludedOrderId) {
    BigDecimal stockLocks = BigDecimal.ZERO;
    BigDecimal futuresMargin = BigDecimal.ZERO;
    for (Order order : activeOrders) {
      if (excludedOrderId != null && excludedOrderId.equals(order.orderId())) {
        continue;
      }
      long remaining = order.quantity() - order.executedQuantity();
      if (remaining <= 0) {
        continue;
      }
      if (isFutures(order.instrument())) {
        futuresMargin =
            futuresMargin.add(
                MARGIN_CALCULATOR.initialMargin(
                    order.instrument().ticker(), remaining, referencePrice(order)));
      } else if (order.side() == Side.BUY && order.price() != null) {
        stockLocks = stockLocks.add(order.price().multiply(BigDecimal.valueOf(remaining)));
      }
    }
    return new OrderLocks(Money.round(stockLocks), Money.round(futuresMargin));
  }

  private BigDecimal referencePrice(Order order) {
    if (order.price() != null) {
      return order.price();
    }
    return marketDataProvider
        .quote(order.instrument())
        .map(Quote::last)
        .orElseThrow(
            () ->
                new IllegalStateException(
                    "Нет последней цены для фьючерсной заявки " + order.orderId()));
  }

  private static boolean isFutures(Instrument instrument) {
    return InstrumentType.FUTURES.name().equalsIgnoreCase(instrument.type());
  }

  /**
   * Блокировки активных заявок: денежный резерв акционных покупок и ГО фьючерсов.
   *
   * @param stockLocks резерв под акционные BUY-заявки
   * @param futuresMargin гарантийное обеспечение фьючерсных заявок
   */
  public record OrderLocks(BigDecimal stockLocks, BigDecimal futuresMargin) {

    /** Суммарная блокировка средств активными заявками. */
    public BigDecimal total() {
      return stockLocks.add(futuresMargin);
    }
  }
}
