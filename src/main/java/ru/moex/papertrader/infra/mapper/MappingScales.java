package ru.moex.papertrader.infra.mapper;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Масштабы величин, зафиксированные схемой БД ({@code V1__init.sql}): деньги — {@code
 * NUMERIC(19,2)}, цены и средние цены — {@code NUMERIC(19,6)}.
 *
 * <p>Значения приводятся к масштабу схемы явно, чтобы не полагаться на молчаливое округление
 * PostgreSQL.
 */
final class MappingScales {

  /** Совпадает с {@code core.portfolio.Money.SCALE}. */
  static final int MONEY_SCALE = 2;

  /** Совпадает с масштабом {@code VirtualPortfolio.AVERAGE_PRICE_SCALE}. */
  static final int PRICE_SCALE = 6;

  private static final RoundingMode ROUNDING = RoundingMode.HALF_UP;

  private MappingScales() {}

  static BigDecimal money(BigDecimal value) {
    return value == null ? null : value.setScale(MONEY_SCALE, ROUNDING);
  }

  static BigDecimal price(BigDecimal value) {
    return value == null ? null : value.setScale(PRICE_SCALE, ROUNDING);
  }
}
