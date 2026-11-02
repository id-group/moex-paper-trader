package ru.moex.papertrader.infra.services;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.moex.papertrader.core.domain.Instrument;
import ru.moex.papertrader.core.domain.PortfolioSnapshot;
import ru.moex.papertrader.core.domain.Position;
import ru.moex.papertrader.core.marketdata.MarketDataProvider;
import ru.moex.papertrader.core.marketdata.Quote;
import ru.moex.papertrader.core.portfolio.Money;
import ru.moex.papertrader.core.portfolio.PortfolioPosition;
import ru.moex.papertrader.infra.mapper.AccountBalanceMapper;
import ru.moex.papertrader.infra.mapper.InstrumentMapper;
import ru.moex.papertrader.infra.mapper.PortfolioBalance;
import ru.moex.papertrader.infra.mapper.PositionMapper;
import ru.moex.papertrader.infra.model.AccountBalanceEntity;
import ru.moex.papertrader.infra.model.InstrumentEntity;
import ru.moex.papertrader.infra.model.PositionEntity;
import ru.moex.papertrader.infra.repository.AccountBalanceRepository;
import ru.moex.papertrader.infra.repository.InstrumentRepository;
import ru.moex.papertrader.infra.repository.PositionRepository;

/**
 * Портфельный снапшот из PostgreSQL: кэш и заблокированная маржа счёта, активные заявки и позиции.
 *
 * <p>Правила блокировок активных заявок вынесены в {@link OrderLocksCalculator} и общие с
 * исполнением заявок. Балансовая блокировка {@code account_balances.locked} — маржа под позиции,
 * она входит в {@code lockedForFutures}.
 *
 * <p>Метод {@code snapshot()} не помечен {@code readOnly}: при отсутствии строки баланса он
 * восстанавливает её через {@link AccountService#ensureAccount()}, а PostgreSQL отклоняет запись в
 * транзакции, открытой только для чтения.
 */
@Service
public class JpaPortfolioServiceImpl implements PortfolioService {

  private static final Logger log = LoggerFactory.getLogger(JpaPortfolioServiceImpl.class);

  private final OrderService orderService;
  private final AccountService accountService;
  private final AccountBalanceRepository balances;
  private final AccountBalanceMapper balanceMapper;
  private final PositionRepository positions;
  private final InstrumentRepository instruments;
  private final InstrumentMapper instrumentMapper;
  private final PositionMapper positionMapper;
  private final MarketDataProvider marketDataProvider;
  private final OrderLocksCalculator orderLocksCalculator;
  private final PortfolioProperties properties;
  private final Clock clock;

  public JpaPortfolioServiceImpl(
      OrderService orderService,
      AccountService accountService,
      AccountBalanceRepository balances,
      AccountBalanceMapper balanceMapper,
      PositionRepository positions,
      InstrumentRepository instruments,
      InstrumentMapper instrumentMapper,
      PositionMapper positionMapper,
      MarketDataProvider marketDataProvider,
      OrderLocksCalculator orderLocksCalculator,
      PortfolioProperties properties,
      Clock clock) {
    this.orderService = orderService;
    this.accountService = accountService;
    this.balances = balances;
    this.balanceMapper = balanceMapper;
    this.positions = positions;
    this.instruments = instruments;
    this.instrumentMapper = instrumentMapper;
    this.positionMapper = positionMapper;
    this.marketDataProvider = marketDataProvider;
    this.orderLocksCalculator = orderLocksCalculator;
    this.properties = properties;
    this.clock = clock;
  }

  @Override
  @Transactional
  public PortfolioSnapshot snapshot() {
    String accountId = properties.account();
    String currency = properties.currency();
    PortfolioBalance balance = balance(accountId, currency);

    OrderLocksCalculator.OrderLocks locks =
        orderLocksCalculator.calculate(orderService.activeOrders(), null);

    // ГО активных фьючерсных заявок и ГО открытых позиций (balance.locked) учитываются раздельно:
    // контракт БКС отдаёт их полями cbplUsedForOrders и cbplUsedForPositions.
    BigDecimal lockedForFuturesOrders = Money.round(locks.futuresMargin());
    BigDecimal lockedForFuturesPositions = Money.round(balance.locked());
    BigDecimal lockedForFutures = lockedForFuturesOrders.add(lockedForFuturesPositions);
    BigDecimal lockedCash = locks.stockLocks().add(lockedForFutures);
    return new PortfolioSnapshot(
        accountId,
        currency,
        balance.cash(),
        lockedCash,
        lockedForFutures,
        lockedForFuturesOrders,
        lockedForFuturesPositions,
        positions(accountId),
        clock.instant());
  }

  private PortfolioBalance balance(String accountId, String currency) {
    Optional<AccountBalanceEntity> found = balances.findByAccountIdAndCurrency(accountId, currency);
    if (found.isEmpty()) {
      // Штатно строку создаёт AccountSeedRunner; fallback страхует тестовые и нестандартные
      // контексты, где снапшот запрашивают до сида.
      log.debug("Баланс {}/{} отсутствует — создаю счёт", accountId, currency);
      accountService.ensureAccount();
      found = balances.findByAccountIdAndCurrency(accountId, currency);
    }
    AccountBalanceEntity entity =
        found.orElseThrow(
            () -> new IllegalStateException("Баланс " + accountId + "/" + currency + " не создан"));
    return balanceMapper.toDomain(entity);
  }

  /** Позиции счёта; после сделок строки обновляются {@code PositionRepository.upsert}. */
  private List<Position> positions(String accountId) {
    List<PositionEntity> rows = positions.findByAccountId(accountId);
    if (rows.isEmpty()) {
      return List.of();
    }
    Set<Long> instrumentIds =
        rows.stream().map(PositionEntity::getInstrumentId).collect(Collectors.toSet());
    Map<Long, Instrument> instrumentsById =
        instruments.findAllById(instrumentIds).stream()
            .collect(
                Collectors.toMap(
                    InstrumentEntity::getId, instrumentMapper::toDomain, (left, right) -> left));
    List<Position> result = new ArrayList<>(rows.size());
    for (PositionEntity row : rows) {
      Instrument instrument = instrumentsById.get(row.getInstrumentId());
      if (instrument == null) {
        throw new IllegalStateException(
            "Инструмент позиции " + row.getInstrumentId() + " не найден");
      }
      PortfolioPosition position = positionMapper.toDomain(row, instrument);
      result.add(
          new Position(
              instrument,
              position.quantity(),
              position.averagePrice(),
              currentPrice(instrument, position.averagePrice())));
    }
    return List.copyOf(result);
  }

  private BigDecimal currentPrice(Instrument instrument, BigDecimal fallback) {
    return marketDataProvider.quote(instrument).map(Quote::last).orElse(fallback);
  }
}
