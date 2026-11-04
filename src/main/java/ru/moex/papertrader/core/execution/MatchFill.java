package ru.moex.papertrader.core.execution;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Запланированное исполнение: опорная цена стакана, фактическая цена после проскальзывания и объём.
 *
 * <p>Опорная цена нужна журналу отчётности, чтобы показать проскальзывание отдельно от комиссии.
 * Для лимитных сделок она совпадает с фактической ценой; для маркет-сделок это цена уровня стакана
 * до применения модели проскальзывания.
 */
public record MatchFill(BigDecimal price, BigDecimal referencePrice, long quantity) {

  public MatchFill {
    Objects.requireNonNull(price, "price");
    Objects.requireNonNull(referencePrice, "referencePrice");
    if (price.signum() <= 0) {
      throw new IllegalArgumentException("price должен быть положительным");
    }
    if (referencePrice.signum() <= 0) {
      throw new IllegalArgumentException("referencePrice должен быть положительным");
    }
    if (quantity <= 0) {
      throw new IllegalArgumentException("quantity должен быть больше 0");
    }
  }

  /** Сделка без отдельной опорной цены: проскальзывание не моделировалось. */
  public MatchFill(BigDecimal price, long quantity) {
    this(price, price, quantity);
  }
}
