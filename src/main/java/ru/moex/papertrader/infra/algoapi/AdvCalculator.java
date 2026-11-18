package ru.moex.papertrader.infra.algoapi;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * Расчёт ADV — среднего дневного объёма торгов в штуках — по дневным свечам.
 *
 * <p>MOEX ISS и AlgoPack отдают {@code volume} свечи <b>в штуках</b>, поэтому пересчёт по лотности
 * не выполняется (проверка на живых данных: для дневной свечи FEES {@code value / volume = 0.0671}
 * при {@code LAST = 0.0678}, то есть объём уже указан в штуках; для {@code lotSize > 1} умножение
 * на лотность выводило бы среднюю цену сделки за границы диапазона свечи). Среднее считается по
 * числу полученных дневных свечей (фактических торговых дней), масштаб 2, округление {@code
 * HALF_UP}; нет данных — ноль.
 */
final class AdvCalculator {

  private static final int SCALE = 2;

  private AdvCalculator() {}

  /**
   * @param volumesInShares объёмы дневных свечей в штуках
   */
  static BigDecimal averageDailyVolume(List<BigDecimal> volumesInShares) {
    if (volumesInShares == null || volumesInShares.isEmpty()) {
      return BigDecimal.ZERO;
    }
    BigDecimal total = BigDecimal.ZERO;
    int days = 0;
    for (BigDecimal volume : volumesInShares) {
      if (volume == null) {
        continue;
      }
      total = total.add(volume);
      days++;
    }
    if (days == 0) {
      return BigDecimal.ZERO;
    }
    return total.divide(BigDecimal.valueOf(days), SCALE, RoundingMode.HALF_UP);
  }
}
