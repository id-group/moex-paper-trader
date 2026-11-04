package ru.moex.papertrader.core.execution;

import java.math.BigDecimal;
import ru.moex.papertrader.core.domain.Side;

/**
 * Модель проскальзывания: доля цены, ухудшающая исполнение клиента.
 *
 * <p>Возвращаемая доля неотрицательна: {@code 0.001} — это 0.1%. Направление (buy дороже, sell
 * дешевле) применяет {@link MatchingEngine}.
 */
public interface SlippageModel {

  /**
   * Доля проскальзывания для одного исполнения.
   *
   * @param side сторона заявки
   * @param referencePrice цена уровня стакана до проскальзывания
   * @param quantity объём исполнения в штуках
   * @param adv средний дневной объём инструмента; {@code null} или 0 — данных нет
   * @return неотрицательная доля цены
   */
  BigDecimal rate(Side side, BigDecimal referencePrice, long quantity, BigDecimal adv);
}
