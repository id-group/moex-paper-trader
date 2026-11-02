package ru.moex.papertrader.infra.mapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import ru.moex.papertrader.TestInstruments;
import ru.moex.papertrader.core.domain.Instrument;
import ru.moex.papertrader.core.portfolio.PortfolioPosition;
import ru.moex.papertrader.core.portfolio.VirtualPortfolio;
import ru.moex.papertrader.infra.model.AccountBalanceEntity;
import ru.moex.papertrader.infra.model.PositionEntity;

class VirtualPortfolioMapperTest {

  private static final Instant UPDATED_AT = Instant.parse("2026-11-02T18:00:00Z");
  private static final String ACCOUNT = "PAPER-0001";
  private static final Instrument SBER = TestInstruments.sber();
  private static final Instrument GAZP = TestInstruments.gazp();

  private final VirtualPortfolioMapper mapper =
      new VirtualPortfolioMapper(new AccountBalanceMapperImpl(), new PositionMapperImpl());

  @Test
  void restoresPortfolioWithCashLocksAndPositions() {
    VirtualPortfolio portfolio =
        mapper.toDomain(
            List.of(balance("RUB", "1000.00", "100.00"), balance("USD", "50.00", "0.00")),
            List.of(
                new PositionEntity(ACCOUNT, 1L, 10, new BigDecimal("100.500000"), UPDATED_AT),
                new PositionEntity(ACCOUNT, 2L, -5, new BigDecimal("200.250000"), UPDATED_AT)),
            Map.of(1L, SBER, 2L, GAZP));

    assertThat(portfolio.cash("RUB")).isEqualByComparingTo("1000");
    assertThat(portfolio.lockedCash("RUB")).isEqualByComparingTo("100");
    assertThat(portfolio.cash("USD")).isEqualByComparingTo("50");
    assertThat(portfolio.lockedCash("USD")).isEqualByComparingTo("0");
    assertThat(portfolio.positions()).hasSize(2);
    assertThat(portfolio.position(SBER).orElseThrow().quantity()).isEqualTo(10);
    assertThat(portfolio.position(SBER).orElseThrow().averagePrice()).isEqualByComparingTo("100.5");
    assertThat(portfolio.position(GAZP).orElseThrow().quantity()).isEqualTo(-5);
  }

  @Test
  void restoresPortfolioWithoutPositions() {
    VirtualPortfolio portfolio =
        mapper.toDomain(List.of(balance("RUB", "10.00", "0.00")), List.of(), Map.of());

    assertThat(portfolio.positions()).isEmpty();
    assertThat(portfolio.cash()).isEqualByComparingTo("10");
  }

  @Test
  void rejectsPositionWithUnknownInstrumentId() {
    List<PositionEntity> positions =
        List.of(new PositionEntity(ACCOUNT, 2L, 1, new BigDecimal("100.500000"), UPDATED_AT));

    assertThatThrownBy(
            () -> mapper.toDomain(List.of(balance("RUB", "10.00", "0.00")), positions, Map.of()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("2");
  }

  @Test
  void rejectsDuplicateCurrencyBalances() {
    List<AccountBalanceEntity> balances =
        List.of(balance("RUB", "10.00", "0.00"), balance("RUB", "20.00", "0.00"));

    assertThatThrownBy(() -> mapper.toDomain(balances, List.of(), Map.of()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("RUB");
  }

  @Test
  void rejectsDuplicatePositionsForSameInstrument() {
    List<PositionEntity> positions =
        List.of(
            new PositionEntity(ACCOUNT, 1L, 1, new BigDecimal("100.500000"), UPDATED_AT),
            new PositionEntity(ACCOUNT, 1L, 2, new BigDecimal("101.500000"), UPDATED_AT));

    assertThatThrownBy(
            () ->
                mapper.toDomain(
                    List.of(balance("RUB", "1000.00", "0.00")), positions, Map.of(1L, SBER)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("SBER");
  }

  @Test
  void mapsPortfolioToEntities() {
    VirtualPortfolio portfolio = portfolioWithPosition();

    List<AccountBalanceEntity> balances = mapper.toBalanceEntities(portfolio, ACCOUNT, UPDATED_AT);
    List<PositionEntity> positions =
        mapper.toPositionEntities(
            portfolio, ACCOUNT, Map.of(VirtualPortfolioMapper.instrumentKey(SBER), 1L), UPDATED_AT);

    assertThat(balances).hasSize(1);
    assertThat(balances.get(0).getAccountId()).isEqualTo(ACCOUNT);
    assertThat(balances.get(0).getCurrency()).isEqualTo("RUB");
    assertThat(balances.get(0).getCash()).isEqualByComparingTo("1000");
    assertThat(balances.get(0).getCash().scale()).isEqualTo(2);
    assertThat(balances.get(0).getLocked()).isEqualByComparingTo("100");
    assertThat(positions).hasSize(1);
    assertThat(positions.get(0).getAccountId()).isEqualTo(ACCOUNT);
    assertThat(positions.get(0).getInstrumentId()).isEqualTo(1L);
    assertThat(positions.get(0).getQuantity()).isEqualTo(10);
    assertThat(positions.get(0).getAveragePrice()).isEqualByComparingTo("100.5");
    assertThat(positions.get(0).getAveragePrice().scale()).isEqualTo(6);
    assertThat(positions.get(0).getUpdatedAt()).isEqualTo(UPDATED_AT);
  }

  @Test
  void roundTripsPortfolioThroughEntities() {
    VirtualPortfolio original = portfolioWithPosition();

    VirtualPortfolio restored =
        mapper.toDomain(
            mapper.toBalanceEntities(original, ACCOUNT, UPDATED_AT),
            mapper.toPositionEntities(
                original,
                ACCOUNT,
                Map.of(VirtualPortfolioMapper.instrumentKey(SBER), 1L),
                UPDATED_AT),
            Map.of(1L, SBER));

    assertThat(restored.cash("RUB")).isEqualByComparingTo(original.cash("RUB"));
    assertThat(restored.lockedCash("RUB")).isEqualByComparingTo(original.lockedCash("RUB"));
    assertThat(restored.positions()).hasSize(1);
    PortfolioPosition position = restored.positions().get(0);
    assertThat(position.instrument()).isEqualTo(SBER);
    assertThat(position.quantity()).isEqualTo(10);
    assertThat(position.averagePrice()).isEqualByComparingTo("100.5");
  }

  @Test
  void rejectsPositionWithoutInstrumentIdMapping() {
    VirtualPortfolio portfolio = portfolioWithPosition();

    assertThatThrownBy(() -> mapper.toPositionEntities(portfolio, ACCOUNT, Map.of(), UPDATED_AT))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("SBER");
  }

  private static VirtualPortfolio portfolioWithPosition() {
    return VirtualPortfolio.restore(
        Map.of("RUB", new BigDecimal("1000.00")),
        Map.of("RUB", new BigDecimal("100.00")),
        List.of(new PortfolioPosition(SBER, 10, new BigDecimal("100.500000"))));
  }

  private static AccountBalanceEntity balance(String currency, String cash, String locked) {
    return new AccountBalanceEntity(
        ACCOUNT, currency, new BigDecimal(cash), new BigDecimal(locked), UPDATED_AT);
  }
}
