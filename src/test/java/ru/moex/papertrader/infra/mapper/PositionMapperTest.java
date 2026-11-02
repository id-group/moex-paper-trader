package ru.moex.papertrader.infra.mapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import ru.moex.papertrader.TestInstruments;
import ru.moex.papertrader.core.domain.Instrument;
import ru.moex.papertrader.core.portfolio.PortfolioPosition;
import ru.moex.papertrader.infra.model.PositionEntity;

class PositionMapperTest {

  private static final Instant UPDATED_AT = Instant.parse("2026-11-02T18:00:00Z");
  private static final String ACCOUNT = "PAPER-0001";
  private static final Long INSTRUMENT_ID = 42L;
  private static final Instrument SBER = TestInstruments.sber();

  private final PositionMapper mapper = new PositionMapperImpl();

  @Test
  void mapsLongPositionToEntity() {
    PortfolioPosition position = new PortfolioPosition(SBER, 10, new BigDecimal("100.500000"));

    PositionEntity entity = mapper.toEntity(position, ACCOUNT, INSTRUMENT_ID, UPDATED_AT);

    assertThat(entity.getAccountId()).isEqualTo(ACCOUNT);
    assertThat(entity.getInstrumentId()).isEqualTo(INSTRUMENT_ID);
    assertThat(entity.getQuantity()).isEqualTo(10);
    assertThat(entity.getAveragePrice()).isEqualByComparingTo("100.5");
    assertThat(entity.getAveragePrice().scale()).isEqualTo(6);
    assertThat(entity.getUpdatedAt()).isEqualTo(UPDATED_AT);
  }

  @Test
  void mapsShortPositionToEntityWithNegativeQuantity() {
    PortfolioPosition position = new PortfolioPosition(SBER, -5, new BigDecimal("100.500000"));

    PositionEntity entity = mapper.toEntity(position, ACCOUNT, INSTRUMENT_ID, UPDATED_AT);

    assertThat(entity.getQuantity()).isEqualTo(-5);
  }

  @Test
  void mapsEntityToDomainWithInstrumentParameter() {
    PositionEntity entity =
        new PositionEntity(ACCOUNT, INSTRUMENT_ID, -5, new BigDecimal("100.500000"), UPDATED_AT);

    PortfolioPosition position = mapper.toDomain(entity, SBER);

    assertThat(position.instrument()).isEqualTo(SBER);
    assertThat(position.quantity()).isEqualTo(-5);
    assertThat(position.averagePrice()).isEqualByComparingTo("100.5");
  }

  @Test
  void rejectsZeroQuantityFromDatabase() {
    PositionEntity entity =
        new PositionEntity(ACCOUNT, INSTRUMENT_ID, 0, new BigDecimal("100.500000"), UPDATED_AT);

    assertThatThrownBy(() -> mapper.toDomain(entity, SBER))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("нулевое количество");
  }

  @Test
  void roundTripsDomainToEntityToDomain() {
    PortfolioPosition original = new PortfolioPosition(SBER, -5, new BigDecimal("100.500000"));

    PortfolioPosition restored =
        mapper.toDomain(mapper.toEntity(original, ACCOUNT, INSTRUMENT_ID, UPDATED_AT), SBER);

    assertThat(restored).isEqualTo(original);
  }

  @Test
  void roundTripsEntityToDomainToEntity() {
    PositionEntity original =
        new PositionEntity(ACCOUNT, INSTRUMENT_ID, -5, new BigDecimal("100.500000"), UPDATED_AT);

    PositionEntity restored =
        mapper.toEntity(mapper.toDomain(original, SBER), ACCOUNT, INSTRUMENT_ID, UPDATED_AT);

    assertThat(restored.getAccountId()).isEqualTo(original.getAccountId());
    assertThat(restored.getInstrumentId()).isEqualTo(original.getInstrumentId());
    assertThat(restored.getQuantity()).isEqualTo(original.getQuantity());
    assertThat(restored.getAveragePrice()).isEqualByComparingTo(original.getAveragePrice());
    assertThat(restored.getUpdatedAt()).isEqualTo(original.getUpdatedAt());
  }
}
