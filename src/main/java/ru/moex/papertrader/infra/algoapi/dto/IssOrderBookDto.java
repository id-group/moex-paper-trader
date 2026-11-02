package ru.moex.papertrader.infra.algoapi.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Стакан MOEX ISS из ответа {@code .../securities/{secid}/orderbook.json}.
 *
 * <p>API отдаёт плоский список уровней (колонки {@code BOARDID}, {@code SECID}, {@code BUYSELL},
 * {@code PRICE}, {@code QUANTITY}, {@code SEQNUM}, {@code UPDATETIME}); клиент агрегирует строки.
 * Отдельного поля «последняя цена» в стакане ISS нет — {@code last} заполняется только если API его
 * вернёт (задел на расширение схемы).
 *
 * <p>Документация: <a href="https://iss.moex.com/iss/reference/371">ISS: стакан инструмента</a>.
 *
 * @param secId код инструмента
 * @param boardId режим торгов
 * @param updatedAt время снимка (если API вернул дату, иначе {@code null})
 * @param last последняя цена, если присутствует в ответе
 * @param bids уровни покупки
 * @param asks уровни продажи
 */
public record IssOrderBookDto(
    String secId,
    String boardId,
    Instant updatedAt,
    BigDecimal last,
    List<Level> bids,
    List<Level> asks) {

  public IssOrderBookDto {
    bids = bids == null ? List.of() : List.copyOf(bids);
    asks = asks == null ? List.of() : List.copyOf(asks);
  }

  /** Уровень стакана: цена и количество в штуках. */
  public record Level(BigDecimal price, long quantity) {}
}
