package ru.moex.papertrader.infra.services;

import java.math.BigDecimal;
import ru.moex.papertrader.core.domain.Order;
import ru.moex.papertrader.core.domain.OrderIdType;

/**
 * Исполнение заявок в виртуальном портфеле.
 *
 * <p>Этап 3: акции и фьючерсы. Сделка проводится по переданной цене или по последней цене заглушки
 * рыночных данных; проскальзывание и матчинг по стакану появятся на этапе 5. Фьючерсная сделка не
 * списывает стоимость контракта — блокируется только ГО позиции, кэш меняется на биржевой сбор.
 * Вызывается внутренними сервисами и тестами — REST-метода исполнения в контракте БКС этапа 3 нет.
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
}
