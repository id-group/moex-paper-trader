package ru.moex.papertrader.infra.mapper;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import org.mapstruct.ReportingPolicy;
import ru.moex.papertrader.core.domain.Instrument;
import ru.moex.papertrader.core.portfolio.PortfolioPosition;
import ru.moex.papertrader.infra.model.PositionEntity;

/**
 * Маппинг позиции: сущность {@code positions} ↔ доменный {@link PortfolioPosition}.
 *
 * <p>Инструмент, счёт и время обновления — данные слоя БД; они передаются параметрами, маппер в
 * репозитории не ходит.
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface PositionMapper {

  /** Создаёт сущность без {@code id}: первичный ключ назначает БД. */
  @Mapping(target = "id", ignore = true)
  @Mapping(target = "accountId", source = "accountId")
  @Mapping(target = "instrumentId", source = "instrumentId")
  @Mapping(target = "quantity", source = "position.quantity")
  @Mapping(
      target = "averagePrice",
      source = "position.averagePrice",
      qualifiedByName = "priceScale")
  @Mapping(target = "updatedAt", source = "updatedAt")
  PositionEntity toEntity(
      PortfolioPosition position, String accountId, Long instrumentId, Instant updatedAt);

  /**
   * Восстанавливает позицию. Схема БД запрещает нулевое количество ({@code CHECK quantity <> 0}),
   * поэтому такая строка считается повреждёнными данными и отвергается явно.
   */
  default PortfolioPosition toDomain(PositionEntity entity, Instrument instrument) {
    Objects.requireNonNull(entity, "entity");
    Objects.requireNonNull(instrument, "instrument");
    if (entity.getQuantity() == 0) {
      throw new IllegalArgumentException(
          "Позиция по инструменту " + instrument.ticker() + " имеет нулевое количество");
    }
    return mapToDomain(entity, instrument);
  }

  @Mapping(target = "instrument", source = "instrument")
  @Mapping(target = "quantity", source = "entity.quantity")
  @Mapping(target = "averagePrice", source = "entity.averagePrice")
  PortfolioPosition mapToDomain(PositionEntity entity, Instrument instrument);

  @Named("priceScale")
  default BigDecimal priceScale(BigDecimal value) {
    return MappingScales.price(value);
  }
}
