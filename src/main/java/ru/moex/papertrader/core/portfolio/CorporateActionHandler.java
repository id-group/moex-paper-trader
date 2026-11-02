package ru.moex.papertrader.core.portfolio;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Обработчик корпоративных событий: дивидендов и сплитов.
 *
 * <p>Этап 3 фиксирует только точку расширения: реальное применение событий к позициям и кэшу —
 * TODO(dividends/splits). Интерфейс живёт в core, чтобы домен не зависел от источника событий.
 */
public interface CorporateActionHandler {

  /** Применяет корпоративное событие к портфелю. */
  void apply(CorporateAction action);

  /**
   * Корпоративное событие по инструменту.
   *
   * @param amount для {@link Kind#DIVIDEND} — сумма на акцию, для {@link Kind#SPLIT} — коэффициент
   *     дробления
   */
  record CorporateAction(String ticker, Kind kind, BigDecimal amount) {

    public CorporateAction {
      Objects.requireNonNull(ticker, "ticker");
      Objects.requireNonNull(kind, "kind");
    }
  }

  /** Вид корпоративного события; список расширяется по мере реализации. */
  enum Kind {
    DIVIDEND,
    SPLIT
  }
}
