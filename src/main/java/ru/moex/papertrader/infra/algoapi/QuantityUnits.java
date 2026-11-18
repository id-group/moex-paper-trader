package ru.moex.papertrader.infra.algoapi;

import java.math.BigDecimal;

/**
 * Единицы количества в ответах MOEX ISS / AlgoPack.
 *
 * <p>Биржа отдаёт {@code QUANTITY} <b>в лотах</b> (подтверждено на живых данных FEES с {@code
 * LOTSIZE = 10000}: суммы {@code QUANTITY} уровней стакана того же порядка, что {@code BIDDEPTHT},
 * который ISS объявляет как «глубина спроса в тиках», то есть в лотах). Модель {@code core}
 * оперирует штуками ({@link ru.moex.papertrader.core.marketdata.OrderBookSnapshot.Level}), поэтому
 * пересчёт выполняется на границе источника: иначе {@code MatchingEngine} видит ликвидность,
 * заниженную в {@code lotSize} раз, и исполняет заявку по крупице за тик филами, некратными лоту.
 */
final class QuantityUnits {

  private QuantityUnits() {}

  /**
   * Переводит количество из лотов в штуки.
   *
   * @param lots количество в лотах, неотрицательное
   * @param lotSize размер лота в штуках; {@code null} или неположительный трактуется как 1
   * @return количество в штуках
   * @throws ArithmeticException если произведение не помещается в {@code long} или дробное
   */
  static long toShares(long lots, BigDecimal lotSize) {
    if (lots < 0) {
      throw new IllegalArgumentException("Количество в лотах не может быть отрицательным: " + lots);
    }
    BigDecimal lot = lotSize == null || lotSize.signum() <= 0 ? BigDecimal.ONE : lotSize;
    return BigDecimal.valueOf(lots).multiply(lot).longValueExact();
  }
}
