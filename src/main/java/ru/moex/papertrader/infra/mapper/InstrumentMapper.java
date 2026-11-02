package ru.moex.papertrader.infra.mapper;

import java.math.BigDecimal;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import org.mapstruct.ReportingPolicy;
import ru.moex.papertrader.core.commission.InstrumentType;
import ru.moex.papertrader.core.domain.Instrument;
import ru.moex.papertrader.infra.model.InstrumentEntity;

/** Маппинг инструмента: сущность {@code instruments} ↔ доменный {@link Instrument}. */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface InstrumentMapper {

  /**
   * Восстанавливает доменный инструмент; тип проверяется по {@link InstrumentType}, чтобы
   * неизвестное значение в БД не превращалось в молчаливый {@code null}.
   */
  @Mapping(target = "type", source = "type", qualifiedByName = "instrumentType")
  Instrument toDomain(InstrumentEntity entity);

  /** Создаёт сущность без {@code id}: первичный ключ назначает БД. */
  @Mapping(target = "id", ignore = true)
  @Mapping(target = "lotSize", source = "lotSize", qualifiedByName = "priceScale")
  @Mapping(target = "minimumStep", source = "minimumStep", qualifiedByName = "priceScale")
  InstrumentEntity toEntity(Instrument instrument);

  @Named("instrumentType")
  default String instrumentType(String rawType) {
    if (rawType == null || rawType.isBlank()) {
      throw new IllegalArgumentException("Тип инструмента не задан");
    }
    try {
      return InstrumentType.valueOf(rawType.trim()).name();
    } catch (IllegalArgumentException exception) {
      throw new IllegalArgumentException("Неизвестный тип инструмента: " + rawType, exception);
    }
  }

  @Named("priceScale")
  default BigDecimal priceScale(BigDecimal value) {
    return MappingScales.price(value);
  }
}
