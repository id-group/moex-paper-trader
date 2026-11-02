package ru.moex.papertrader.infra.services;

import java.math.BigDecimal;
import java.time.LocalDate;
import ru.moex.papertrader.core.portfolio.NavSnapshot;

/**
 * Клиринг виртуального портфеля: ежедневная вариационная маржа фьючерсов и NAV-срез закрытия дня.
 *
 * <p>Расчётная цена дня — последняя цена {@code MarketDataProvider}. Дата и момент среза передаются
 * вызывающим: торговый календарь и определение закрытия основной сессии упрощены до этапа 4.
 */
public interface MarginSettlementService {

  /**
   * Начисляет вариационную маржу по всем открытым фьючерсным позициям счёта и пересчитывает ГО от
   * новой расчётной цены. Идемпотентен: повторный вызов с той же расчётной ценой возвращает ноль.
   *
   * @return суммарная ВМ за вызов; может быть отрицательной
   */
  BigDecimal settleVariationMargin();

  /**
   * Закрывает торговый день: начисляет ВМ, считает NAV и сохраняет ровно один срез {@code
   * nav_daily} на пару (счёт, дата).
   *
   * @throws ru.moex.papertrader.infra.services.exception.ConflictException если срез за эту дату
   *     уже существует
   */
  NavSnapshot closeDay(LocalDate navDate);
}
