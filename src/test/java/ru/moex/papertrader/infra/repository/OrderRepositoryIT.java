package ru.moex.papertrader.infra.repository;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.jdbc.core.JdbcTemplate;
import ru.moex.papertrader.PostgresContainerSupport;
import ru.moex.papertrader.core.domain.OrderStatus;
import ru.moex.papertrader.core.domain.OrderType;
import ru.moex.papertrader.core.domain.Side;
import ru.moex.papertrader.core.domain.TimeInForce;
import ru.moex.papertrader.infra.model.AccountEntity;
import ru.moex.papertrader.infra.model.InstrumentEntity;
import ru.moex.papertrader.infra.model.OrderEntity;

/** Сохранение и чтение заявки; перечисления хранятся строкой. */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class OrderRepositoryIT extends PostgresContainerSupport {

  // Значения уникальны для класса: БД общая для всех тестов JVM, и BKS-тесты коммитят свои
  // счёт, инструменты и заявки. Глобальные уникальные ключи (order_num, client_order_id)
  // не должны пересекаться с закоммиченными данными.
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
  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private EntityManager entityManager;

  @Test
  void savesAndReadsOrder() {
    Long instrumentId = persistAccountAndInstrument();
    orders.saveAndFlush(order(instrumentId));
    entityManager.clear();

    OrderEntity loaded = orders.findById(ORDER_ID).orElseThrow();

    assertThat(loaded.getOrderNum()).isEqualTo(ORDER_NUM);
    assertThat(loaded.getClientOrderId()).isEqualTo(CLIENT_ORDER_ID);
    assertThat(loaded.getAccountId()).isEqualTo(ACCOUNT_ID);
    assertThat(loaded.getInstrumentId()).isEqualTo(instrumentId);
    assertThat(loaded.getSide()).isEqualTo(Side.BUY);
    assertThat(loaded.getOrderType()).isEqualTo(OrderType.LIMIT);
    assertThat(loaded.getTimeInForce()).isEqualTo(TimeInForce.DAY);
    assertThat(loaded.getQuantity()).isEqualTo(10L);
    assertThat(loaded.getPrice()).isEqualTo(new BigDecimal("285.500000"));
    assertThat(loaded.getPrice().scale()).isEqualTo(6);
    assertThat(loaded.getClientCode()).isEqualTo(ACCOUNT_ID);
    assertThat(loaded.getCreatedAt()).isEqualTo(NOW);
    assertThat(loaded.getStatus()).isEqualTo(OrderStatus.NEW);
    assertThat(loaded.getExecutedQuantity()).isZero();
    assertThat(loaded.getAveragePrice()).isNull();
  }

  @Test
  void persistsEnumsAsStringNames() {
    Long instrumentId = persistAccountAndInstrument();
    orders.saveAndFlush(order(instrumentId));

    Map<String, Object> row =
        jdbcTemplate.queryForMap(
            "SELECT side, order_type, time_in_force, status FROM orders WHERE order_id = ?",
            ORDER_ID);

    assertThat(row.get("side")).isEqualTo("BUY");
    assertThat(row.get("order_type")).isEqualTo("LIMIT");
    assertThat(row.get("time_in_force")).isEqualTo("DAY");
    assertThat(row.get("status")).isEqualTo("NEW");
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

  private static OrderEntity order(Long instrumentId) {
    return new OrderEntity(
        ORDER_ID,
        ORDER_NUM,
        CLIENT_ORDER_ID,
        ACCOUNT_ID,
        instrumentId,
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
        null);
  }
}
