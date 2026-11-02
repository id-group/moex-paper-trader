package ru.moex.papertrader.infra.services;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.moex.papertrader.core.domain.Instrument;
import ru.moex.papertrader.core.domain.NewOrder;
import ru.moex.papertrader.core.domain.Order;
import ru.moex.papertrader.core.domain.OrderIdType;
import ru.moex.papertrader.core.domain.OrderSearchCriteria;
import ru.moex.papertrader.core.domain.PageResult;
import ru.moex.papertrader.core.marketdata.Clock;
import ru.moex.papertrader.core.marketdata.MarketDataProvider;
import ru.moex.papertrader.infra.mapper.InstrumentMapper;
import ru.moex.papertrader.infra.mapper.OrderMapper;
import ru.moex.papertrader.infra.model.InstrumentEntity;
import ru.moex.papertrader.infra.model.OrderEntity;
import ru.moex.papertrader.infra.repository.InstrumentRepository;
import ru.moex.papertrader.infra.repository.OrderRepository;
import ru.moex.papertrader.infra.services.exception.ConflictException;
import ru.moex.papertrader.infra.services.exception.InvalidRequestException;
import ru.moex.papertrader.infra.services.exception.ResourceNotFoundException;

/**
 * Заявки в PostgreSQL: поведение этапа 2 без изменений, состояние переживает перезапуск.
 *
 * <p>Нумерация и проверка дубликатов выполняются под пессимистичной блокировкой строки счёта, а
 * {@code order_num} и {@code client_order_id} дополнительно защищены уникальными ограничениями БД.
 */
@Service
public class JpaOrderServiceImpl implements OrderService {

  private static final Logger log = LoggerFactory.getLogger(JpaOrderServiceImpl.class);

  private final Clock clock;
  private final MarketDataProvider marketDataProvider;
  private final PortfolioProperties portfolioProperties;
  private final AccountService accountService;
  private final OrderRepository orders;
  private final InstrumentRepository instruments;
  private final OrderMapper orderMapper;
  private final InstrumentMapper instrumentMapper;

  public JpaOrderServiceImpl(
      Clock clock,
      MarketDataProvider marketDataProvider,
      PortfolioProperties portfolioProperties,
      AccountService accountService,
      OrderRepository orders,
      InstrumentRepository instruments,
      OrderMapper orderMapper,
      InstrumentMapper instrumentMapper) {
    this.clock = clock;
    this.marketDataProvider = marketDataProvider;
    this.portfolioProperties = portfolioProperties;
    this.accountService = accountService;
    this.orders = orders;
    this.instruments = instruments;
    this.orderMapper = orderMapper;
    this.instrumentMapper = instrumentMapper;
  }

  @Override
  @Transactional
  public Order place(NewOrder request) {
    if (request.clientOrderId() == null) {
      throw new InvalidRequestException("clientOrderId обязателен");
    }
    Instrument instrument =
        marketDataProvider
            .instrument(request.ticker(), request.classCode())
            .orElseThrow(
                () ->
                    new ResourceNotFoundException(
                        "Инструмент "
                            + request.ticker()
                            + "/"
                            + request.classCode()
                            + " не найден"));

    String accountId = portfolioProperties.account();
    accountService.lockAccount(accountId);
    if (orders.existsByClientOrderId(request.clientOrderId())) {
      throw new ConflictException(
          "Заявка с clientOrderId " + request.clientOrderId() + " уже существует");
    }

    long orderNum = orders.findMaxOrderNum().orElse(0L) + 1;
    Order order =
        Order.accept(
            request,
            instrument,
            OrderServiceSupport.exchangeOrderId(clock, instrument, orderNum),
            orderNum,
            accountId,
            clock.instant());
    Long instrumentId = resolveInstrumentId(instrument);
    try {
      orders.saveAndFlush(orderMapper.toEntity(order, accountId, instrumentId));
    } catch (DataIntegrityViolationException exception) {
      // Гонка с параллельной заявкой того же clientOrderId: ограничение БД надёжнее проверки.
      throw new ConflictException(
          "Заявка с clientOrderId " + request.clientOrderId() + " уже существует");
    }
    log.debug("Заявка {} сохранена, номер {}", order.orderId(), orderNum);
    return order;
  }

  @Override
  @Transactional(readOnly = true)
  public Order get(OrderIdType idType, String orderId) {
    return toDomain(resolveEntity(idType, orderId));
  }

  @Override
  @Transactional
  public Order cancel(OrderIdType idType, String orderId) {
    String accountId = portfolioProperties.account();
    accountService.lockAccount(accountId);
    OrderEntity entity = resolveEntity(idType, orderId);
    Order order = orderMapper.toDomain(entity, instrument(entity.getInstrumentId()));
    order.cancel(clock.instant());
    orders.saveAndFlush(orderMapper.toEntity(order, accountId, entity.getInstrumentId()));
    log.debug("Заявка {} отменена", order.orderId());
    return order;
  }

  @Override
  @Transactional(readOnly = true)
  public PageResult<Order> search(
      OrderSearchCriteria criteria, int page, int size, List<String> sortSpecs) {
    List<Order> filtered = new ArrayList<>();
    for (Order order : toDomain(orders.findByAccountIdOrderByOrderNumAsc(accountId()))) {
      if (criteria.matches(order)) {
        filtered.add(order);
      }
    }
    filtered.sort(OrderServiceSupport.comparator(sortSpecs));
    return OrderServiceSupport.paginate(filtered, page, size);
  }

  @Override
  @Transactional(readOnly = true)
  public List<Order> activeOrders() {
    return toDomain(orders.findByAccountIdOrderByOrderNumAsc(accountId())).stream()
        .filter(order -> order.status().isActive())
        .toList();
  }

  private String accountId() {
    return portfolioProperties.account();
  }

  private OrderEntity resolveEntity(OrderIdType idType, String orderId) {
    if (orderId == null || orderId.isBlank()) {
      throw new InvalidRequestException("orderId обязателен");
    }
    Optional<OrderEntity> found =
        idType == OrderIdType.CLIENT
            ? orders.findByClientOrderId(parseClientOrderId(orderId))
            : orders.findById(orderId);
    return found.orElseThrow(
        () -> new ResourceNotFoundException("Заявка " + orderId + " не найдена"));
  }

  private UUID parseClientOrderId(String value) {
    try {
      return UUID.fromString(value);
    } catch (IllegalArgumentException exception) {
      throw new InvalidRequestException("orderId должен быть UUID при orderIdType=1", exception);
    }
  }

  private Long resolveInstrumentId(Instrument instrument) {
    Optional<InstrumentEntity> existing =
        instruments.findByTickerAndClassCode(instrument.ticker(), instrument.classCode());
    if (existing.isPresent()) {
      return existing.get().getId();
    }
    InstrumentEntity entity = instrumentMapper.toEntity(instrument);
    // ON CONFLICT DO NOTHING вместо save(): упавшая на UNIQUE вставка прервала бы транзакцию,
    // и перечитать строку, вставленную конкурентом, в ней уже нельзя.
    instruments.insertIfAbsent(
        entity.getTicker(),
        entity.getClassCode(),
        entity.getType(),
        entity.getShortName(),
        entity.getDisplayName(),
        entity.getIsin(),
        entity.getLotSize(),
        entity.getMinimumStep(),
        entity.getCurrency());
    return instruments
        .findByTickerAndClassCode(instrument.ticker(), instrument.classCode())
        .orElseThrow(
            () ->
                new IllegalStateException(
                    "Инструмент "
                        + instrument.ticker()
                        + "/"
                        + instrument.classCode()
                        + " не сохранён"))
        .getId();
  }

  private Instrument instrument(Long instrumentId) {
    return instruments
        .findById(instrumentId)
        .map(instrumentMapper::toDomain)
        .orElseThrow(() -> new IllegalStateException("Инструмент " + instrumentId + " не найден"));
  }

  /** Восстанавливает заявки, загружая инструменты одним запросом (без N+1). */
  private List<Order> toDomain(List<OrderEntity> entities) {
    if (entities.isEmpty()) {
      return List.of();
    }
    Set<Long> instrumentIds =
        entities.stream().map(OrderEntity::getInstrumentId).collect(Collectors.toSet());
    Map<Long, Instrument> instrumentsById =
        instruments.findAllById(instrumentIds).stream()
            .collect(
                Collectors.toMap(
                    InstrumentEntity::getId, instrumentMapper::toDomain, (left, right) -> left));
    Function<OrderEntity, Instrument> instrumentOf =
        entity -> {
          Instrument instrument = instrumentsById.get(entity.getInstrumentId());
          if (instrument == null) {
            throw new IllegalStateException(
                "Инструмент заявки " + entity.getOrderId() + " не найден");
          }
          return instrument;
        };
    return entities.stream()
        .map(entity -> orderMapper.toDomain(entity, instrumentOf.apply(entity)))
        .toList();
  }

  private Order toDomain(OrderEntity entity) {
    return orderMapper.toDomain(entity, instrument(entity.getInstrumentId()));
  }
}
