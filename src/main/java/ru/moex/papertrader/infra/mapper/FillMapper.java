package ru.moex.papertrader.infra.mapper;

import java.math.BigDecimal;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import org.mapstruct.ReportingPolicy;
import ru.moex.papertrader.core.domain.Fill;
import ru.moex.papertrader.infra.model.FillEntity;

/**
 * Маппинг сделки: сущность {@code fills} ↔ доменный {@link Fill}.
 *
 * <p>Домен четырёхполевой; {@code order_id}, {@code account_id} и {@code instrument_id} — ссылки
 * слоя БД, они передаются параметрами при сохранении и отбрасываются при чтении.
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface FillMapper {

  /** Создаёт сущность без {@code id}: первичный ключ назначает БД. */
  @Mapping(target = "id", ignore = true)
  @Mapping(target = "orderId", source = "orderId")
  @Mapping(target = "accountId", source = "accountId")
  @Mapping(target = "instrumentId", source = "instrumentId")
  @Mapping(target = "price", source = "fill.price", qualifiedByName = "priceScale")
  @Mapping(target = "quantity", source = "fill.quantity")
  @Mapping(target = "commission", source = "fill.commission", qualifiedByName = "moneyScale")
  @Mapping(target = "executedAt", source = "fill.executedAt")
  FillEntity toEntity(Fill fill, String orderId, String accountId, Long instrumentId);

  /** Восстанавливает доменную сделку; ссылки на заявку/счёт/инструмент не нужны. */
  Fill toDomain(FillEntity entity);

  @Named("priceScale")
  default BigDecimal priceScale(BigDecimal value) {
    return MappingScales.price(value);
  }

  @Named("moneyScale")
  default BigDecimal moneyScale(BigDecimal value) {
    return MappingScales.money(value);
  }
}
