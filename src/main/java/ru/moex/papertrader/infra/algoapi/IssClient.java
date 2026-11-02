package ru.moex.papertrader.infra.algoapi;

import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.web.client.RestClient;
import ru.moex.papertrader.infra.algoapi.dto.IssCandleDto;
import ru.moex.papertrader.infra.algoapi.dto.IssOrderBookDto;
import ru.moex.papertrader.infra.algoapi.dto.IssSecurityDto;
import ru.moex.papertrader.infra.algoapi.dto.IssTradeDto;
import tools.jackson.databind.JsonNode;

/**
 * HTTP-клиент MOEX ISS: справочник инструментов, стакан, свечи, сделки.
 *
 * <p>Ответы ISS имеют табличную схему {@code {block: {columns, data}}} (при {@code iss.meta=off}
 * блок {@code metadata} отсутствует). Время в ответах — московское, в DTO переводится в UTC.
 *
 * <p>Документация: <a href="https://iss.moex.com/iss/reference/">ISS Queries</a>.
 */
public class IssClient {

  /** Формат даты для параметров from/till свечей: ISS принимает только {@code yyyy-MM-dd}. */
  private static final DateTimeFormatter ISS_DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd");

  private final JsonHttpClient http;

  /** Клиент с собственным {@link RestClient.Builder}; удобно для Spring-сборки. */
  public IssClient(RestClient.Builder builder, IssProperties properties) {
    Objects.requireNonNull(properties, "properties");
    this.http =
        new JsonHttpClient(
            builder,
            properties.baseUrl(),
            properties.connectTimeoutMs(),
            properties.readTimeoutMs(),
            properties.maxRetries(),
            properties.retryDelayMs(),
            "MOEX ISS",
            Map.of());
  }

  /** Клиент со стандартным построителем RestClient. */
  public IssClient(IssProperties properties) {
    this(RestClient.builder(), properties);
  }

  /** Инструменты режима торгов ({@code .../boards/{board}/securities.json}). */
  public List<IssSecurityDto> securities(String engine, String market, String board) {
    Map<String, String> query = new LinkedHashMap<>();
    query.put("iss.meta", "off");
    query.put("iss.only", "securities");
    JsonNode root = http.get(prefix(engine, market, board) + "/securities.json", query);
    return parseSecurities(root);
  }

  /** Стакан инструмента ({@code .../securities/{secid}/orderbook.json}). */
  public IssOrderBookDto orderBook(String engine, String market, String board, String secid) {
    JsonNode root =
        http.get(
            prefix(engine, market, board) + "/securities/" + encode(secid) + "/orderbook.json",
            Map.of("iss.meta", "off"));
    return parseOrderBook(root, secid, board);
  }

  /**
   * Свечи инструмента ({@code .../securities/{secid}/candles.json}).
   *
   * @param intervalMinutes интервал ISS: 1, 10, 60, 24, 7, 31 (4 — квартал)
   */
  public List<IssCandleDto> candles(
      String engine,
      String market,
      String board,
      String secid,
      int intervalMinutes,
      Instant from,
      Instant to) {
    Map<String, String> query = new LinkedHashMap<>();
    query.put("iss.meta", "off");
    query.put("interval", Integer.toString(intervalMinutes));
    if (from != null) {
      // ISS принимает только дату: передача времени возвращает пустой набор свечей.
      query.put("from", from.atZone(TableReader.MOSCOW).toLocalDate().format(ISS_DATE));
    }
    if (to != null) {
      query.put("till", to.atZone(TableReader.MOSCOW).toLocalDate().format(ISS_DATE));
    }
    JsonNode root =
        http.get(
            prefix(engine, market, board) + "/securities/" + encode(secid) + "/candles.json",
            query);
    return parseCandles(root);
  }

  /**
   * Сделки инструмента за диапазон. ISS отдаёт сделки текущего торгового дня и не поддерживает
   * фильтр по дате, поэтому диапазон применяется к строкам ответа на стороне клиента.
   */
  public List<IssTradeDto> trades(
      String engine, String market, String board, String secid, Instant from, Instant to) {
    JsonNode root =
        http.get(
            prefix(engine, market, board) + "/securities/" + encode(secid) + "/trades.json",
            Map.of("iss.meta", "off"));
    return parseTrades(root, from, to);
  }

  private static String prefix(String engine, String market, String board) {
    return "/iss/engines/" + engine + "/markets/" + market + "/boards/" + board;
  }

  private static String encode(String value) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException("secid не может быть пустым");
    }
    return URLEncoder.encode(value, StandardCharsets.UTF_8);
  }

  private static List<IssSecurityDto> parseSecurities(JsonNode root) {
    TableReader securities = TableReader.of(root, "securities");
    List<IssSecurityDto> result = new ArrayList<>(securities.size());
    for (int row = 0; row < securities.size(); row++) {
      String secId = securities.text(row, "SECID");
      if (secId == null) {
        continue;
      }
      result.add(
          new IssSecurityDto(
              secId,
              securities.text(row, "BOARDID"),
              securities.text(row, "SHORTNAME"),
              securities.text(row, "SECNAME"),
              securities.text(row, "ISIN"),
              securities.decimal(row, "LOTSIZE"),
              securities.decimal(row, "MINSTEP"),
              securities.text(row, "CURRENCYID")));
    }
    return List.copyOf(result);
  }

  private static IssOrderBookDto parseOrderBook(JsonNode root, String secid, String board) {
    TableReader book = TableReader.of(root, "orderbook");
    List<IssOrderBookDto.Level> bids = new ArrayList<>();
    List<IssOrderBookDto.Level> asks = new ArrayList<>();
    String secId = secid;
    String boardId = board;
    BigDecimal last = null;
    for (int row = 0; row < book.size(); row++) {
      secId = textOr(book.text(row, "SECID"), secId);
      boardId = textOr(book.text(row, "BOARDID"), boardId);
      BigDecimal rowLast = book.decimal(row, "LAST");
      if (rowLast != null) {
        last = rowLast;
      }
      BigDecimal price = book.decimal(row, "PRICE");
      if (price == null) {
        continue;
      }
      IssOrderBookDto.Level level =
          new IssOrderBookDto.Level(price, book.longValue(row, "QUANTITY", 0L));
      String buySell = book.text(row, "BUYSELL");
      if ("B".equalsIgnoreCase(buySell)) {
        bids.add(level);
      } else if ("S".equalsIgnoreCase(buySell)) {
        asks.add(level);
      }
    }
    // В стакане ISS нет даты, только UPDATETIME: полный момент собирает провайдер через Clock.
    return new IssOrderBookDto(secId, boardId, null, last, bids, asks);
  }

  private static List<IssCandleDto> parseCandles(JsonNode root) {
    TableReader candles = TableReader.of(root, "candles");
    List<IssCandleDto> result = new ArrayList<>(candles.size());
    for (int row = 0; row < candles.size(); row++) {
      result.add(
          new IssCandleDto(
              candles.dateTime(row, "begin"),
              candles.dateTime(row, "end"),
              candles.decimal(row, "open"),
              candles.decimal(row, "close"),
              candles.decimal(row, "high"),
              candles.decimal(row, "low"),
              candles.decimal(row, "value"),
              candles.decimal(row, "volume")));
    }
    return List.copyOf(result);
  }

  private static List<IssTradeDto> parseTrades(JsonNode root, Instant from, Instant to) {
    TableReader trades = TableReader.of(root, "trades");
    List<IssTradeDto> result = new ArrayList<>(trades.size());
    for (int row = 0; row < trades.size(); row++) {
      Instant time = trades.dateTime(row, "SYSTIME");
      if (time == null) {
        time = trades.dateAndTime(row, "TRADEDATE", "TRADETIME");
      }
      if (!inRange(time, from, to)) {
        continue;
      }
      result.add(
          new IssTradeDto(
              trades.longValue(row, "TRADENO", 0L),
              time,
              trades.text(row, "SECID"),
              trades.decimal(row, "PRICE"),
              trades.longValue(row, "QUANTITY", 0L),
              trades.decimal(row, "VALUE"),
              trades.text(row, "BOARDID"),
              trades.text(row, "BUYSELL")));
    }
    return List.copyOf(result);
  }

  private static boolean inRange(Instant time, Instant from, Instant to) {
    if (time == null) {
      return true;
    }
    if (from != null && time.isBefore(from)) {
      return false;
    }
    return to == null || !time.isAfter(to);
  }

  private static String textOr(String value, String fallback) {
    return value == null ? fallback : value;
  }
}
