package ru.moex.papertrader.infra.mapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import ru.moex.papertrader.core.portfolio.NavSnapshot;
import ru.moex.papertrader.infra.model.NavDailyEntity;

class NavDailyMapperTest {

  private static final Instant AS_OF = Instant.parse("2026-11-02T18:00:00Z");
  private static final LocalDate NAV_DATE = LocalDate.of(2026, 11, 2);
  private static final String ACCOUNT = "PAPER-0001";

  private final NavDailyMapper mapper = new NavDailyMapper();

  @Test
  void mapsSnapshotToEntityWithMoneyScaleAndJsonHoldings() {
    NavSnapshot snapshot =
        snapshot(
            List.of(
                new NavSnapshot.Holding("SBER", 10, new BigDecimal("100.500000")),
                new NavSnapshot.Holding("GAZP", -5, new BigDecimal("200.250000"))));

    NavDailyEntity entity = mapper.toEntity(snapshot, ACCOUNT, NAV_DATE);

    assertThat(entity.getAccountId()).isEqualTo(ACCOUNT);
    assertThat(entity.getNavDate()).isEqualTo(NAV_DATE);
    assertThat(entity.getAsOf()).isEqualTo(AS_OF);
    assertThat(entity.getCash()).isEqualByComparingTo("1000");
    assertThat(entity.getCash().scale()).isEqualTo(2);
    assertThat(entity.getCommission().scale()).isEqualTo(2);
    assertThat(entity.getVariationMargin().scale()).isEqualTo(2);
    assertThat(entity.getMarketValue().scale()).isEqualTo(2);
    assertThat(entity.getNav().scale()).isEqualTo(2);
    assertThat(entity.getHoldingsJson()).contains("SBER", "GAZP", "100.500000");
  }

  @Test
  void mapsEntityToDomainRestoringHoldings() {
    NavSnapshot original =
        snapshot(List.of(new NavSnapshot.Holding("SBER", 10, new BigDecimal("100.500000"))));
    NavDailyEntity entity = mapper.toEntity(original, ACCOUNT, NAV_DATE);

    NavSnapshot restored = mapper.toDomain(entity);

    assertThat(restored).isEqualTo(original);
  }

  @Test
  void roundTripsSnapshotWithManyHoldings() {
    NavSnapshot original =
        snapshot(
            List.of(
                new NavSnapshot.Holding("SBER", 10, new BigDecimal("100.500000")),
                new NavSnapshot.Holding("GAZP", -5, new BigDecimal("200.250000")),
                new NavSnapshot.Holding("LKOH", 1, new BigDecimal("6500.120000"))));

    assertThat(mapper.toDomain(mapper.toEntity(original, ACCOUNT, NAV_DATE))).isEqualTo(original);
  }

  @Test
  void roundTripsSnapshotWithEmptyHoldings() {
    NavSnapshot original = snapshot(List.of());

    NavDailyEntity entity = mapper.toEntity(original, ACCOUNT, NAV_DATE);

    assertThat(entity.getHoldingsJson()).isEqualTo("[]");
    assertThat(mapper.toDomain(entity)).isEqualTo(original);
  }

  @Test
  void roundTripsEntityToDomainToEntity() {
    NavDailyEntity original =
        mapper.toEntity(
            snapshot(List.of(new NavSnapshot.Holding("SBER", 10, new BigDecimal("100.500000")))),
            ACCOUNT,
            NAV_DATE);

    NavDailyEntity restored = mapper.toEntity(mapper.toDomain(original), ACCOUNT, NAV_DATE);

    assertThat(restored.getAccountId()).isEqualTo(original.getAccountId());
    assertThat(restored.getNavDate()).isEqualTo(original.getNavDate());
    assertThat(restored.getAsOf()).isEqualTo(original.getAsOf());
    assertThat(restored.getCash()).isEqualByComparingTo(original.getCash());
    assertThat(restored.getCommission()).isEqualByComparingTo(original.getCommission());
    assertThat(restored.getVariationMargin()).isEqualByComparingTo(original.getVariationMargin());
    assertThat(restored.getMarketValue()).isEqualByComparingTo(original.getMarketValue());
    assertThat(restored.getNav()).isEqualByComparingTo(original.getNav());
  }

  @Test
  void writesNumbersWithoutScientificNotationAndKeepsScale() {
    NavSnapshot snapshot =
        snapshot(
            List.of(
                new NavSnapshot.Holding("SBER", 10, new BigDecimal("100.500000")),
                new NavSnapshot.Holding("TINY", 1, new BigDecimal("1E-7"))));

    String json = mapper.toEntity(snapshot, ACCOUNT, NAV_DATE).getHoldingsJson();

    assertThat(json).doesNotContainPattern("\\d[Ee][+-]?\\d");
    assertThat(json).contains("100.500000", "0.0000001");

    NavSnapshot restored = mapper.toDomain(mapper.toEntity(snapshot, ACCOUNT, NAV_DATE));
    assertThat(restored.holdings().get(0).closingPrice().scale()).isEqualTo(6);
    assertThat(restored.holdings().get(1).closingPrice().scale()).isEqualTo(7);
  }

  @Test
  void invalidHoldingsJsonFailsWithClearMessage() {
    NavDailyEntity entity =
        new NavDailyEntity(
            ACCOUNT,
            NAV_DATE,
            AS_OF,
            new BigDecimal("1000.00"),
            new BigDecimal("1.50"),
            new BigDecimal("-20.00"),
            new BigDecimal("500.00"),
            new BigDecimal("1478.50"),
            "not-a-json");

    assertThatThrownBy(() -> mapper.toDomain(entity))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("holdings");
  }

  private static NavSnapshot snapshot(List<NavSnapshot.Holding> holdings) {
    return new NavSnapshot(
        AS_OF,
        new BigDecimal("1000.00"),
        new BigDecimal("1.50"),
        new BigDecimal("-20.00"),
        new BigDecimal("500.00"),
        new BigDecimal("1478.50"),
        holdings);
  }
}
