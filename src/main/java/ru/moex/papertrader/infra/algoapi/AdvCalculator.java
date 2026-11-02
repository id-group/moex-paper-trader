package ru.moex.papertrader.infra.algoapi;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * Расчёт ADV — среднего дневного объёма торгов в штуках — по дневным свечам.
 *
 * <p>Объём свечи задаётся в лотах, поэтому умножается на размер лота инструмента. Среднее считается
 * по числу полученных дневных свечей (фактических торговых дней), масштаб 2, округление {@code
 * HALF_UP}; нет данных — ноль.
 */
final class AdvCalculator {

  private static final int SCALE = 2;

  private AdvCalculator() {}

  /**
   * @param volumesInLots объёмы дневных свечей в лотах
   * @param lotSize размер лота в штуках
   */
  static BigDecimal averageDailyVolume(List<BigDecimal> volumesInLots, BigDecimal lotSize) {
    if (volumesInLots == null || volumesInLots.isEmpty()) {
      return BigDecimal.ZERO;
    }
    BigDecimal lot = lotSize == null || lotSize.signum() <= 0 ? BigDecimal.ONE : lotSize;
    BigDecimal total = BigDecimal.ZERO;
    int days = 0;
    for (BigDecimal volume : volumesInLots) {
      if (volume == null) {
        continue;
      }
      total = total.add(volume.multiply(lot));
      days++;
    }
    if (days == 0) {
      return BigDecimal.ZERO;
    }
    return total.divide(BigDecimal.valueOf(days), SCALE, RoundingMode.HALF_UP);
  }
}
