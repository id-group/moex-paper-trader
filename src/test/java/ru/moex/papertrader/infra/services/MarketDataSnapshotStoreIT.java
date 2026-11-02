package ru.moex.papertrader.infra.services;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import ru.moex.papertrader.PostgresContainerSupport;
import ru.moex.papertrader.core.marketdata.MarketDataSnapshot;
import ru.moex.papertrader.core.marketdata.OrderBookEvent;
import ru.moex.papertrader.core.marketdata.OrderBookSnapshot;
import ru.moex.papertrader.core.marketdata.Quote;
import ru.moex.papertrader.core.marketdata.QuoteSnapshot;
import ru.moex.papertrader.core.marketdata.Trade;
import ru.moex.papertrader.core.marketdata.TradeDirection;
import ru.moex.papertrader.core.marketdata.TradeEvent;
import ru.moex.papertrader.infra.mapper.MarketDataSnapshotMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * Хранилище снапшотов на PostgreSQL: запись всех типов, чтение таймлайна по возрастанию времени,
 * JSONB-полезная нагрузка и очистка по отсечке/retention.
 *
 * <p>Контекст JPA проверяет и маппинг Hibernate на схему {@code V2__market_data.sql} ({@code
 * ddl-auto=validate}): несовпадение типа {@code payload} с {@code jsonb} уронило бы тест.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({
  MarketDataSnapshotStore.class,
  MarketDataSnapshotMapper.class,
  MarketDataSnapshotStoreIT.StoreTestConfiguration.class
})
class MarketDataSnapshotStoreIT extends PostgresContainerSupport {

  private static final String TICKER = "ITSNAP" + UUID.randomUUID().toString().substring(0, 6);
  private static final String CLASS_CODE = "TQBR";
  private static final Instant T0 = Instant.parse("2026-11-02T07:00:00Z");
  private static final Instant T1 = Instant.parse("2026-11-02T07:00:01Z");
  private static final Instant T2 = Instant.parse("2026-11-02T07:00:02Z");

  @Autowired private MarketDataSnapshotStore store;
  @Autowired private EntityManager entityManager;
  @Autowired private JdbcTemplate jdbcTemplate;

  @BeforeEach
  void cleanSlate() {
    jdbcTemplate.update("DELETE FROM market_data_snapshots WHERE ticker = ?", TICKER);
  }

  @Test
  void savesAllKindsAndLoadsTimelineAscending() {
    QuoteSnapshot quote = new QuoteSnapshot(T0, quote());
    OrderBookEvent orderBook =
        new OrderBookEvent(
            T1,
            new OrderBookSnapshot(
                TICKER,
                CLASS_CODE,
                2,
                T1,
                List.of(
                    new OrderBookSnapshot.Level(new BigDecimal("285.500000"), 100),
                    new OrderBookSnapshot.Level(new BigDecimal("285.400000"), 200)),
                List.of(new OrderBookSnapshot.Level(new BigDecimal("285.600000"), 300))));
    TradeEvent trade =
        new TradeEvent(
            T2,
            new Trade(
                T2, TICKER, CLASS_CODE, new BigDecimal("285.700000"), 10, TradeDirection.BUY));

    // Порядок входа намеренно не совпадает с временем: таймлайн сортирует хранилище.
    store.save(List.of(trade, quote, orderBook));
    flushAndClear();

    List<MarketDataSnapshot> loaded =
        store.load(TICKER, CLASS_CODE, T0.minusSeconds(1), T2.plusSeconds(1));

    assertThat(loaded).hasSize(3);
    assertThat(loaded).extracting(MarketDataSnapshot::timestamp).containsExactly(T0, T1, T2);
    assertThat(loaded).containsExactly(quote, orderBook, trade);
  }

  @Test
  void storesPayloadAsJsonbPreservingDomainFields() {
    store.save(List.of(new QuoteSnapshot(T0, quote())));
    flushAndClear();

    String columnType =
        jdbcTemplate.queryForObject(
            "SELECT data_type FROM information_schema.columns"
                + " WHERE table_name = 'market_data_snapshots' AND column_name = 'payload'",
            String.class);
    String tickerInPayload =
        jdbcTemplate.queryForObject(
            "SELECT payload -> 'quote' ->> 'ticker' FROM market_data_snapshots WHERE ticker = ?",
            String.class,
            TICKER);

    assertThat(columnType).isEqualTo("jsonb");
    assertThat(tickerInPayload).isEqualTo(TICKER);
    assertThat(store.load(TICKER, CLASS_CODE, T0, T0))
        .singleElement()
        .isEqualTo(new QuoteSnapshot(T0, quote()));
  }

  @Test
  void deleteBeforeRemovesOnlyOlderSnapshots() {
    store.save(List.of(quote(T0), trade(T1), trade(T2)));
    flushAndClear();

    long deleted = store.deleteBefore(T1);

    flushAndClear();
    assertThat(deleted).isEqualTo(1);
    assertThat(store.load(TICKER, CLASS_CODE, T0.minusSeconds(1), T2.plusSeconds(1)))
        .extracting(MarketDataSnapshot::timestamp)
        .containsExactly(T1, T2);
  }

  @Test
  void purgeExpiredAppliesRetentionDays() {
    store.save(List.of(quote(T0), trade(T1), trade(T2)));
    flushAndClear();

    long deleted = store.purgeExpired(T2.plusSeconds(8 * 24 * 60 * 60L));

    assertThat(deleted).isEqualTo(3);
    assertThat(store.load(TICKER, CLASS_CODE, T0.minusSeconds(1), T2.plusSeconds(1))).isEmpty();
  }

  @Test
  void emptyWindowReturnsNoSnapshots() {
    store.save(List.of(quote(T0)));
    flushAndClear();

    assertThat(store.load(TICKER, CLASS_CODE, T1, T2)).isEmpty();
  }

  @Test
  void savingEmptyBatchIsNoOp() {
    store.save(List.of());
    flushAndClear();

    assertThat(store.load(TICKER, CLASS_CODE, T0, T2)).isEmpty();
  }

  private void flushAndClear() {
    entityManager.flush();
    entityManager.clear();
  }

  private static QuoteSnapshot quote(Instant ts) {
    return new QuoteSnapshot(ts, quote());
  }

  private static Quote quote() {
    return new Quote(
        TICKER,
        CLASS_CODE,
        T0,
        17,
        "RUB",
        new BigDecimal("285.400000"),
        new BigDecimal("285.600000"),
        new BigDecimal("284.000000"),
        new BigDecimal("285.000000"),
        new BigDecimal("286.000000"),
        new BigDecimal("283.000000"),
        new BigDecimal("285.500000"),
        new BigDecimal("0.500000"),
        new BigDecimal("0.175400"));
  }

  private static TradeEvent trade(Instant ts) {
    return new TradeEvent(
        ts,
        new Trade(ts, TICKER, CLASS_CODE, new BigDecimal("285.700000"), 10, TradeDirection.SELL));
  }

  /** Тестовые бины среза JPA: Jackson и настройки рыночных данных. */
  @TestConfiguration
  static class StoreTestConfiguration {

    @Bean
    JsonMapper marketDataJsonMapper() {
      return JsonMapper.builder().build();
    }

    @Bean
    MarketDataProperties marketDataProperties() {
      return MarketDataProperties.defaults();
    }
  }
}
