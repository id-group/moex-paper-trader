package ru.moex.papertrader.core.domain;

import java.time.Instant;
import java.util.Set;

/** Фильтр поиска сделок (аналог {@code TradesSearchRequest} контракта). */
public record TradeSearchCriteria(
    Instant from,
    Instant to,
    Side side,
    Set<Long> tradeNums,
    Set<Long> orderNums,
    Set<String> tickers,
    Set<String> classCodes) {

  public TradeSearchCriteria {
    tradeNums = tradeNums == null ? Set.of() : Set.copyOf(tradeNums);
    orderNums = orderNums == null ? Set.of() : Set.copyOf(orderNums);
    tickers = tickers == null ? Set.of() : Set.copyOf(tickers);
    classCodes = classCodes == null ? Set.of() : Set.copyOf(classCodes);
  }

  public boolean matches(Trade trade) {
    if (from != null && trade.executedAt().isBefore(from)) {
      return false;
    }
    if (to != null && trade.executedAt().isAfter(to)) {
      return false;
    }
    if (side != null && trade.side() != side) {
      return false;
    }
    if (!tradeNums.isEmpty() && !tradeNums.contains(trade.tradeNum())) {
      return false;
    }
    if (!orderNums.isEmpty() && !orderNums.contains(trade.orderNum())) {
      return false;
    }
    if (!tickers.isEmpty() && !tickers.contains(trade.instrument().ticker())) {
      return false;
    }
    return classCodes.isEmpty() || classCodes.contains(trade.instrument().classCode());
  }
}
