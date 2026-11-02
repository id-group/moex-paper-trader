package ru.moex.papertrader.infra.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import ru.moex.papertrader.infra.model.AccountBalanceEntity;

class AccountBalanceMapperTest {

  private static final Instant UPDATED_AT = Instant.parse("2026-11-02T18:00:00Z");
  private static final String ACCOUNT = "PAPER-0001";

  private final AccountBalanceMapper mapper = new AccountBalanceMapperImpl();

  @Test
  void mapsEntityToDomain() {
    AccountBalanceEntity entity = entity("RUB", "1000.00", "100.00");

    PortfolioBalance balance = mapper.toDomain(entity);

    assertThat(balance.currency()).isEqualTo("RUB");
    assertThat(balance.cash()).isEqualByComparingTo("1000");
    assertThat(balance.locked()).isEqualByComparingTo("100");
  }

  @Test
  void mapsDomainToEntityWithMoneyScaleAndAccountParameter() {
    PortfolioBalance balance =
        new PortfolioBalance("USD", new BigDecimal("50"), new BigDecimal("5"));

    AccountBalanceEntity entity = mapper.toEntity(balance, ACCOUNT, UPDATED_AT);

    assertThat(entity.getAccountId()).isEqualTo(ACCOUNT);
    assertThat(entity.getCurrency()).isEqualTo("USD");
    assertThat(entity.getCash()).isEqualByComparingTo("50");
    assertThat(entity.getCash().scale()).isEqualTo(2);
    assertThat(entity.getLocked()).isEqualByComparingTo("5");
    assertThat(entity.getLocked().scale()).isEqualTo(2);
    assertThat(entity.getUpdatedAt()).isEqualTo(UPDATED_AT);
  }

  @Test
  void mapsEntityListToDomainList() {
    List<AccountBalanceEntity> entities =
        List.of(entity("RUB", "1000.00", "100.00"), entity("USD", "50.00", "0.00"));

    List<PortfolioBalance> balances = mapper.toDomain(entities);

    assertThat(balances).hasSize(2);
    assertThat(balances.get(0).currency()).isEqualTo("RUB");
    assertThat(balances.get(1).currency()).isEqualTo("USD");
  }

  @Test
  void roundTripsDomainToEntityToDomain() {
    PortfolioBalance original =
        new PortfolioBalance("RUB", new BigDecimal("1000.00"), new BigDecimal("100.00"));

    PortfolioBalance restored = mapper.toDomain(mapper.toEntity(original, ACCOUNT, UPDATED_AT));

    assertThat(restored).isEqualTo(original);
  }

  @Test
  void roundTripsEntityToDomainToEntity() {
    AccountBalanceEntity original = entity("RUB", "1000.00", "100.00");

    AccountBalanceEntity restored = mapper.toEntity(mapper.toDomain(original), ACCOUNT, UPDATED_AT);

    assertThat(restored.getAccountId()).isEqualTo(original.getAccountId());
    assertThat(restored.getCurrency()).isEqualTo(original.getCurrency());
    assertThat(restored.getCash()).isEqualByComparingTo(original.getCash());
    assertThat(restored.getLocked()).isEqualByComparingTo(original.getLocked());
    assertThat(restored.getUpdatedAt()).isEqualTo(original.getUpdatedAt());
  }

  private static AccountBalanceEntity entity(String currency, String cash, String locked) {
    return new AccountBalanceEntity(
        ACCOUNT, currency, new BigDecimal(cash), new BigDecimal(locked), UPDATED_AT);
  }
}
