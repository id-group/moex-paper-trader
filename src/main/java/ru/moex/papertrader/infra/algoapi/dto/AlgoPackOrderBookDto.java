package ru.moex.papertrader.infra.algoapi.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Стакан AlgoPack из ответа {@code .../securities/{ticker}/orderbook.json}.
 *
 * <p>API отдаёт плоский список уровней (колонки {@code BOARDID}, {@code SECID}, {@code BUYSELL},
 * {@code PRICE}, {@code QUANTITY}, {@code UPDATETIME}); клиент агрегирует строки в два списка
 * уровней. {@code BUYSELL=B} — покупка, {@code BUYSELL=S} — продажа.
 *
 * <p>Документация: <a
 * href="https://moexalgo.github.io/docs/api/real-time-market-data-акции">Real-time market data —
 * Акции</a>.
 *
 * @param secId код инструмента
 * @param boardId режим торгов
 * @param updatedAt дата и время снимка (из блока {@code dataversion} + {@code UPDATETIME})
 * @param bids уровни покупки, от лучшей цены
 * @param asks уровни продажи, от лучшей цены
 */
public record AlgoPackOrderBookDto(
    String secId, String boardId, Instant updatedAt, List<Level> bids, List<Level> asks) {

  public AlgoPackOrderBookDto {
    bids = bids == null ? List.of() : List.copyOf(bids);
    asks = asks == null ? List.of() : List.copyOf(asks);
  }

  /** Уровень стакана: цена и количество в штуках. */
  public record Level(BigDecimal price, long quantity) {}
}
