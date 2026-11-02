package ru.moex.papertrader.infra.mapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import ru.moex.papertrader.core.domain.Instrument;
import ru.moex.papertrader.infra.model.InstrumentEntity;

class InstrumentMapperTest {

  private static final Instrument SBER =
      new Instrument(
          "SBER",
          "TQBR",
          "STOCK",
          "Сбербанк",
          "Сбербанк",
          "RU0009029540",
          new BigDecimal("10.000000"),
          new BigDecimal("0.010000"),
          "RUB");

  private final InstrumentMapper mapper = new InstrumentMapperImpl();

  @Test
  void mapsEntityToDomainAllFields() {
    Instrument instrument = mapper.toDomain(entity("STOCK"));

    assertThat(instrument).isEqualTo(SBER);
  }

  @Test
  void mapsDomainToEntityAllFields() {
    InstrumentEntity entity = mapper.toEntity(SBER);

    assertThat(entity.getTicker()).isEqualTo("SBER");
    assertThat(entity.getClassCode()).isEqualTo("TQBR");
    assertThat(entity.getType()).isEqualTo("STOCK");
    assertThat(entity.getShortName()).isEqualTo("Сбербанк");
    assertThat(entity.getDisplayName()).isEqualTo("Сбербанк");
    assertThat(entity.getIsin()).isEqualTo("RU0009029540");
    assertThat(entity.getLotSize()).isEqualByComparingTo("10");
    assertThat(entity.getMinimumStep()).isEqualByComparingTo("0.01");
    assertThat(entity.getCurrency()).isEqualTo("RUB");
    assertThat(entity.getLotSize().scale()).isEqualTo(6);
    assertThat(entity.getMinimumStep().scale()).isEqualTo(6);
  }

  @Test
  void mapsDomainToEntityNormalizingPriceScale() {
    Instrument raw =
        new Instrument(
            "GAZP",
            "TQBR",
            "STOCK",
            "Газпром",
            "Газпром",
            "RU0007661625",
            new BigDecimal("10"),
            new BigDecimal("0.01"),
            "RUB");

    InstrumentEntity entity = mapper.toEntity(raw);

    assertThat(entity.getLotSize()).isEqualByComparingTo("10");
    assertThat(entity.getLotSize().scale()).isEqualTo(6);
    assertThat(entity.getMinimumStep().scale()).isEqualTo(6);
  }

  @Test
  void roundTripsDomainToEntityToDomain() {
    Instrument restored = mapper.toDomain(mapper.toEntity(SBER));

    assertThat(restored).isEqualTo(SBER);
  }

  @Test
  void roundTripsEntityToDomainToEntity() {
    InstrumentEntity original = entity("STOCK");

    InstrumentEntity restored = mapper.toEntity(mapper.toDomain(original));

    assertThat(restored.getTicker()).isEqualTo(original.getTicker());
    assertThat(restored.getClassCode()).isEqualTo(original.getClassCode());
    assertThat(restored.getType()).isEqualTo(original.getType());
    assertThat(restored.getShortName()).isEqualTo(original.getShortName());
    assertThat(restored.getDisplayName()).isEqualTo(original.getDisplayName());
    assertThat(restored.getIsin()).isEqualTo(original.getIsin());
    assertThat(restored.getLotSize()).isEqualByComparingTo(original.getLotSize());
    assertThat(restored.getMinimumStep()).isEqualByComparingTo(original.getMinimumStep());
    assertThat(restored.getCurrency()).isEqualTo(original.getCurrency());
  }

  @Test
  void unknownInstrumentTypeFailsWithClearMessage() {
    InstrumentEntity entity = entity("BOND");

    assertThatThrownBy(() -> mapper.toDomain(entity))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("BOND");
  }

  private static InstrumentEntity entity(String type) {
    return new InstrumentEntity(
        "SBER",
        "TQBR",
        type,
        "Сбербанк",
        "Сбербанк",
        "RU0009029540",
        new BigDecimal("10.000000"),
        new BigDecimal("0.010000"),
        "RUB");
  }
}
