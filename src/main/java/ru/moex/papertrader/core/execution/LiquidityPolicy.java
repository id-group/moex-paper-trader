package ru.moex.papertrader.core.execution;

import java.math.BigDecimal;

/** Политика контроля ликвидности: пропустить, отклонить или разбить заявку по объёму ADV. */
public interface LiquidityPolicy {

  /**
   * Оценивает объём заявки.
   *
   * @param quantity объём к исполнению в штуках, больше 0
   * @param adv средний дневной объём; {@code null} или 0 — данных нет, ограничение не применяется
   */
  LiquidityDecision evaluate(long quantity, BigDecimal adv);
}
