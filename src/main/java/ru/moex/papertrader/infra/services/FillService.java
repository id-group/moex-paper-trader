package ru.moex.papertrader.infra.services;

import java.math.BigDecimal;
import java.util.List;
import ru.moex.papertrader.core.domain.Order;
import ru.moex.papertrader.core.domain.OrderIdType;
import ru.moex.papertrader.core.execution.MatchFill;

/**
 * Исполнение заявок в виртуальном портфеле.
 *
 * <p>Этап 5: сделки приходят из {@code MatchingEngine} после матчинга по стакану; сервис проводит
 * их через портфель (комиссия, блокировки, позиция) и фиксирует события жизненного цикла.
 * Вызывается внутренними сервисами и тестами — REST-метода исполнения в контракте БКС нет.
 */
public interface FillService {

  /**
   * Исполняет часть заявки.
   *
   * @param idType тип идентификатора заявки
   * @param orderId клиентский UUID или биржевой идентификатор
   * @param quantity объём сделки, больше 0 и не больше остатка заявки
   * @param price цена сделки; {@code null} — последняя цена заглушки {@code MarketDataProvider}
   * @return заявка в состоянии после сделки
   */
  Order fill(OrderIdType idType, String orderId, long quantity, BigDecimal price);

  /**
   * Исполняет набор сделок одной заявки атомарно: при ошибке любой сделки откатываются все.
   *
   * @param fills сделки, рассчитанные матчингом, в порядке исполнения
   * @return заявка в состоянии после последней сделки
   */
  Order fillAll(OrderIdType idType, String orderId, List<MatchFill> fills);
}
