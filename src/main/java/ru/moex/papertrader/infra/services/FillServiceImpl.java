package ru.moex.papertrader.infra.services;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.moex.papertrader.core.commission.CommissionCalculator;
import ru.moex.papertrader.core.commission.CommissionTariffRegistry;
import ru.moex.papertrader.core.commission.InstrumentType;
import ru.moex.papertrader.core.domain.Fill;
import ru.moex.papertrader.core.domain.Instrument;
import ru.moex.papertrader.core.domain.InvalidOrderException;
import ru.moex.papertrader.core.domain.Order;
import ru.moex.papertrader.core.domain.OrderIdType;
import ru.moex.papertrader.core.domain.OrderStatus;
import ru.moex.papertrader.core.domain.Side;
import ru.moex.papertrader.core.marketdata.Clock;
import ru.moex.papertrader.core.marketdata.MarketDataProvider;
import ru.moex.papertrader.core.marketdata.Quote;
import ru.moex.papertrader.core.portfolio.MarginCalculator;
import ru.moex.papertrader.core.portfolio.Money;
import ru.moex.papertrader.core.portfolio.PortfolioPosition;
import ru.moex.papertrader.core.portfolio.VirtualPortfolio;
import ru.moex.papertrader.infra.mapper.FillMapper;
import ru.moex.papertrader.infra.mapper.InstrumentMapper;
import ru.moex.papertrader.infra.mapper.OrderMapper;
import ru.moex.papertrader.infra.mapper.VirtualPortfolioMapper;
import ru.moex.papertrader.infra.model.AccountBalanceEntity;
import ru.moex.papertrader.infra.model.InstrumentEntity;
import ru.moex.papertrader.infra.model.PositionEntity;
import ru.moex.papertrader.infra.repository.AccountBalanceRepository;
import ru.moex.papertrader.infra.repository.FillRepository;
import ru.moex.papertrader.infra.repository.InstrumentRepository;
import ru.moex.papertrader.infra.repository.OrderRepository;
import ru.moex.papertrader.infra.repository.PositionRepository;

/**
 * Исполнение заявок в PostgreSQL: акции и фьючерсы.
 *
 * <p>Акции: покупка списывает {@code цена × количество + комиссия}, продажа зачисляет {@code цена ×
 * количество − комиссия}; шорт акций запрещён. Комиссия — тариф по умолчанию {@link
 * CommissionTariffRegistry#defaults()}: 0.1% от оборота, минимум 0.01 RUB.
 *
 * <p>Фьючерсы: стоимость контракта не списывается, кэш меняется только на биржевой сбор {@code 1.00
 * RUB × количество}. Под открытую позицию блокируется ГО {@code |количество| × средняя цена × 10%}
 * (см. {@link ru.moex.papertrader.core.portfolio.MarginCalculator}); при сделке меняется только
 * дельта ГО: рост блокируется, снижение освобождается. Короткая позиция по фьючерсу разрешена.
 *
 * <p>Доступные средства: кэш минус балансовая блокировка {@code account_balances.locked} и
 * блокировки других активных заявок. Блокировка исполняемой заявки не учитывается: иначе стоимость
 * сделки или ГО считались бы дважды.
 *
 * <p>Заявка, сделка, позиция и баланс меняются в одной транзакции под пессимистичной блокировкой
 * строки счёта. Вариационная маржа и NAV — задача S3-5e.
 */
@Service
public class FillServiceImpl implements FillService {

  private static final Logger log = LoggerFactory.getLogger(FillServiceImpl.class);

  /**
   * Тариф по умолчанию: акции 0.1%, минимум 0.01 RUB; фьючерсы — сбор за контракт. Переопределения
   * — задача тарифного профиля.
   */
  private static final CommissionCalculator COMMISSION_CALCULATOR =
      new CommissionCalculator(CommissionTariffRegistry.defaults());

  /** Ставки ГО по умолчанию (10%, без переопределений по тикеру). */
  private static final MarginCalculator MARGIN_CALCULATOR = MarginCalculator.withDefaults();

  private final Clock clock;
  private final MarketDataProvider marketDataProvider;
  private final PortfolioProperties properties;
  private final AccountService accountService;
  private final OrderService orderService;
  private final OrderRepository orders;
  private final FillRepository fills;
  private final PositionRepository positions;
  private final AccountBalanceRepository balances;
  private final InstrumentRepository instruments;
  private final OrderMapper orderMapper;
  private final FillMapper fillMapper;
  private final InstrumentMapper instrumentMapper;
  private final VirtualPortfolioMapper virtualPortfolioMapper;
  private final OrderLocksCalculator orderLocksCalculator;

  public FillServiceImpl(
      Clock clock,
      MarketDataProvider marketDataProvider,
      PortfolioProperties properties,
      AccountService accountService,
      OrderService orderService,
      OrderRepository orders,
      FillRepository fills,
      PositionRepository positions,
      AccountBalanceRepository balances,
      InstrumentRepository instruments,
      OrderMapper orderMapper,
      FillMapper fillMapper,
      InstrumentMapper instrumentMapper,
      VirtualPortfolioMapper virtualPortfolioMapper,
      OrderLocksCalculator orderLocksCalculator) {
    this.clock = clock;
    this.marketDataProvider = marketDataProvider;
    this.properties = properties;
    this.accountService = accountService;
    this.orderService = orderService;
    this.orders = orders;
    this.fills = fills;
    this.positions = positions;
    this.balances = balances;
    this.instruments = instruments;
    this.orderMapper = orderMapper;
    this.fillMapper = fillMapper;
    this.instrumentMapper = instrumentMapper;
    this.virtualPortfolioMapper = virtualPortfolioMapper;
    this.orderLocksCalculator = orderLocksCalculator;
  }

  @Override
  @Transactional
  public Order fill(OrderIdType idType, String orderId, long quantity, BigDecimal price) {
    String accountId = properties.account();
    accountService.lockAccount(accountId);
    Order order = orderService.get(idType, orderId);

    requireActive(order);
    long remaining = order.quantity() - order.executedQuantity();
    if (quantity <= 0) {
      throw new InvalidOrderException("Объём сделки должен быть больше 0");
    }
    if (quantity > remaining) {
      throw new InvalidOrderException(
          "Объём сделки " + quantity + " больше остатка " + remaining + " по заявке " + orderId);
    }

    Instrument instrument = order.instrument();
    InstrumentType type = instrumentType(instrument);
    Instant now = clock.instant();
    BigDecimal effectivePrice = price == null ? lastPrice(instrument) : price;
    if (effectivePrice == null || effectivePrice.signum() <= 0) {
      throw new InvalidOrderException("price должен быть положительным");
    }
    BigDecimal commission =
        COMMISSION_CALCULATOR.calculate(type, instrument.ticker(), effectivePrice, quantity);

    VirtualPortfolio portfolio = restorePortfolio(accountId);
    boolean futures = type == InstrumentType.FUTURES;
    // ГО «до» считается по средней цене уже открытой позиции — формулы средней цены не дублируем.
    BigDecimal marginBefore = futures ? positionMargin(portfolio, instrument) : Money.ZERO;
    if (!futures) {
      checkStockAvailability(portfolio, order, effectivePrice, quantity, commission);
    }

    Fill fill =
        order.side() == Side.BUY
            ? portfolio.buy(instrument, quantity, effectivePrice, commission, now)
            : portfolio.sell(instrument, quantity, effectivePrice, commission, now);
    if (futures) {
      // Портфель — рабочая копия до persist: отказ по обеспечению откатывает транзакцию и не
      // сохраняет ни сделку, ни позицию, ни баланс.
      updateFuturesMargin(portfolio, order, marginBefore);
    }
    order.applyFill(fill);

    persist(accountId, order, instrument, fill, portfolio, now);
    log.debug(
        "Сделка по заявке {}: {} {}/{} по {} с комиссией {}",
        order.orderId(),
        quantity,
        instrument.ticker(),
        instrument.classCode(),
        effectivePrice,
        commission);
    return order;
  }

  private static void requireActive(Order order) {
    if (order.status() != OrderStatus.NEW && order.status() != OrderStatus.PARTIALLY_FILLED) {
      throw new InvalidOrderException(
          "Заявка "
              + order.orderId()
              + " не может быть исполнена в статусе "
              + order.status().code()
              + " ("
              + order.status()
              + ")");
    }
  }

  /**
   * Тип инструмента, определяющий схему исполнения: акции и фьючерсы. Прочие типы пока не
   * поддерживаются — новые классы инструментов добавляются вместе со своей моделью исполнения.
   */
  private static InstrumentType instrumentType(Instrument instrument) {
    if (InstrumentType.FUTURES.name().equalsIgnoreCase(instrument.type())) {
      return InstrumentType.FUTURES;
    }
    if (InstrumentType.STOCK.name().equalsIgnoreCase(instrument.type())) {
      return InstrumentType.STOCK;
    }
    throw new InvalidOrderException(
        "Тип инструмента " + instrument.type() + " пока не поддерживается исполнением");
  }

  private BigDecimal lastPrice(Instrument instrument) {
    return marketDataProvider
        .quote(instrument)
        .map(Quote::last)
        .orElseThrow(
            () ->
                new InvalidOrderException(
                    "Нет рыночной цены для " + instrument.ticker() + "/" + instrument.classCode()));
  }

  /**
   * Проверяет доступные средства для акционной сделки до изменения состояния.
   *
   * <p>Покупка: {@code cash − account_balances.locked − блокировки других активных заявок} должно
   * покрывать {@code цена × количество + комиссия}. Продажа: позиции должно хватать на объём.
   */
  private void checkStockAvailability(
      VirtualPortfolio portfolio,
      Order order,
      BigDecimal price,
      long quantity,
      BigDecimal commission) {
    String currency = order.instrument().currency();
    if (order.side() == Side.BUY) {
      BigDecimal available = availableCash(portfolio, order, currency);
      BigDecimal required =
          Money.round(price.multiply(BigDecimal.valueOf(quantity))).add(commission);
      if (available.compareTo(required) < 0) {
        throw new InvalidOrderException(
            "Недостаточно свободных средств "
                + currency
                + ": требуется "
                + required
                + ", доступно "
                + available);
      }
      return;
    }
    long held = portfolio.position(order.instrument()).map(PortfolioPosition::quantity).orElse(0L);
    if (quantity > held) {
      throw new InvalidOrderException(
          "Нельзя продать "
              + quantity
              + " "
              + order.instrument().ticker()
              + ": в позиции только "
              + held);
    }
  }

  /**
   * Переводит блокировку ГО на дельту позиции: рост блокируется, снижение освобождается.
   *
   * <p>Дельта — разница ГО позиции до и после сделки. Комиссия к моменту проверки уже списана
   * портфелем, поэтому требование {@code дельта + комиссия} проверяется как {@code дельта} от
   * доступных средств.
   *
   * @param marginBefore ГО позиции по средней цене до сделки
   * @throws InvalidOrderException если на дельту ГО не хватает обеспечения
   */
  private void updateFuturesMargin(
      VirtualPortfolio portfolio, Order order, BigDecimal marginBefore) {
    String currency = order.instrument().currency();
    BigDecimal delta = positionMargin(portfolio, order.instrument()).subtract(marginBefore);
    if (delta.signum() > 0) {
      BigDecimal available = availableCash(portfolio, order, currency);
      if (available.compareTo(delta) < 0) {
        throw new InvalidOrderException(
            "Недостаточно обеспечения "
                + currency
                + ": требуется "
                + delta
                + ", доступно "
                + available);
      }
      portfolio.lock(currency, delta);
    } else if (delta.signum() < 0) {
      portfolio.release(currency, delta.negate());
    }
  }

  /**
   * Доступные средства под сделку: кэш минус балансовая блокировка и блокировки других активных
   * заявок. Собственная блокировка исполняемой заявки исключена.
   */
  private BigDecimal availableCash(VirtualPortfolio portfolio, Order order, String currency) {
    BigDecimal otherOrderLocks =
        orderLocksCalculator.calculate(orderService.activeOrders(), order.orderId()).total();
    return portfolio
        .cash(currency)
        .subtract(portfolio.lockedCash(currency))
        .subtract(otherOrderLocks);
  }

  /** ГО открытой фьючерсной позиции по средней цене; формула — {@link MarginCalculator}. */
  private static BigDecimal positionMargin(VirtualPortfolio portfolio, Instrument instrument) {
    return portfolio
        .position(instrument)
        .map(
            position ->
                MARGIN_CALCULATOR.initialMargin(
                    instrument.ticker(), position.quantity(), position.averagePrice()))
        .orElse(Money.ZERO);
  }

  /**
   * Восстанавливает портфель счёта: кэш, балансовые блокировки и открытые позиции.
   *
   * <p>Отрицательный кэш и блокировка ГО больше остатка допустимы: после убыточной вариационной
   * маржи это штатное состояние, и строгое восстановление не должно запрещать сделки по счёту,
   * включая закрытие убыточной позиции. Проверки свободных средств выполняются отдельно — до
   * изменения состояния и только для сделок, которым нужны средства.
   */
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

  private void persist(
      String accountId,
      Order order,
      Instrument instrument,
      Fill fill,
      VirtualPortfolio portfolio,
      Instant now) {
    Long instrumentId =
        instruments
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

    orders.saveAndFlush(orderMapper.toEntity(order, accountId, instrumentId));
    fills.save(fillMapper.toEntity(fill, order.orderId(), accountId, instrumentId));
    savePosition(accountId, instrumentId, instrument, portfolio, now);
    saveBalances(accountId, portfolio, now);
  }

  /** Пишет только позицию исполняемого инструмента; остальные позиции не меняются. */
  private void savePosition(
      String accountId,
      Long instrumentId,
      Instrument instrument,
      VirtualPortfolio portfolio,
      Instant now) {
    PortfolioPosition position = portfolio.position(instrument).orElse(null);
    if (position == null) {
      positions.deleteByAccountIdAndInstrumentId(accountId, instrumentId);
      return;
    }
    positions.upsert(accountId, instrumentId, position.quantity(), position.averagePrice(), now);
  }

  /**
   * Пишет кэш и балансовую блокировку портфеля. Блокировки отдельных заявок в {@code
   * account_balances.locked} не попадают: они считаются динамически.
   */
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
}
