package ru.moex.papertrader.infra.algoapi;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.Map;
import tools.jackson.databind.JsonNode;

/**
 * Чтение ISS-подобной табличной схемы ответа {@code {block: {columns: [...], data: [[...]]}}}.
 *
 * <p>Обе площадки (AlgoPack и MOEX ISS) возвращают данные одинаковым табличным форматом: набор
 * колонок и массив строк-массивов. Класс даёт доступ к значениям по имени колонки и не зависит от
 * порядка полей в ответе. Время без смещения трактуется как московское (UTC+3) и переводится в UTC
 * — по брифу этапа 4 храним UTC.
 *
 * <p>Класс открыт и для infra/services: торговый календарь разбирает им минутные свечи (границы
 * сессии).
 */
public final class TableReader {

  /** Московское время — часовой пояс всех дат ISS/AlgoPack. */
  static final ZoneId MOSCOW = ZoneId.of("Europe/Moscow");

  private static final DateTimeFormatter DATE_TIME =
      DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
  private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss");

  private final String blockName;
  private final Map<String, Integer> columns;
  private final JsonNode rows;

  private TableReader(String blockName, Map<String, Integer> columns, JsonNode rows) {
    this.blockName = blockName;
    this.columns = columns;
    this.rows = rows;
  }

  /** Обязательный блок ответа; его отсутствие — ошибка схемы API. */
  public static TableReader of(JsonNode root, String block) {
    JsonNode node = root.path(block);
    if (!node.isObject() || !node.path("columns").isArray() || !node.path("data").isArray()) {
      throw new AlgoApiException("В ответе API нет табличного блока '" + block + "'");
    }
    Map<String, Integer> indexes = new LinkedHashMap<>();
    JsonNode columns = node.path("columns");
    for (int i = 0; i < columns.size(); i++) {
      indexes.put(columns.get(i).asString(), i);
    }
    return new TableReader(block, Map.copyOf(indexes), node.path("data"));
  }

  /** Необязательный блок ответа; {@code null}, если API его не вернул. */
  public static TableReader optional(JsonNode root, String block) {
    return root.path(block).isObject() ? of(root, block) : null;
  }

  public int size() {
    return rows.size();
  }

  public String text(int row, String column) {
    JsonNode value = value(row, column);
    if (value == null || value.isNull()) {
      return null;
    }
    String text = value.asString();
    return text.isBlank() ? null : text;
  }

  public BigDecimal decimal(int row, String column) {
    JsonNode value = value(row, column);
    if (value == null || value.isNull()) {
      return null;
    }
    if (value.isNumber()) {
      return value.decimalValue();
    }
    String text = value.asString();
    if (text.isBlank()) {
      return null;
    }
    try {
      return new BigDecimal(text.trim());
    } catch (NumberFormatException e) {
      throw new AlgoApiException(
          "Блок '" + blockName + "', колонка '" + column + "': ожидалось число, получено " + text,
          e);
    }
  }

  public long longValue(int row, String column, long defaultValue) {
    BigDecimal value = decimal(row, column);
    return value == null ? defaultValue : value.longValue();
  }

  /** Дата-время из колонки; {@code null}, если значение пустое или не распознано. */
  public Instant dateTime(int row, String column) {
    return toInstant(text(row, column));
  }

  /** Дата из одной колонки и время из другой (например, {@code TRADEDATE} + {@code TRADETIME}). */
  public Instant dateAndTime(int row, String dateColumn, String timeColumn) {
    return combine(text(row, dateColumn), text(row, timeColumn));
  }

  /** Собирает момент из даты {@code yyyy-MM-dd} и времени {@code HH:mm:ss} в московской зоне. */
  static Instant combine(String date, String time) {
    if (date == null || date.isBlank() || time == null || time.isBlank()) {
      return null;
    }
    try {
      return LocalDateTime.of(LocalDate.parse(date.trim()), LocalTime.parse(time.trim(), TIME))
          .atZone(MOSCOW)
          .toInstant();
    } catch (DateTimeParseException e) {
      return null;
    }
  }

  /**
   * Разбирает время API в UTC. Поддерживаются ISO-момент, ISO со смещением, {@code yyyy-MM-dd
   * HH:mm:ss} (МСК) и дата {@code yyyy-MM-dd} (начало дня, МСК). Время без даты распознать нельзя —
   * возвращается {@code null}.
   */
  static Instant toInstant(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    String text = value.trim();
    try {
      return Instant.parse(text);
    } catch (DateTimeParseException ignored) {
      // пробуем следующие форматы
    }
    try {
      return OffsetDateTime.parse(text).toInstant();
    } catch (DateTimeParseException ignored) {
      // пробуем следующие форматы
    }
    try {
      return LocalDateTime.parse(text, DATE_TIME).atZone(MOSCOW).toInstant();
    } catch (DateTimeParseException ignored) {
      // пробуем следующий формат
    }
    try {
      return LocalDate.parse(text).atStartOfDay(MOSCOW).toInstant();
    } catch (DateTimeParseException ignored) {
      return null;
    }
  }

  private JsonNode value(int row, String column) {
    Integer index = columns.get(column);
    if (index == null || row < 0 || row >= rows.size()) {
      return null;
    }
    JsonNode values = rows.get(row);
    return values.isArray() && index < values.size() ? values.get(index) : null;
  }
}
