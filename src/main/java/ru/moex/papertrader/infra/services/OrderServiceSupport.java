package ru.moex.papertrader.infra.services;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import ru.moex.papertrader.core.domain.Instrument;
import ru.moex.papertrader.core.domain.Order;
import ru.moex.papertrader.core.domain.PageResult;
import ru.moex.papertrader.core.marketdata.Clock;
import ru.moex.papertrader.infra.services.exception.InvalidRequestException;

/**
 * Эталонная логика заявок этапа 2, общая для in-memory и JPA реализаций: биржевой идентификатор,
 * сортировка и пагинация результата поиска.
 *
 * <p>Вынесена из {@link InMemoryOrderServiceImpl}, чтобы реализации не расходились в поведении.
 */
final class OrderServiceSupport {

  private static final ZoneId MOSCOW = ZoneId.of("Europe/Moscow");
  private static final DateTimeFormatter ORDER_ID_DATE =
      DateTimeFormatter.ofPattern("yyyyMMdd").withZone(MOSCOW);
  private static final long ORDER_ID_SEQUENCE_BASE = 796_285_400_000L;
  private static final List<String> DEFAULT_SORT = List.of("orderDateTime,asc");

  private OrderServiceSupport() {}

  /** Биржевой идентификатор контракта БКС: {@code ГГММДД-КодКласса-Номер}. */
  static String exchangeOrderId(Clock clock, Instrument instrument, long orderNum) {
    return ORDER_ID_DATE.format(clock.instant())
        + "-"
        + instrument.classCode()
        + "-"
        + (ORDER_ID_SEQUENCE_BASE + orderNum);
  }

  /** Компаратор по спецификациям {@code поле,направление}; по умолчанию — дата создания. */
  static Comparator<Order> comparator(List<String> sortSpecs) {
    List<String> specs = sortSpecs == null || sortSpecs.isEmpty() ? DEFAULT_SORT : sortSpecs;
    Comparator<Order> comparator = null;
    for (String spec : specs) {
      String[] parts = spec.split(",", -1);
      if (parts.length != 2) {
        throw new InvalidRequestException("Некорректная сортировка: " + spec);
      }
      Comparator<Order> current = fieldComparator(parts[0].trim());
      String direction = parts[1].trim();
      if ("desc".equalsIgnoreCase(direction)) {
        current = current.reversed();
      } else if (!"asc".equalsIgnoreCase(direction)) {
        throw new InvalidRequestException("Некорректное направление сортировки: " + direction);
      }
      comparator = comparator == null ? current : comparator.thenComparing(current);
    }
    return comparator == null ? Comparator.comparingLong(Order::orderNum) : comparator;
  }

  /** Страница по уже отсортированному списку; за пределами диапазона — пустой список. */
  static PageResult<Order> paginate(List<Order> orders, int page, int size) {
    long totalRecords = orders.size();
    int totalPages = (int) ((totalRecords + size - 1) / size);
    long offset = (long) page * size;
    if (offset >= totalRecords) {
      return new PageResult<>(List.of(), totalRecords, totalPages);
    }
    int fromIndex = (int) offset;
    int toIndex = (int) Math.min(offset + size, totalRecords);
    return new PageResult<>(orders.subList(fromIndex, toIndex), totalRecords, totalPages);
  }

  private static Comparator<Order> fieldComparator(String field) {
    return switch (field) {
      case "orderDateTime" -> Comparator.comparing(Order::createdAt);
      case "updateDateTime" -> Comparator.comparing(Order::updatedAt);
      case "ticker" -> Comparator.comparing(order -> order.instrument().ticker());
      case "classCode" -> Comparator.comparing(order -> order.instrument().classCode());
      case "orderType" -> Comparator.comparing(order -> order.orderType().code());
      case "side" -> Comparator.comparing(order -> order.side().code());
      default -> throw new InvalidRequestException("Неподдерживаемое поле сортировки: " + field);
    };
  }
}
