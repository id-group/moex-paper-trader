package ru.moex.papertrader.infra.services;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.stereotype.Service;
import ru.moex.papertrader.core.domain.PageResult;
import ru.moex.papertrader.core.domain.Trade;
import ru.moex.papertrader.core.domain.TradeSearchCriteria;
import ru.moex.papertrader.infra.services.exception.InvalidRequestException;

/** In-memory реализация хранилища сделок (временное решение; этап 3 заменит на JPA). */
@Service
public class InMemoryTradeServiceImpl implements TradeService {

  private static final List<String> DEFAULT_SORT = List.of("tradeDateTime,asc");

  private final Map<Long, Trade> byTradeNum = new ConcurrentHashMap<>();
  private final AtomicLong tradeSequence = new AtomicLong();

  @Override
  public Trade register(Trade trade) {
    long tradeNum = tradeSequence.incrementAndGet();
    Trade registered =
        new Trade(
            tradeNum,
            trade.orderNum(),
            trade.clientCode(),
            trade.instrument(),
            trade.side(),
            trade.price(),
            trade.quantity(),
            trade.commission(),
            trade.executedAt());
    byTradeNum.put(tradeNum, registered);
    return registered;
  }

  @Override
  public PageResult<Trade> search(
      TradeSearchCriteria criteria, int page, int size, List<String> sortSpecs) {
    List<Trade> filtered = new ArrayList<>();
    for (Trade trade : byTradeNum.values()) {
      if (criteria.matches(trade)) {
        filtered.add(trade);
      }
    }
    filtered.sort(comparator(sortSpecs));
    long totalRecords = filtered.size();
    int totalPages = (int) ((totalRecords + size - 1) / size);
    long offset = (long) page * size;
    if (offset >= totalRecords) {
      return new PageResult<>(List.of(), totalRecords, totalPages);
    }
    int fromIndex = (int) offset;
    int toIndex = (int) Math.min(offset + size, totalRecords);
    return new PageResult<>(filtered.subList(fromIndex, toIndex), totalRecords, totalPages);
  }

  private Comparator<Trade> comparator(List<String> sortSpecs) {
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

  private Comparator<Trade> fieldComparator(String field) {
    return switch (field) {
      case "tradeDateTime" -> Comparator.comparing(Trade::executedAt);
      case "ticker" -> Comparator.comparing(trade -> trade.instrument().ticker());
      case "classCode" -> Comparator.comparing(trade -> trade.instrument().classCode());
      case "side" -> Comparator.comparing(trade -> trade.side().code());
      default -> throw new InvalidRequestException("Неподдерживаемое поле сортировки: " + field);
    };
  }
}
