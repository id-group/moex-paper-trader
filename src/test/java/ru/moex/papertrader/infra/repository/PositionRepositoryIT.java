package ru.moex.papertrader.infra.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.dao.DataIntegrityViolationException;
import ru.moex.papertrader.PostgresContainerSupport;
import ru.moex.papertrader.infra.model.AccountEntity;
import ru.moex.papertrader.infra.model.InstrumentEntity;
import ru.moex.papertrader.infra.model.PositionEntity;

/** Позиции: чтение по паре (счёт, инструмент) и уникальность этой пары. */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class PositionRepositoryIT extends PostgresContainerSupport {

  // Уникальные ключи на класс: БД общая для всех тестов JVM (см. OrderRepositoryIT).
  private static final String ACCOUNT_ID = "IT-" + UUID.randomUUID();
  private static final String TICKER = "IT" + UUID.randomUUID().toString().substring(0, 8);
  private static final Instant NOW = Instant.parse("2026-11-02T10:00:00Z");

  @Autowired private AccountRepository accounts;
  @Autowired private InstrumentRepository instruments;
  @Autowired private PositionRepository positions;
  @Autowired private EntityManager entityManager;

  @Test
  void savesAndReadsPositionPreservingAveragePriceScale() {
    Long instrumentId = persistAccountAndInstrument();
    positions.saveAndFlush(
        new PositionEntity(ACCOUNT_ID, instrumentId, 10L, new BigDecimal("285.500000"), NOW));
    entityManager.clear();

    PositionEntity loaded =
        positions.findByAccountIdAndInstrumentId(ACCOUNT_ID, instrumentId).orElseThrow();

    assertThat(loaded.getQuantity()).isEqualTo(10L);
    assertThat(loaded.getAveragePrice()).isEqualTo(new BigDecimal("285.500000"));
    assertThat(loaded.getAveragePrice().scale()).isEqualTo(6);
    assertThat(loaded.getUpdatedAt()).isEqualTo(NOW);
    assertThat(positions.findByAccountId(ACCOUNT_ID)).hasSize(1);
  }

  @Test
  void rejectsSecondPositionForSameAccountAndInstrument() {
    Long instrumentId = persistAccountAndInstrument();
    positions.saveAndFlush(
        new PositionEntity(ACCOUNT_ID, instrumentId, 10L, new BigDecimal("285.500000"), NOW));

    assertThatThrownBy(
            () ->
                positions.saveAndFlush(
                    new PositionEntity(
                        ACCOUNT_ID, instrumentId, 5L, new BigDecimal("300.000000"), NOW)))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  private Long persistAccountAndInstrument() {
    accounts.saveAndFlush(new AccountEntity(ACCOUNT_ID, NOW));
    InstrumentEntity instrument =
        instruments.saveAndFlush(
            new InstrumentEntity(
                TICKER,
                "TQBR",
                "STOCK",
                "Сбербанк",
                "Сбербанк",
                "RU0009029540",
                new BigDecimal("10.000000"),
                new BigDecimal("0.010000"),
                "RUB"));
    return instrument.getId();
  }
}
