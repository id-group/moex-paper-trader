package ru.moex.papertrader.infra.mapper;

import java.util.Objects;
import org.springframework.stereotype.Component;
import ru.moex.papertrader.core.marketdata.TradingDay;
import ru.moex.papertrader.infra.model.TradingCalendarEntity;

/**
 * Маппинг торгового дня: сущность {@code trading_calendar} ↔ доменный {@link TradingDay}.
 *
 * <p>Идентичность строки — (движок, рынок, режим, дата); эти поля есть только в слое БД, поэтому
 * при создании сущности они передаются параметрами. Для неторгового дня окно сессии — {@code null}.
 */
@Component
public class TradingCalendarMapper {

  /** Создаёт сущность без {@code id}: первичный ключ назначает БД. */
  public TradingCalendarEntity toEntity(
      TradingDay day, String engine, String market, String board) {
    Objects.requireNonNull(day, "day");
    Objects.requireNonNull(day.date(), "day.date");
    return new TradingCalendarEntity(
        requireText(engine, "engine"),
        requireText(market, "market"),
        requireText(board, "board"),
        day.date(),
        day.trading(),
        day.openUtc(),
        day.closeUtc());
  }

  public TradingDay toDomain(TradingCalendarEntity entity) {
    Objects.requireNonNull(entity, "entity");
    return new TradingDay(
        Objects.requireNonNull(entity.getTradeDate(), "entity.tradeDate"),
        entity.isTrading(),
        entity.getOpenUtc(),
        entity.getCloseUtc());
  }

  private static String requireText(String value, String name) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(name + " не может быть пустым");
    }
    return value;
  }
}
