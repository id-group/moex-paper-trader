package ru.moex.papertrader.infra.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import ru.moex.papertrader.core.domain.Fill;
import ru.moex.papertrader.infra.model.FillEntity;

class FillMapperTest {

  private static final Instant EXECUTED_AT = Instant.parse("2026-11-02T10:00:00Z");
  private static final String ORDER_ID = "ORD-1";
  private static final String ACCOUNT = "PAPER-0001";
  private static final Long INSTRUMENT_ID = 42L;

  private final FillMapper mapper = new FillMapperImpl();

  @Test
  void mapsFillToEntityWithSchemaScalesAndInfraReferences() {
    Fill fill = new Fill(new BigDecimal("123.456789"), 10, new BigDecimal("1.50"), EXECUTED_AT);

    FillEntity entity = mapper.toEntity(fill, ORDER_ID, ACCOUNT, INSTRUMENT_ID);

    assertThat(entity.getOrderId()).isEqualTo(ORDER_ID);
    assertThat(entity.getAccountId()).isEqualTo(ACCOUNT);
    assertThat(entity.getInstrumentId()).isEqualTo(INSTRUMENT_ID);
    assertThat(entity.getPrice()).isEqualByComparingTo("123.456789");
    assertThat(entity.getPrice().scale()).isEqualTo(6);
    assertThat(entity.getQuantity()).isEqualTo(10);
    assertThat(entity.getCommission()).isEqualByComparingTo("1.50");
    assertThat(entity.getCommission().scale()).isEqualTo(2);
    assertThat(entity.getExecutedAt()).isEqualTo(EXECUTED_AT);
  }

  @Test
  void roundsCommissionToMoneyScale() {
    Fill fill = new Fill(new BigDecimal("100.000000"), 1, new BigDecimal("1.234"), EXECUTED_AT);

    FillEntity entity = mapper.toEntity(fill, ORDER_ID, ACCOUNT, INSTRUMENT_ID);

    assertThat(entity.getCommission()).isEqualByComparingTo("1.23");
    assertThat(entity.getCommission().scale()).isEqualTo(2);
  }

  @Test
  void mapsEntityToDomainDroppingInfraReferences() {
    FillEntity entity =
        new FillEntity(
            ORDER_ID,
            ACCOUNT,
            INSTRUMENT_ID,
            new BigDecimal("123.456789"),
            10,
            new BigDecimal("1.50"),
            EXECUTED_AT);

    Fill fill = mapper.toDomain(entity);

    assertThat(fill.price()).isEqualByComparingTo("123.456789");
    assertThat(fill.quantity()).isEqualTo(10);
    assertThat(fill.commission()).isEqualByComparingTo("1.50");
    assertThat(fill.executedAt()).isEqualTo(EXECUTED_AT);
  }

  @Test
  void roundTripsDomainToEntityToDomain() {
    Fill original = new Fill(new BigDecimal("123.456789"), 10, new BigDecimal("1.50"), EXECUTED_AT);

    Fill restored = mapper.toDomain(mapper.toEntity(original, ORDER_ID, ACCOUNT, INSTRUMENT_ID));

    assertThat(restored).isEqualTo(original);
  }

  @Test
  void roundTripsEntityToDomainToEntity() {
    FillEntity original =
        new FillEntity(
            ORDER_ID,
            ACCOUNT,
            INSTRUMENT_ID,
            new BigDecimal("123.456789"),
            10,
            new BigDecimal("1.50"),
            EXECUTED_AT);

    FillEntity restored =
        mapper.toEntity(mapper.toDomain(original), ORDER_ID, ACCOUNT, INSTRUMENT_ID);

    assertThat(restored.getOrderId()).isEqualTo(original.getOrderId());
    assertThat(restored.getAccountId()).isEqualTo(original.getAccountId());
    assertThat(restored.getInstrumentId()).isEqualTo(original.getInstrumentId());
    assertThat(restored.getPrice()).isEqualByComparingTo(original.getPrice());
    assertThat(restored.getQuantity()).isEqualTo(original.getQuantity());
    assertThat(restored.getCommission()).isEqualByComparingTo(original.getCommission());
    assertThat(restored.getExecutedAt()).isEqualTo(original.getExecutedAt());
  }
}
