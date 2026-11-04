package ru.moex.papertrader.core.execution;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import ru.moex.papertrader.core.domain.Order;
import ru.moex.papertrader.core.domain.OrderType;
import ru.moex.papertrader.core.domain.Side;
import ru.moex.papertrader.core.domain.TimeInForce;
import ru.moex.papertrader.core.marketdata.OrderBookSnapshot;

/**
 * Матчинг заявок по стакану (ADR-9).
 *
 * <p>Маркет-заявка немедленно пересекает стакан: buy — по ask, sell — по bid, с проскальзыванием в
 * неблагоприятную сторону. Лимитка исполняется только при пересечении цены и по цене «лимит или
 * лучше» (buy: ask ≤ limit, sell: bid ≥ limit). Частичное исполнение ограничено объёмом levels,
 * доступных по цене; непокрытый остаток маркета снимается.
 *
 * <p>FOK исполняется только целиком: если доступного объёма не хватает, заявка отклоняется.
 * Политика ликвидности проверяется до матчинга; при SPLIT исполнение дробится на части не больше
 * {@link LiquidityDecision#maxPartQuantity()}.
 *
 * <p>Класс не меняет заявку: применение сделок и смена статуса — задача сервисного слоя.
 */
public final class MatchingEngine {

  private static final int PRICE_SCALE = 6;

  private final SlippageModel slippageModel;
  private final LiquidityPolicy liquidityPolicy;

  public MatchingEngine(SlippageModel slippageModel, LiquidityPolicy liquidityPolicy) {
    this.slippageModel = Objects.requireNonNull(slippageModel, "slippageModel");
    this.liquidityPolicy = Objects.requireNonNull(liquidityPolicy, "liquidityPolicy");
  }

  public MatchResult match(Order order, OrderBookSnapshot book, BigDecimal adv) {
    Objects.requireNonNull(order, "order");
    Objects.requireNonNull(book, "book");
    long remaining = order.quantity() - order.executedQuantity();
    if (remaining <= 0) {
      return MatchResult.none();
    }

    LiquidityDecision liquidity = liquidityPolicy.evaluate(remaining, adv);
    if (liquidity.action() == LiquidityDecision.Action.REJECT) {
      return MatchResult.reject(liquidity.reason());
    }

    boolean market = order.orderType() == OrderType.MARKET;
    boolean fok = order.timeInForce() == TimeInForce.FILL_OR_KILL;
    long maxPart =
        fok || liquidity.action() == LiquidityDecision.Action.PASS
            ? remaining
            : liquidity.maxPartQuantity();
    // FOK всегда требует весь остаток; SPLIT для лимитки исполняет одну часть за проход.
    long target = market || fok ? remaining : Math.min(remaining, maxPart);

    List<OrderBookSnapshot.Level> levels =
        market || order.price() != null
            ? order.side() == Side.BUY ? sortedAsks(book) : sortedBids(book)
            : List.of();

    List<MatchFill> fills = new ArrayList<>();
    long left = target;
    for (OrderBookSnapshot.Level level : levels) {
      if (left == 0) {
        break;
      }
      if (level.price() == null || level.price().signum() <= 0 || level.quantity() <= 0) {
        continue;
      }
      if (!market && !crosses(order.side(), level.price(), order.price())) {
        break;
      }
      long available = level.quantity();
      while (available > 0 && left > 0) {
        long quantity = Math.min(available, Math.min(left, maxPart));
        BigDecimal price =
            market
                ? slippedPrice(order.side(), level.price(), target, adv)
                : level.price().setScale(PRICE_SCALE, RoundingMode.HALF_UP);
        fills.add(new MatchFill(price, quantity));
        available -= quantity;
        left -= quantity;
      }
    }

    if (fills.isEmpty()) {
      if (fok) {
        return MatchResult.reject("FOK: недостаточно объёма в стакане для полного исполнения");
      }
      if (market) {
        return MatchResult.reject("Нет встречных заявок в стакане");
      }
      return MatchResult.none();
    }
    if (fok && left > 0) {
      return MatchResult.reject(
          "FOK: доступно " + (target - left) + " из " + target + " — заявка не исполнена");
    }
    return new MatchResult(
        fills,
        market && left > 0 ? MatchResult.RemainderAction.CANCEL : MatchResult.RemainderAction.KEEP,
        null);
  }

  private BigDecimal slippedPrice(
      Side side, BigDecimal levelPrice, long orderQuantity, BigDecimal adv) {
    BigDecimal rate = slippageModel.rate(side, levelPrice, orderQuantity, adv);
    if (rate.signum() < 0 || rate.compareTo(BigDecimal.ONE) >= 0) {
      throw new IllegalStateException("SlippageModel вернула недопустимую долю: " + rate);
    }
    BigDecimal multiplier =
        side == Side.BUY ? BigDecimal.ONE.add(rate) : BigDecimal.ONE.subtract(rate);
    return levelPrice.multiply(multiplier).setScale(PRICE_SCALE, RoundingMode.HALF_UP);
  }

  private static boolean crosses(Side side, BigDecimal levelPrice, BigDecimal limitPrice) {
    return side == Side.BUY
        ? levelPrice.compareTo(limitPrice) <= 0
        : levelPrice.compareTo(limitPrice) >= 0;
  }

  private static List<OrderBookSnapshot.Level> sortedAsks(OrderBookSnapshot book) {
    return book.asks().stream()
        .sorted(Comparator.comparing(OrderBookSnapshot.Level::price))
        .toList();
  }

  private static List<OrderBookSnapshot.Level> sortedBids(OrderBookSnapshot book) {
    return book.bids().stream()
        .sorted(Comparator.comparing(OrderBookSnapshot.Level::price).reversed())
        .toList();
  }
}
