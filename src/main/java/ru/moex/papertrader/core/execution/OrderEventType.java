package ru.moex.papertrader.core.execution;

/** Тип события жизненного цикла заявки для журнала {@code order_events}. */
public enum OrderEventType {
  /** Заявка принята и сохранена в статусе NEW. */
  CREATED,
  /** Исполнена часть объёма. */
  PARTIALLY_FILLED,
  /** Исполнен весь объём. */
  FILLED,
  /** Заявка отменена клиентом или сервисом. */
  CANCELED,
  /** Заявка отклонена (FOK, ликвидность, недостаток средств). */
  REJECTED,
  /** Срок действия DAY/GTD истёк. */
  EXPIRED
}
