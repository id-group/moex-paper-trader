package ru.moex.papertrader.infra.services;

import java.util.List;
import ru.moex.papertrader.core.domain.PageResult;
import ru.moex.papertrader.core.domain.Trade;
import ru.moex.papertrader.core.domain.TradeSearchCriteria;

/** Хранилище и поиск сделок (этап 2 — in-memory; этап 3 — JPA). */
public interface TradeService {

  /** Сохраняет сделку и присваивает ей порядковый номер. */
  Trade register(Trade trade);

  PageResult<Trade> search(
      TradeSearchCriteria criteria, int page, int size, List<String> sortSpecs);
}
