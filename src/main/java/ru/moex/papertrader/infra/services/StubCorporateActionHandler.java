package ru.moex.papertrader.infra.services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import ru.moex.papertrader.core.portfolio.CorporateActionHandler;

/**
 * Заглушка корпоративных событий этапа 3.
 *
 * <p>Дивиденды и сплиты пока не влияют на портфель: метод только пишет отладочный след. Реальная
 * логика — TODO(dividends/splits).
 */
@Component
public class StubCorporateActionHandler implements CorporateActionHandler {

  private static final Logger log = LoggerFactory.getLogger(StubCorporateActionHandler.class);

  @Override
  public void apply(CorporateAction action) {
    // TODO(dividends/splits): начисление дивидендов и пересчёт позиций при сплите.
    log.debug("Корпоративное событие {} по {} пропущено заглушкой", action.kind(), action.ticker());
  }
}
