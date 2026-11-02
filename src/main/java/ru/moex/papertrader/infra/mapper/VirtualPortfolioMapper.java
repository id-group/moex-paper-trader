package ru.moex.papertrader.infra.mapper;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import org.springframework.stereotype.Component;
import ru.moex.papertrader.core.domain.Instrument;
import ru.moex.papertrader.core.portfolio.PortfolioPosition;
import ru.moex.papertrader.core.portfolio.VirtualPortfolio;
import ru.moex.papertrader.infra.model.AccountBalanceEntity;
import ru.moex.papertrader.infra.model.PositionEntity;

/**
 * Сборка состояния {@link VirtualPortfolio} из сущностей БД и обратно.
 *
 * <p>Маппер не обращается к репозиториям: соответствие {@code instrument_id → Instrument} и {@code
 * Instrument → instrument_id} передаётся вызывающим сервисом.
 */
@Component
public class VirtualPortfolioMapper {

  private final AccountBalanceMapper balanceMapper;
  private final PositionMapper positionMapper;

  public VirtualPortfolioMapper(AccountBalanceMapper balanceMapper, PositionMapper positionMapper) {
    this.balanceMapper = Objects.requireNonNull(balanceMapper, "balanceMapper");
    this.positionMapper = Objects.requireNonNull(positionMapper, "positionMapper");
  }

  /**
   * Восстанавливает портфель: балансы по валютам, блокировки и открытые позиции.
   *
   * @param balances строки {@code account_balances} одного счёта
   * @param positions строки {@code positions} одного счёта
   * @param instrumentsById соответствие {@code instrument_id → инструмент}
   */
  public VirtualPortfolio toDomain(
      List<AccountBalanceEntity> balances,
      List<PositionEntity> positions,
      Map<Long, Instrument> instrumentsById) {
    return build(balances, positions, instrumentsById, false);
  }

  /**
   * Восстанавливает портфель для клиринга: допускает отрицательный кэш и ГО больше остатка.
   *
   * <p>После убыточной вариационной маржи обычная {@code restore} отвергает снимок, поэтому клиринг
   * использует отдельную фабрику {@link VirtualPortfolio#restoreForSettlement}.
   */
  public VirtualPortfolio toDomainAllowNegativeCash(
      List<AccountBalanceEntity> balances,
      List<PositionEntity> positions,
      Map<Long, Instrument> instrumentsById) {
    return build(balances, positions, instrumentsById, true);
  }

  private VirtualPortfolio build(
      List<AccountBalanceEntity> balances,
      List<PositionEntity> positions,
      Map<Long, Instrument> instrumentsById,
      boolean allowNegativeCash) {
    Objects.requireNonNull(balances, "balances");
    Objects.requireNonNull(positions, "positions");
    Objects.requireNonNull(instrumentsById, "instrumentsById");

    Map<String, BigDecimal> cashByCurrency = new LinkedHashMap<>();
    Map<String, BigDecimal> lockedByCurrency = new LinkedHashMap<>();
    for (AccountBalanceEntity entity : balances) {
      PortfolioBalance balance = balanceMapper.toDomain(entity);
      if (cashByCurrency.putIfAbsent(balance.currency(), balance.cash()) != null) {
        throw new IllegalArgumentException("Дубликат баланса по валюте " + balance.currency());
      }
      lockedByCurrency.put(balance.currency(), balance.locked());
    }

    List<PortfolioPosition> domainPositions = new ArrayList<>();
    Set<String> seenInstruments = new HashSet<>();
    for (PositionEntity entity : positions) {
      Instrument instrument = instrumentsById.get(entity.getInstrumentId());
      if (instrument == null) {
        throw new IllegalArgumentException(
            "Нет инструмента с id " + entity.getInstrumentId() + " для позиции");
      }
      String key = instrumentKey(instrument);
      if (!seenInstruments.add(key)) {
        throw new IllegalArgumentException("Дубликат позиции " + key);
      }
      domainPositions.add(positionMapper.toDomain(entity, instrument));
    }
    return allowNegativeCash
        ? VirtualPortfolio.restoreForSettlement(cashByCurrency, lockedByCurrency, domainPositions)
        : VirtualPortfolio.restore(cashByCurrency, lockedByCurrency, domainPositions);
  }

  /** Строки {@code account_balances} по всем валютам портфеля (в алфавитном порядке). */
  public List<AccountBalanceEntity> toBalanceEntities(
      VirtualPortfolio portfolio, String accountId, Instant updatedAt) {
    Objects.requireNonNull(portfolio, "portfolio");
    TreeSet<String> currencies = new TreeSet<>(portfolio.cashByCurrency().keySet());
    currencies.addAll(portfolio.lockedCashByCurrency().keySet());
    List<AccountBalanceEntity> entities = new ArrayList<>(currencies.size());
    for (String currency : currencies) {
      PortfolioBalance balance =
          new PortfolioBalance(currency, portfolio.cash(currency), portfolio.lockedCash(currency));
      entities.add(balanceMapper.toEntity(balance, accountId, updatedAt));
    }
    return List.copyOf(entities);
  }

  /**
   * Строки {@code positions} по открытым позициям портфеля.
   *
   * @param instrumentIdsByKey соответствие {@code ticker|classCode → instrument_id}
   */
  public List<PositionEntity> toPositionEntities(
      VirtualPortfolio portfolio,
      String accountId,
      Map<String, Long> instrumentIdsByKey,
      Instant updatedAt) {
    Objects.requireNonNull(portfolio, "portfolio");
    Objects.requireNonNull(instrumentIdsByKey, "instrumentIdsByKey");
    List<PositionEntity> entities = new ArrayList<>();
    for (PortfolioPosition position : portfolio.positions()) {
      String key = instrumentKey(position.instrument());
      Long instrumentId = instrumentIdsByKey.get(key);
      if (instrumentId == null) {
        throw new IllegalArgumentException("Нет instrument_id для инструмента " + key);
      }
      entities.add(positionMapper.toEntity(position, accountId, instrumentId, updatedAt));
    }
    return List.copyOf(entities);
  }

  /** Ключ инструмента в инфраструктурных соответствиях: {@code ticker|classCode}. */
  public static String instrumentKey(Instrument instrument) {
    Objects.requireNonNull(instrument, "instrument");
    return instrument.ticker() + "|" + instrument.classCode();
  }
}
