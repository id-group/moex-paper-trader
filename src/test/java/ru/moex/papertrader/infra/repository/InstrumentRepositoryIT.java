package ru.moex.papertrader.infra.repository;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import ru.moex.papertrader.PostgresContainerSupport;
import ru.moex.papertrader.infra.model.InstrumentEntity;

/** Сохранение и чтение инструмента; масштаб ценовых полей — 6 знаков. */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class InstrumentRepositoryIT extends PostgresContainerSupport {

  /** Уникальный тикер: SBER/TQBR в общей БД занимают BKS-тесты. */
  private static final String TICKER = "IT" + UUID.randomUUID().toString().substring(0, 8);

  @Autowired private InstrumentRepository instruments;
  @Autowired private EntityManager entityManager;

  @Test
  void savesAndReadsInstrumentByTickerAndClassCode() {
    instruments.saveAndFlush(instrument());
    entityManager.clear();

    InstrumentEntity loaded = instruments.findByTickerAndClassCode(TICKER, "TQBR").orElseThrow();

    assertThat(loaded.getType()).isEqualTo("STOCK");
    assertThat(loaded.getShortName()).isEqualTo("Сбербанк");
    assertThat(loaded.getDisplayName()).isEqualTo("Сбербанк");
    assertThat(loaded.getIsin()).isEqualTo("RU0009029540");
    assertThat(loaded.getLotSize()).isEqualTo(new BigDecimal("10.000000"));
    assertThat(loaded.getMinimumStep()).isEqualTo(new BigDecimal("0.010000"));
    assertThat(loaded.getMinimumStep().scale()).isEqualTo(6);
    assertThat(loaded.getCurrency()).isEqualTo("RUB");
  }

  private static InstrumentEntity instrument() {
    return new InstrumentEntity(
        TICKER,
        "TQBR",
        "STOCK",
        "Сбербанк",
        "Сбербанк",
        "RU0009029540",
        new BigDecimal("10.000000"),
        new BigDecimal("0.010000"),
        "RUB");
  }
}
