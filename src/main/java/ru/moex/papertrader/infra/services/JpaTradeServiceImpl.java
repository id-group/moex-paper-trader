package ru.moex.papertrader.infra.services;

import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.moex.papertrader.core.domain.Instrument;
import ru.moex.papertrader.core.domain.PageResult;
import ru.moex.papertrader.core.domain.Trade;
import ru.moex.papertrader.core.domain.TradeSearchCriteria;
import ru.moex.papertrader.infra.mapper.InstrumentMapper;
import ru.moex.papertrader.infra.model.FillEntity;
import ru.moex.papertrader.infra.model.OrderEntity;
import ru.moex.papertrader.infra.repository.FillRepository;
import ru.moex.papertrader.infra.repository.InstrumentRepository;
import ru.moex.papertrader.infra.repository.OrderRepository;
import ru.moex.papertrader.infra.services.exception.InvalidRequestException;

/**
 * Сделки счёта из PostgreSQL: {@code POST /v1/trades/search}.
 *
 * <p>Источник — таблица {@code fills} (её пишет {@link FillService}); заявка даёт номер, сторону и
 * клиентский код, справочник — инструмент. Торговый номер сделки — первичный ключ {@code fills}, он
 * уникален и стабилен. Выборка идёт по счёту из {@code emulator.portfolio.account}, как и у заявок.
 *
 * <p>Этот бин — основной ({@code @Primary}); {@link InMemoryTradeServiceImpl} остаётся как
 * in-memory реализация этапа 2 и источник unit-тестов сортировки/пагинации.
 */
@Primary
@Service
public class JpaTradeServiceImpl implements TradeService {

  private static final ZoneId MOSCOW = ZoneId.of("Europe/Moscow");
  private static final List<String> DEFAULT_SORT = List.of("tradeDateTime,asc");

  private final PortfolioProperties properties;
  private final FillRepository fills;
  private final OrderRepository orders;
  private final InstrumentRepository instruments;
  private final InstrumentMapper instrumentMapper;

  public JpaTradeServiceImpl(
      PortfolioProperties properties,
      FillRepository fills,
      OrderRepository orders,
      InstrumentRepository instruments,
      InstrumentMapper instrumentMapper) {
    this.properties = properties;
    this.fills = fills;
    this.orders = orders;
    this.instruments = instruments;
    this.instrumentMapper = instrumentMapper;
  }

  /**
   * Регистрация сделок выполняется {@link FillService} при исполнении; метод оставлен ради
   * совместимости интерфейса {@link TradeService} и возвращает сделку без записи.
   */
  @Override
  public Trade register(Trade trade) {
    return trade;
  }

  @Override
  @Transactional(readOnly = true)
  public PageResult<Trade> search(
      TradeSearchCriteria criteria, int page, int size, List<String> sortSpecs) {
    List<FillEntity> rows = fills.findByAccountIdOrderByExecutedAtAscIdAsc(properties.account());
    if (rows.isEmpty()) {
      return PageResult.empty();
    }

    Map<String, OrderEntity> orderCache = new LinkedHashMap<>();
    Map<Long, Instrument> instrumentCache = new LinkedHashMap<>();
    List<Trade> matched = new ArrayList<>();
    for (FillEntity fill : rows) {
      OrderEntity order =
          orderCache.computeIfAbsent(
              fill.getOrderId(), orderId -> orders.findById(orderId).orElse(null));
      Instrument instrument =
          instrumentCache.computeIfAbsent(
              fill.getInstrumentId(),
              instrumentId ->
                  instruments.findById(instrumentId).map(instrumentMapper::toDomain).orElse(null));
      if (order == null || instrument == null) {
        continue;
      }
      Trade trade =
          new Trade(
              fill.getId(),
              order.getOrderNum(),
              order.getClientCode(),
              instrument,
              order.getSide(),
              fill.getPrice(),
              fill.getQuantity(),
              fill.getCommission(),
              fill.getExecutedAt());
      if (criteria.matches(trade)) {
        matched.add(trade);
      }
    }
    matched.sort(comparator(sortSpecs));
    return paginate(matched, page, size);
  }

  private static Comparator<Trade> comparator(List<String> sortSpecs) {
    List<String> specs = sortSpecs == null || sortSpecs.isEmpty() ? DEFAULT_SORT : sortSpecs;
    Comparator<Trade> comparator = null;
    for (String spec : specs) {
      String[] parts = spec.split(",", -1);
      if (parts.length != 2) {
        throw new InvalidRequestException("Некорректная сортировка: " + spec);
      }
      Comparator<Trade> current = fieldComparator(parts[0].trim());
      String direction = parts[1].trim();
      if ("desc".equalsIgnoreCase(direction)) {
        current = current.reversed();
      } else if (!"asc".equalsIgnoreCase(direction)) {
        throw new InvalidRequestException("Некорректное направление сортировки: " + direction);
      }
      comparator = comparator == null ? current : comparator.thenComparing(current);
    }
    return comparator == null ? Comparator.comparingLong(Trade::tradeNum) : comparator;
  }

  private static Comparator<Trade> fieldComparator(String field) {
    return switch (field) {
      case "tradeDateTime" -> Comparator.comparing(Trade::executedAt);
      case "ticker" -> Comparator.comparing(trade -> trade.instrument().ticker());
      case "classCode" -> Comparator.comparing(trade -> trade.instrument().classCode());
      case "side" -> Comparator.comparing(trade -> trade.side().code());
      default -> throw new InvalidRequestException("Неподдерживаемое поле сортировки: " + field);
    };
  }

  /** Пагинация выборки; логика совпадает с in-memory реализацией этапа 2. */
  private static PageResult<Trade> paginate(List<Trade> trades, int page, int size) {
    long totalRecords = trades.size();
    int totalPages = (int) ((totalRecords + size - 1) / size);
    long offset = (long) page * size;
    if (offset >= totalRecords) {
      return new PageResult<>(List.of(), totalRecords, totalPages);
    }
    int fromIndex = (int) offset;
    int toIndex = (int) Math.min(offset + size, totalRecords);
    return new PageResult<>(trades.subList(fromIndex, toIndex), totalRecords, totalPages);
  }
}
