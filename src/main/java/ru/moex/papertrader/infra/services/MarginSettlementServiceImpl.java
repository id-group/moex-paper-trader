package ru.moex.papertrader.infra.services;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.moex.papertrader.core.commission.InstrumentType;
import ru.moex.papertrader.core.domain.Instrument;
import ru.moex.papertrader.core.marketdata.MarketDataProvider;
import ru.moex.papertrader.core.marketdata.Quote;
import ru.moex.papertrader.core.portfolio.MarginCalculator;
import ru.moex.papertrader.core.portfolio.Money;
import ru.moex.papertrader.core.portfolio.NavCalculator;
import ru.moex.papertrader.core.portfolio.NavSnapshot;
import ru.moex.papertrader.core.portfolio.PortfolioPosition;
import ru.moex.papertrader.core.portfolio.VariationMarginCalculator;
import ru.moex.papertrader.core.portfolio.VirtualPortfolio;
import ru.moex.papertrader.infra.mapper.InstrumentMapper;
import ru.moex.papertrader.infra.mapper.NavDailyMapper;
import ru.moex.papertrader.infra.mapper.VirtualPortfolioMapper;
import ru.moex.papertrader.infra.model.AccountBalanceEntity;
import ru.moex.papertrader.infra.model.InstrumentEntity;
import ru.moex.papertrader.infra.model.PositionEntity;
import ru.moex.papertrader.infra.repository.AccountBalanceRepository;
import ru.moex.papertrader.infra.repository.FillRepository;
import ru.moex.papertrader.infra.repository.InstrumentRepository;
import ru.moex.papertrader.infra.repository.NavDailyRepository;
import ru.moex.papertrader.infra.repository.PositionRepository;
import ru.moex.papertrader.infra.services.exception.ConflictException;

/**
 * Клиринг в PostgreSQL: вариационная маржа фьючерсов и NAV-срез закрытия дня.
 *
 * <p>ВМ по позиции считается {@link VariationMarginCalculator} от средней цены позиции к расчётной
 * цене дня (последняя цена {@link MarketDataProvider}) и начисляется в кэш. Убыток фиксируется даже
 * если кэш становится отрицательным: проверка свободных средств на клиринг не распространяется.
 * После начисления средняя цена позиции становится расчётной, а ГО пересчитывается от неё ({@link
 * MarginCalculator}); дельта ГО блокируется или освобождается без проверки обеспечения.
 *
 * <p>NAV считается {@link NavCalculator}: {@code cash − комиссия + ВМ + рыночная стоимость акций}.
 * Компонент {@code cash} записи — баланс после ВМ и комиссий, приведённый к состоянию до комиссии и
 * ВМ, поэтому итог совпадает с {@code баланс + рыночная стоимость}. Фьючерсы в рыночную стоимость
 * не входят: их результат уже в кэше через ВМ.
 *
 * <p>ВМ и NAV выполняются в одной транзакции под пессимистичной блокировкой строки счёта. Срез
 * {@code nav_daily} уникален по паре (счёт, дата): повторное закрытие дня — {@link
 * ConflictException}.
 */
@Service
public class MarginSettlementServiceImpl implements MarginSettlementService {

  private static final Logger log = LoggerFactory.getLogger(MarginSettlementServiceImpl.class);

  /** Формула ВМ; экземпляр не хранит состояния. */
  private static final VariationMarginCalculator VARIATION_MARGIN_CALCULATOR =
      new VariationMarginCalculator();

  /** Ставки ГО по умолчанию (10%, без переопределений по тикеру). */
  private static final MarginCalculator MARGIN_CALCULATOR = MarginCalculator.withDefaults();

  /** Формула NAV; экземпляр не хранит состояния. */
  private static final NavCalculator NAV_CALCULATOR = new NavCalculator();

  private final Clock clock;
  private final MarketDataProvider marketDataProvider;
  private final PortfolioProperties properties;
  private final AccountService accountService;
  private final FillRepository fills;
  private final NavDailyRepository navDaily;
  private final PositionRepository positions;
  private final AccountBalanceRepository balances;
  private final InstrumentRepository instruments;
  private final InstrumentMapper instrumentMapper;
  private final NavDailyMapper navDailyMapper;
  private final VirtualPortfolioMapper virtualPortfolioMapper;

  public MarginSettlementServiceImpl(
      Clock clock,
      MarketDataProvider marketDataProvider,
      PortfolioProperties properties,
      AccountService accountService,
      FillRepository fills,
      NavDailyRepository navDaily,
      PositionRepository positions,
      AccountBalanceRepository balances,
      InstrumentRepository instruments,
      InstrumentMapper instrumentMapper,
      NavDailyMapper navDailyMapper,
      VirtualPortfolioMapper virtualPortfolioMapper) {
    this.clock = clock;
    this.marketDataProvider = marketDataProvider;
    this.properties = properties;
    this.accountService = accountService;
    this.fills = fills;
    this.navDaily = navDaily;
    this.positions = positions;
    this.balances = balances;
    this.instruments = instruments;
    this.instrumentMapper = instrumentMapper;
    this.navDailyMapper = navDailyMapper;
    this.virtualPortfolioMapper = virtualPortfolioMapper;
  }

  @Override
  @Transactional
  public BigDecimal settleVariationMargin() {
    String accountId = properties.account();
    accountService.lockAccount(accountId);
    Instant now = clock.instant();
    VirtualPortfolio portfolio = restorePortfolio(accountId);
    return settle(accountId, portfolio, now);
  }

  @Override
  @Transactional
  public NavSnapshot closeDay(LocalDate navDate) {
    if (navDate == null) {
      throw new IllegalArgumentException("navDate не может быть null");
    }
    String accountId = properties.account();
    accountService.lockAccount(accountId);
    if (navDaily.findByAccountIdAndNavDate(accountId, navDate).isPresent()) {
      throw new ConflictException("NAV за " + navDate + " по счёту " + accountId + " уже закрыт");
    }

    Instant now = clock.instant();
    VirtualPortfolio portfolio = restorePortfolio(accountId);
    BigDecimal variationMargin = settle(accountId, portfolio, now);
    BigDecimal commission = commissionForDay(accountId, navDate);
    BigDecimal cashBalance = portfolio.cash(properties.currency());
    // cash_param приводится к состоянию до комиссии и ВМ: NavCalculator вычтет комиссию и прибавит
    // ВМ, поэтому nav = cashBalance + marketValue без двойного счёта.
    BigDecimal cashParam = Money.round(cashBalance.subtract(variationMargin).add(commission));

    Map<String, PortfolioPosition> positionsByTicker = new TreeMap<>();
    for (PortfolioPosition position : portfolio.positions()) {
      positionsByTicker.put(position.instrument().ticker(), position);
    }
    Map<String, Long> stockHoldings = new TreeMap<>();
    Map<String, BigDecimal> stockPrices = new TreeMap<>();
    List<NavSnapshot.Holding> holdings = new ArrayList<>(positionsByTicker.size());
    for (PortfolioPosition position : positionsByTicker.values()) {
      BigDecimal closingPrice = settlementPrice(position.instrument());
      holdings.add(
          new NavSnapshot.Holding(
              position.instrument().ticker(), position.quantity(), closingPrice));
      if (!isFutures(position.instrument())) {
        stockHoldings.put(position.instrument().ticker(), position.quantity());
        stockPrices.put(position.instrument().ticker(), closingPrice);
      }
    }

    NavSnapshot stockSnapshot =
        NAV_CALCULATOR.calculate(
            now, cashParam, commission, variationMargin, stockHoldings, stockPrices);
    NavSnapshot snapshot =
        new NavSnapshot(
            stockSnapshot.asOf(),
            stockSnapshot.cash(),
            stockSnapshot.commission(),
            stockSnapshot.variationMargin(),
            stockSnapshot.marketValue(),
            stockSnapshot.nav(),
            holdings);
    navDaily.saveAndFlush(navDailyMapper.toEntity(snapshot, accountId, navDate));
    log.debug(
        "NAV за {} по счёту {}: кэш {}, комиссия {}, ВМ {}, рыночная стоимость {}, NAV {}",
        navDate,
        accountId,
        stockSnapshot.cash(),
        commission,
        variationMargin,
        stockSnapshot.marketValue(),
        stockSnapshot.nav());
    return snapshot;
  }

  /**
   * Начисляет ВМ и пересчитывает ГО по всем фьючерсным позициям.
   *
   * <p>Сначала ВМ меняет кэш, затем дельта ГО меняет блокировку без проверки свободных средств.
   * Запись в БД выполняется только при ненулевом изменении: повторный расчёт с той же расчётной
   * ценой состояние не трогает.
   *
   * @return суммарная ВМ вызова
   */
  private BigDecimal settle(String accountId, VirtualPortfolio portfolio, Instant now) {
    BigDecimal total = Money.ZERO;
    boolean changed = false;
    for (PortfolioPosition position : portfolio.positions()) {
      Instrument instrument = position.instrument();
      if (!isFutures(instrument)) {
        continue;
      }
      BigDecimal settlementPrice = settlementPrice(instrument);
      BigDecimal variationMargin =
          VARIATION_MARGIN_CALCULATOR.variationMargin(
              position.quantity(), settlementPrice, position.averagePrice());
      BigDecimal marginBefore =
          MARGIN_CALCULATOR.initialMargin(
              instrument.ticker(), position.quantity(), position.averagePrice());
      BigDecimal marginAfter =
          MARGIN_CALCULATOR.initialMargin(
              instrument.ticker(), position.quantity(), settlementPrice);
      BigDecimal marginDelta = marginAfter.subtract(marginBefore);

      portfolio.applyVariationMargin(instrument.currency(), variationMargin);
      portfolio.applyMarginChange(instrument.currency(), marginDelta);
      portfolio.settlePosition(instrument, settlementPrice);
      total = total.add(variationMargin);
      changed = changed || variationMargin.signum() != 0 || marginDelta.signum() != 0;
    }
    if (changed) {
      persistSettlement(accountId, portfolio, now);
    }
    return Money.round(total);
  }

  /**
   * Записывает средние цены фьючерсных позиций и баланс счёта. Акции не пишутся: их средняя цена —
   * цена сделки, клиринг её не меняет.
   */
  private void persistSettlement(String accountId, VirtualPortfolio portfolio, Instant now) {
    for (PortfolioPosition position : portfolio.positions()) {
      if (!isFutures(position.instrument())) {
        continue;
      }
      positions.upsert(
          accountId,
          instrumentId(position.instrument()),
          position.quantity(),
          position.averagePrice(),
          now);
    }
    saveBalances(accountId, portfolio, now);
  }

  private void saveBalances(String accountId, VirtualPortfolio portfolio, Instant now) {
    Set<String> currencies = new TreeSet<>(portfolio.cashByCurrency().keySet());
    currencies.addAll(portfolio.lockedCashByCurrency().keySet());
    for (String currency : currencies) {
      int updated =
          balances.updateBalances(
              accountId, currency, portfolio.cash(currency), portfolio.lockedCash(currency), now);
      if (updated == 0) {
        throw new IllegalStateException("Баланс " + accountId + "/" + currency + " не найден");
      }
    }
  }

  /** Сумма комиссий счёта за календарный день {@code navDate} (границы — UTC). */
  private BigDecimal commissionForDay(String accountId, LocalDate navDate) {
    Instant from = navDate.atStartOfDay(ZoneOffset.UTC).toInstant();
    Instant to = navDate.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
    BigDecimal sum = fills.sumCommissionByAccountIdAndExecutedAtBetween(accountId, from, to);
    return Money.round(sum == null ? BigDecimal.ZERO : sum);
  }

  /** Расчётная цена дня — последняя цена заглушки рыночных данных. */
  private BigDecimal settlementPrice(Instrument instrument) {
    return marketDataProvider
        .quote(instrument)
        .map(Quote::last)
        .orElseThrow(
            () ->
                new IllegalStateException(
                    "Нет расчётной цены для "
                        + instrument.ticker()
                        + "/"
                        + instrument.classCode()));
  }

  /** Восстанавливает портфель счёта, допуская отрицательный кэш после убыточной ВМ. */
  private VirtualPortfolio restorePortfolio(String accountId) {
    List<AccountBalanceEntity> balanceRows = balances.findByAccountId(accountId);
    List<PositionEntity> positionRows = positions.findByAccountId(accountId);
    Map<Long, Instrument> instrumentsById =
        positionRows.isEmpty()
            ? Map.of()
            : instruments
                .findAllById(positionRows.stream().map(PositionEntity::getInstrumentId).toList())
                .stream()
                .collect(
                    Collectors.toMap(
                        InstrumentEntity::getId,
                        instrumentMapper::toDomain,
                        (left, right) -> left));
    return virtualPortfolioMapper.toDomainAllowNegativeCash(
        balanceRows, positionRows, instrumentsById);
  }

  private Long instrumentId(Instrument instrument) {
    return instruments
        .findByTickerAndClassCode(instrument.ticker(), instrument.classCode())
        .map(InstrumentEntity::getId)
        .orElseThrow(
            () ->
                new IllegalStateException(
                    "Инструмент "
                        + instrument.ticker()
                        + "/"
                        + instrument.classCode()
                        + " не найден"));
  }

  private static boolean isFutures(Instrument instrument) {
    return InstrumentType.FUTURES.name().equalsIgnoreCase(instrument.type());
  }
}
