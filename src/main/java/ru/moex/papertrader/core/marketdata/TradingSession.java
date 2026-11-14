package ru.moex.papertrader.core.marketdata;

import java.time.Instant;

/**
 * Состояние торговой сессии (на этапе 2 — упрощение конфигом, этап 4 — торговый календарь).
 *
 * @param closeAt закрытие (UTC) актуального торгового дня источника: текущего дня, если сессия
 *     открыта или ещё впереди, либо последнего прошедшего торгового дня. Используется как граница
 *     жизни DAY-заявок. {@code null} — источник границу не сообщает.
 */
public record TradingSession(
    int id, String name, boolean open, Instant nextSessionAt, Instant closeAt) {

  /** Сессия без известной границы закрытия: источник не сообщает {@code closeAt}. */
  public TradingSession(int id, String name, boolean open, Instant nextSessionAt) {
    this(id, name, open, nextSessionAt, null);
  }
}
