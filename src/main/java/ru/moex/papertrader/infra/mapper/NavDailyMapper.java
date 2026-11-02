package ru.moex.papertrader.infra.mapper;

import java.time.LocalDate;
import java.util.Objects;
import org.springframework.stereotype.Component;
import ru.moex.papertrader.core.portfolio.NavSnapshot;
import ru.moex.papertrader.infra.model.NavDailyEntity;

/**
 * Маппинг ежедневного NAV: сущность {@code nav_daily} ↔ доменный {@link NavSnapshot}.
 *
 * <p>Ручной маппер: поле {@code holdings} хранится JSON-строкой и кодируется отдельным {@link
 * NavHoldingsCodec}. День среза ({@code nav_date}) и счёт — данные слоя БД, передаются параметрами.
 */
@Component
public class NavDailyMapper {

  private final NavHoldingsCodec holdingsCodec;

  public NavDailyMapper() {
    this(new NavHoldingsCodec());
  }

  NavDailyMapper(NavHoldingsCodec holdingsCodec) {
    this.holdingsCodec = Objects.requireNonNull(holdingsCodec, "holdingsCodec");
  }

  /** Создаёт сущность без {@code id}: первичный ключ назначает БД. */
  public NavDailyEntity toEntity(NavSnapshot snapshot, String accountId, LocalDate navDate) {
    Objects.requireNonNull(snapshot, "snapshot");
    requireText(accountId, "accountId");
    Objects.requireNonNull(navDate, "navDate");
    return new NavDailyEntity(
        accountId,
        navDate,
        snapshot.asOf(),
        MappingScales.money(snapshot.cash()),
        MappingScales.money(snapshot.commission()),
        MappingScales.money(snapshot.variationMargin()),
        MappingScales.money(snapshot.marketValue()),
        MappingScales.money(snapshot.nav()),
        holdingsCodec.encode(snapshot.holdings()));
  }

  public NavSnapshot toDomain(NavDailyEntity entity) {
    Objects.requireNonNull(entity, "entity");
    return new NavSnapshot(
        entity.getAsOf(),
        entity.getCash(),
        entity.getCommission(),
        entity.getVariationMargin(),
        entity.getMarketValue(),
        entity.getNav(),
        holdingsCodec.decode(entity.getHoldingsJson()));
  }

  private static void requireText(String value, String name) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(name + " не может быть пустым");
    }
  }
}
