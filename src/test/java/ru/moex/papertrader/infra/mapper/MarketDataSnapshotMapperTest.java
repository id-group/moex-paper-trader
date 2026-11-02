package ru.moex.papertrader.infra.mapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import ru.moex.papertrader.core.marketdata.MarketDataSnapshot;
import ru.moex.papertrader.core.marketdata.OrderBookEvent;
import ru.moex.papertrader.core.marketdata.OrderBookSnapshot;
import ru.moex.papertrader.core.marketdata.Quote;
import ru.moex.papertrader.core.marketdata.QuoteSnapshot;
import ru.moex.papertrader.core.marketdata.Trade;
import ru.moex.papertrader.core.marketdata.TradeDirection;
import ru.moex.papertrader.core.marketdata.TradeEvent;
import ru.moex.papertrader.infra.model.MarketDataSnapshotEntity;
import tools.jackson.databind.json.JsonMapper;

/** Round-trip всех типов снапшотов через JSONB-полезную нагрузку. */
class MarketDataSnapshotMapperTest {

  private static final String TICKER = "SBER";
  private static final String CLASS_CODE = "TQBR";
  private static final Instant TS = Instant.parse("2026-11-02T07:00:00Z");

  private final MarketDataSnapshotMapper mapper =
      new MarketDataSnapshotMapper(JsonMapper.builder().build());

  @Test
  void roundTripsQuoteSnapshot() {
    QuoteSnapshot original = new QuoteSnapshot(TS, quote());

    MarketDataSnapshotEntity entity = mapper.toEntity(original);

    assertThat(entity.getId()).isNull();
    assertThat(entity.getTs()).isEqualTo(TS);
    assertThat(entity.getKind()).isEqualTo("QUOTE");
    assertThat(entity.getTicker()).isEqualTo(TICKER);
    assertThat(entity.getClassCode()).isEqualTo(CLASS_CODE);
    assertThat(entity.getPayload())
        .contains("\"ticker\":\"SBER\"", "\"dateTime\":\"2026-11-02T07:00:00Z\"");

    MarketDataSnapshot restored = mapper.toDomain(entity);

    assertThat(restored).isEqualTo(original);
    assertThat(restored.kind().name()).isEqualTo("QUOTE");
  }

  @Test
  void roundTripsOrderBookEvent() {
    OrderBookEvent original =
        new OrderBookEvent(
            TS,
            new OrderBookSnapshot(
                TICKER,
                CLASS_CODE,
                2,
                TS,
                List.of(
                    new OrderBookSnapshot.Level(new BigDecimal("285.500000"), 100),
                    new OrderBookSnapshot.Level(new BigDecimal("285.400000"), 200)),
                List.of(new OrderBookSnapshot.Level(new BigDecimal("285.600000"), 300))));

    MarketDataSnapshotEntity entity = mapper.toEntity(original);
    MarketDataSnapshot restored = mapper.toDomain(entity);

    assertThat(entity.getKind()).isEqualTo("ORDER_BOOK");
    assertThat(restored).isEqualTo(original);
    assertThat(((OrderBookEvent) restored).orderBook().bidVolume()).isEqualTo(300);
  }

  @Test
  void roundTripsTradeEvent() {
    TradeEvent original =
        new TradeEvent(
            TS,
            new Trade(
                TS, TICKER, CLASS_CODE, new BigDecimal("285.500000"), 10, TradeDirection.BUY));

    MarketDataSnapshotEntity entity = mapper.toEntity(original);
    MarketDataSnapshot restored = mapper.toDomain(entity);

    assertThat(entity.getKind()).isEqualTo("TRADE");
    assertThat(restored).isEqualTo(original);
    assertThat(((TradeEvent) restored).trade().direction()).isEqualTo(TradeDirection.BUY);
  }

  @Test
  void entityToDomainToEntityPreservesColumnsAndPayload() {
    MarketDataSnapshotEntity original =
        mapper.toEntity(
            new TradeEvent(
                TS,
                new Trade(TS, TICKER, CLASS_CODE, new BigDecimal("1E-7"), 1, TradeDirection.SELL)));

    MarketDataSnapshotEntity restored = mapper.toEntity(mapper.toDomain(original));

    assertThat(restored.getTs()).isEqualTo(original.getTs());
    assertThat(restored.getKind()).isEqualTo(original.getKind());
    assertThat(restored.getTicker()).isEqualTo(original.getTicker());
    assertThat(restored.getClassCode()).isEqualTo(original.getClassCode());
    assertThat(restored.getPayload()).isEqualTo(original.getPayload());
    assertThat(restored.getPayload()).doesNotContainPattern("\\d[Ee][+-]?\\d");
    assertThat(restored.getPayload()).contains("0.0000001");
  }

  @Test
  void rejectsNullAndBlankInput() {
    assertThatThrownBy(() -> mapper.toEntity(null)).isInstanceOf(NullPointerException.class);
    assertThatThrownBy(() -> mapper.toDomain(null)).isInstanceOf(NullPointerException.class);
    assertThatThrownBy(
            () -> mapper.toDomain(new MarketDataSnapshotEntity(TS, " ", TICKER, CLASS_CODE, "{}")))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("kind");
    assertThatThrownBy(
            () ->
                mapper.toDomain(new MarketDataSnapshotEntity(TS, "QUOTE", TICKER, CLASS_CODE, " ")))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("payload");
  }

  @Test
  void unknownKindFailsWithClearMessage() {
    MarketDataSnapshotEntity entity =
        new MarketDataSnapshotEntity(TS, "UNKNOWN", TICKER, CLASS_CODE, "{}");

    assertThatThrownBy(() -> mapper.toDomain(entity))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("UNKNOWN");
  }

  @Test
  void brokenPayloadFailsWithClearMessage() {
    MarketDataSnapshotEntity entity =
        new MarketDataSnapshotEntity(TS, "QUOTE", TICKER, CLASS_CODE, "not-a-json");

    assertThatThrownBy(() -> mapper.toDomain(entity))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("QUOTE");
  }

  private static Quote quote() {
    return new Quote(
        TICKER,
        CLASS_CODE,
        TS,
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
}
