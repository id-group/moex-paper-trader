package ru.moex.papertrader.core.portfolio;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Расчёт NAV на закрытие основной сессии.
 *
 * <p>На вход принимает уже посчитанные величины: денежные средства, комиссию (её считает {@code
 * core/commission}), начисленную вариационную маржу и позиции по акциям. Калькулятор намеренно не
 * зависит от {@code VirtualPortfolio} и {@code Position}: это чистые вычисления без изменения
 * состояния.
 *
 * <p>В рыночную стоимость входят только акции; фьючерсы учтены вариационной маржой, а начальное ГО
 * — блокировка, не расход, поэтому в NAV не вычитается.
 */
public final class NavCalculator {

  /**
   * Считает NAV портфеля.
   *
   * @param asOf момент среза (передаётся явно, чтобы не читать системные часы)
   * @param cash денежные средства на счёте до вычета комиссии и без вариационной маржи
   * @param commission суммарная комиссия по сделкам с начала дня
   * @param variationMargin начисленная вариационная маржа (может быть отрицательной)
   * @param stockHoldings позиции по акциям: тикер → количество (может быть отрицательным)
   * @param closingPrices цены закрытия акций: тикер → цена
   */
  public NavSnapshot calculate(
      Instant asOf,
      BigDecimal cash,
      BigDecimal commission,
      BigDecimal variationMargin,
      Map<String, Long> stockHoldings,
      Map<String, BigDecimal> closingPrices) {
    requireNonNull(asOf, "asOf");
    requireNonNull(cash, "cash");
    requireNonNull(commission, "commission");
    requireNonNull(variationMargin, "variationMargin");
    requireNonNull(stockHoldings, "stockHoldings");
    requireNonNull(closingPrices, "closingPrices");
    if (commission.signum() < 0) {
      throw new IllegalArgumentException("commission не может быть отрицательной");
    }

    List<NavSnapshot.Holding> holdings = new ArrayList<>();
    BigDecimal marketValue = Money.ZERO;
    // TreeMap даёт детерминированный порядок среза независимо от порядка входной Map.
    for (Map.Entry<String, Long> entry : new TreeMap<>(stockHoldings).entrySet()) {
      String ticker = entry.getKey();
      Long quantity = entry.getValue();
      if (quantity == null) {
        throw new IllegalArgumentException("количество по тикеру " + ticker + " не задано");
      }
      BigDecimal closingPrice = closingPrices.get(ticker);
      if (closingPrice == null) {
        throw new IllegalArgumentException("нет цены закрытия для тикера " + ticker);
      }
      if (closingPrice.signum() <= 0) {
        throw new IllegalArgumentException(
            "цена закрытия для тикера " + ticker + " должна быть положительной");
      }
      marketValue = marketValue.add(closingPrice.multiply(BigDecimal.valueOf(quantity)));
      holdings.add(new NavSnapshot.Holding(ticker, quantity, closingPrice));
    }

    BigDecimal roundedCash = Money.round(cash);
    BigDecimal roundedCommission = Money.round(commission);
    BigDecimal roundedVariationMargin = Money.round(variationMargin);
    BigDecimal roundedMarketValue = Money.round(marketValue);
    BigDecimal nav =
        Money.round(
            roundedCash
                .subtract(roundedCommission)
                .add(roundedVariationMargin)
                .add(roundedMarketValue));

    return new NavSnapshot(
        asOf,
        roundedCash,
        roundedCommission,
        roundedVariationMargin,
        roundedMarketValue,
        nav,
        holdings);
  }

  private static void requireNonNull(Object value, String name) {
    if (value == null) {
      throw new IllegalArgumentException(name + " не может быть null");
    }
  }
}
