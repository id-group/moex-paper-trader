package ru.moex.papertrader.infra.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.dao.DataIntegrityViolationException;
import ru.moex.papertrader.PostgresContainerSupport;
import ru.moex.papertrader.core.domain.OrderStatus;
import ru.moex.papertrader.core.domain.OrderType;
import ru.moex.papertrader.core.domain.Side;
import ru.moex.papertrader.core.domain.TimeInForce;
import ru.moex.papertrader.infra.model.AccountEntity;
import ru.moex.papertrader.infra.model.FillEntity;
import ru.moex.papertrader.infra.model.InstrumentEntity;
import ru.moex.papertrader.infra.model.OrderEntity;

/** Сохранение сделки и контроль внешнего ключа на заявку. */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class FillRepositoryIT extends PostgresContainerSupport {

  // Уникальные ключи на класс: БД общая для всех тестов JVM (см. OrderRepositoryIT).
  private static final String ACCOUNT_ID = "IT-" + UUID.randomUUID();
  private static final String TICKER = "IT" + UUID.randomUUID().toString().substring(0, 8);
  private static final String ORDER_ID = "IT-" + UUID.randomUUID();
  private static final UUID CLIENT_ORDER_ID = UUID.randomUUID();
  private static final long ORDER_NUM =
      ThreadLocalRandom.current().nextLong(1_000_000_000L, 2_000_000_000L);
  private static final Instant NOW = Instant.parse("2026-11-02T10:00:00Z");

  @Autowired private AccountRepository accounts;
  @Autowired private InstrumentRepository instruments;
  @Autowired private OrderRepository orders;
  @Autowired private FillRepository fills;
  @Autowired private EntityManager entityManager;

  @Test
  void savesAndReadsFillPreservingPriceAndCommissionScale() {
    Long instrumentId = persistAccountInstrumentAndOrder();
    fills.saveAndFlush(fill(ORDER_ID, instrumentId));
    entityManager.clear();

    FillEntity loaded = fills.findByOrderId(ORDER_ID).getFirst();

    assertThat(loaded.getOrderId()).isEqualTo(ORDER_ID);
    assertThat(loaded.getAccountId()).isEqualTo(ACCOUNT_ID);
    assertThat(loaded.getInstrumentId()).isEqualTo(instrumentId);
    assertThat(loaded.getPrice()).isEqualTo(new BigDecimal("285.500000"));
    assertThat(loaded.getPrice().scale()).isEqualTo(6);
    assertThat(loaded.getQuantity()).isEqualTo(10L);
    assertThat(loaded.getCommission()).isEqualTo(new BigDecimal("2.86"));
    assertThat(loaded.getCommission().scale()).isEqualTo(2);
    assertThat(loaded.getExecutedAt()).isEqualTo(NOW);
  }

  @Test
  void rejectsFillWithUnknownOrder() {
    Long instrumentId = persistAccountInstrumentAndOrder();

    assertThatThrownBy(() -> fills.saveAndFlush(fill("UNKNOWN-ORDER", instrumentId)))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  private Long persistAccountInstrumentAndOrder() {
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
    orders.saveAndFlush(
        new OrderEntity(
            ORDER_ID,
            ORDER_NUM,
            CLIENT_ORDER_ID,
            ACCOUNT_ID,
            instrument.getId(),
            Side.BUY,
            OrderType.LIMIT,
            TimeInForce.DAY,
            10L,
            new BigDecimal("285.500000"),
            ACCOUNT_ID,
            NOW,
            NOW,
            OrderStatus.NEW,
            0L,
            null));
    return instrument.getId();
  }

  private static FillEntity fill(String orderId, Long instrumentId) {
    return new FillEntity(
        orderId,
        ACCOUNT_ID,
        instrumentId,
        new BigDecimal("285.500000"),
        10L,
        new BigDecimal("2.86"),
        NOW);
  }
}
