package ru.moex.papertrader.infra.algoapi;

import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.web.client.RestClient;
import ru.moex.papertrader.infra.algoapi.dto.AlgoPackCandleDto;
import ru.moex.papertrader.infra.algoapi.dto.AlgoPackOrderBookDto;
import ru.moex.papertrader.infra.algoapi.dto.AlgoPackQuoteDto;
import ru.moex.papertrader.infra.algoapi.dto.AlgoPackTradeDto;
import tools.jackson.databind.JsonNode;

/**
 * HTTP-клиент AlgoPack (real-time market data: котировки, стакан, свечи, сделки).
 *
 * <p>Ответы площадки имеют ISS-подобную табличную схему {@code metadata/columns/data}; клиент
 * разбирает строки в типизированные DTO. Даты без смещения — московские, в DTO переводятся в UTC.
 *
 * <p>Авторизация (решение владельца, 2026-11-02): заголовок {@code Authorization: Bearer <APIKEY>}
 * отправляется только при непустом API-ключе; сам ключ — только из переменной окружения.
 *
 * <p>Документация: <a
 * href="https://moexalgo.github.io/docs/api/real-time-market-data-акции">Real-time market data —
 * Акции</a>.
 */
public class AlgoPackClient {

  /** Префикс заголовка авторизации AlgoPack: {@code Authorization: Bearer <APIKEY>}. */
  public static final String AUTH_HEADER = "Authorization";

  /** Схема токена в заголовке авторизации. */
  public static final String BEARER_PREFIX = "Bearer ";

  private final JsonHttpClient http;
  private final String pathPrefix;
  private final String defaultBoard;

  /** Клиент с собственным {@link RestClient.Builder}; удобно для Spring-сборки. */
  public AlgoPackClient(RestClient.Builder builder, AlgoPackProperties properties) {
    Objects.requireNonNull(properties, "properties");
    Map<String, String> headers =
        properties.token().isBlank()
            ? Map.of()
            : Map.of(AUTH_HEADER, BEARER_PREFIX + properties.token());
    this.http =
        new JsonHttpClient(
            builder,
            properties.baseUrl(),
            properties.connectTimeoutMs(),
            properties.readTimeoutMs(),
            properties.maxRetries(),
            properties.retryDelayMs(),
            "AlgoPack",
            headers);
    this.pathPrefix =
        "/iss/engines/"
            + properties.engine()
            + "/markets/"
            + properties.market()
            + "/boards/"
            + properties.board();
    this.defaultBoard = properties.board();
  }

  /** Клиент со стандартным построителем RestClient. */
  public AlgoPackClient(AlgoPackProperties properties) {
    this(RestClient.builder(), properties);
  }

  /** Котировка инструмента ({@code .../securities/{ticker}.json}). */
  public AlgoPackQuoteDto quote(String security) {
    JsonNode root = http.get(securityPath(security) + ".json", Map.of());
    return parseQuote(root, security);
  }

  /**
   * Стакан инструмента ({@code .../securities/{ticker}/orderbook.json}).
   *
   * @param depth максимальная глубина каждой стороны; API отдаёт 10 уровней для акций
   */
  public AlgoPackOrderBookDto orderBook(String security, int depth) {
    JsonNode root = http.get(securityPath(security) + "/orderbook.json", Map.of());
    return parseOrderBook(root, security, depth);
  }

  /**
   * Сделки инструмента за диапазон. API отдаёт сделки текущего торгового дня и не поддерживает
   * фильтр по дате, поэтому диапазон применяется к строкам ответа на стороне клиента.
   */
  public List<AlgoPackTradeDto> allTrades(String security, Instant from, Instant to) {
    JsonNode root = http.get(securityPath(security) + "/trades.json", Map.of());
    return parseTrades(root, from, to);
  }

  /**
   * Свечи инструмента за период.
   *
   * @param timeframe код интервала API: 1, 10, 60, 24, 7, 31 (4 — квартал)
   */
  public List<AlgoPackCandleDto> candles(
      String security, String timeframe, Instant from, Instant to) {
    Map<String, String> query = new LinkedHashMap<>();
    query.put("interval", timeframe);
    if (from != null) {
      query.put("from", from.toString());
    }
    if (to != null) {
      query.put("till", to.toString());
    }
    JsonNode root = http.get(securityPath(security) + "/candles.json", query);
    return parseCandles(root);
  }

  private String securityPath(String security) {
    if (security == null || security.isBlank()) {
      throw new IllegalArgumentException("security не может быть пустым");
    }
    String encoded = URLEncoder.encode(security, StandardCharsets.UTF_8);
    return pathPrefix + "/securities/" + encoded;
  }

  private AlgoPackQuoteDto parseQuote(JsonNode root, String security) {
    TableReader market = TableReader.of(root, "marketdata");
    if (market.size() == 0) {
      throw new AlgoApiException("AlgoPack: пустой блок marketdata для " + security);
    }
    TableReader securities = TableReader.optional(root, "securities");
    BigDecimal prevPrice =
        securities != null && securities.size() > 0 ? securities.decimal(0, "PREVPRICE") : null;
    return new AlgoPackQuoteDto(
        textOr(market.text(0, "SECID"), security),
        textOr(market.text(0, "BOARDID"), defaultBoard),
        market.decimal(0, "BID"),
        market.decimal(0, "OFFER"),
        market.decimal(0, "OPEN"),
        market.decimal(0, "HIGH"),
        market.decimal(0, "LOW"),
        market.decimal(0, "LAST"),
        prevPrice,
        market.decimal(0, "LASTCHANGE"),
        market.decimal(0, "LASTCHANGEPRCNT"),
        market.text(0, "TRADINGSTATUS"),
        market.dateTime(0, "SYSTIME"));
  }

  private AlgoPackOrderBookDto parseOrderBook(JsonNode root, String security, int depth) {
    TableReader book = TableReader.of(root, "orderbook");
    TableReader dataversion = TableReader.optional(root, "dataversion");
    String tradeDate =
        dataversion != null && dataversion.size() > 0 ? dataversion.text(0, "trade_date") : null;
    List<AlgoPackOrderBookDto.Level> bids = new ArrayList<>();
    List<AlgoPackOrderBookDto.Level> asks = new ArrayList<>();
    String updateTime = null;
    String secId = security;
    String boardId = defaultBoard;
    for (int row = 0; row < book.size(); row++) {
      secId = textOr(book.text(row, "SECID"), secId);
      boardId = textOr(book.text(row, "BOARDID"), boardId);
      if (updateTime == null) {
        updateTime = book.text(row, "UPDATETIME");
      }
      BigDecimal price = book.decimal(row, "PRICE");
      if (price == null) {
        continue;
      }
      long quantity = book.longValue(row, "QUANTITY", 0L);
      AlgoPackOrderBookDto.Level level = new AlgoPackOrderBookDto.Level(price, quantity);
      String buySell = book.text(row, "BUYSELL");
      if ("B".equalsIgnoreCase(buySell) && bids.size() < Math.max(depth, 0)) {
        bids.add(level);
      } else if ("S".equalsIgnoreCase(buySell) && asks.size() < Math.max(depth, 0)) {
        asks.add(level);
      }
    }
    return new AlgoPackOrderBookDto(
        secId, boardId, TableReader.combine(tradeDate, updateTime), bids, asks);
  }

  private List<AlgoPackTradeDto> parseTrades(JsonNode root, Instant from, Instant to) {
    TableReader trades = TableReader.of(root, "trades");
    List<AlgoPackTradeDto> result = new ArrayList<>(trades.size());
    for (int row = 0; row < trades.size(); row++) {
      Instant time = trades.dateTime(row, "SYSTIME");
      if (time == null) {
        time = trades.dateAndTime(row, "TRADEDATE", "TRADETIME");
      }
      if (!inRange(time, from, to)) {
        continue;
      }
      result.add(
          new AlgoPackTradeDto(
              trades.longValue(row, "TRADENO", 0L),
              time,
              trades.text(row, "BOARDID"),
              trades.text(row, "SECID"),
              trades.decimal(row, "PRICE"),
              trades.longValue(row, "QUANTITY", 0L),
              trades.decimal(row, "VALUE"),
              trades.text(row, "BUYSELL"),
              trades.text(row, "TRADINGSESSION")));
    }
    return List.copyOf(result);
  }

  private List<AlgoPackCandleDto> parseCandles(JsonNode root) {
    TableReader candles = TableReader.of(root, "candles");
    List<AlgoPackCandleDto> result = new ArrayList<>(candles.size());
    for (int row = 0; row < candles.size(); row++) {
      result.add(
          new AlgoPackCandleDto(
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
