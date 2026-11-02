package ru.moex.papertrader.core.marketdata;

import java.time.Instant;
import java.util.List;

/**
 * Порт торгового календаря: состояние сессии на момент и торговые дни диапазона. Источник в LIVE —
 * расписание MOEX ISS с кэшем в БД (infra), в REPLAY — записанный календарь.
 */
public interface TradingCalendar {

  /** Состояние торговой сессии на момент времени. */
  TradingSession tradingSession(Instant at);

  /**
   * Торговые дни, даты которых (в МСК) пересекаются с диапазоном [from, to] (границы по UTC), по
   * возрастанию даты.
   */
  List<TradingDay> tradingDays(Instant from, Instant to);
}
