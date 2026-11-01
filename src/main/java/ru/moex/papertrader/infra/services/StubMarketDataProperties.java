package ru.moex.papertrader.infra.services;

import java.math.BigDecimal;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Конфигурация заглушки рыночных данных (этап 2; настоящий провайдер — этап 4). */
@ConfigurationProperties(prefix = "emulator.marketdata.stub")
public record StubMarketDataProperties(List<InstrumentDefinition> instruments) {

  public StubMarketDataProperties {
    instruments = instruments == null ? List.of() : List.copyOf(instruments);
  }

  /** Описание инструмента и базовая цена для генерации котировок. */
  public record InstrumentDefinition(
      String ticker,
      String classCode,
      String type,
      String shortName,
      String displayName,
      String isin,
      BigDecimal lotSize,
      BigDecimal minimumStep,
      String currency,
      BigDecimal basePrice) {}
}
