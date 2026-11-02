package ru.moex.papertrader.infra.mapper;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import org.mapstruct.ReportingPolicy;
import ru.moex.papertrader.infra.model.AccountBalanceEntity;

/**
 * Маппинг баланса счёта: сущность {@code account_balances} ↔ состояние портфеля по валютам.
 *
 * <p>Деньги в БД хранятся с масштабом 2; к этому масштабу значения приводятся при сохранении.
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface AccountBalanceMapper {

  PortfolioBalance toDomain(AccountBalanceEntity entity);

  List<PortfolioBalance> toDomain(List<AccountBalanceEntity> entities);

  /** Создаёт сущность без {@code id}: первичный ключ назначает БД. */
  @Mapping(target = "id", ignore = true)
  @Mapping(target = "accountId", source = "accountId")
  @Mapping(target = "currency", source = "balance.currency")
  @Mapping(target = "cash", source = "balance.cash", qualifiedByName = "moneyScale")
  @Mapping(target = "locked", source = "balance.locked", qualifiedByName = "moneyScale")
  @Mapping(target = "updatedAt", source = "updatedAt")
  AccountBalanceEntity toEntity(PortfolioBalance balance, String accountId, Instant updatedAt);

  @Named("moneyScale")
  default BigDecimal moneyScale(BigDecimal value) {
    return MappingScales.money(value);
  }
}
