package ru.moex.papertrader.infra.services;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponentsBuilder;
import ru.moex.papertrader.core.marketdata.TradingCalendar;
import ru.moex.papertrader.core.marketdata.TradingDay;
import ru.moex.papertrader.core.marketdata.TradingSession;
import ru.moex.papertrader.infra.algoapi.AlgoApiException;
import ru.moex.papertrader.infra.algoapi.TableReader;
import ru.moex.papertrader.infra.mapper.TradingCalendarMapper;
import ru.moex.papertrader.infra.model.TradingCalendarEntity;
import ru.moex.papertrader.infra.repository.TradingCalendarRepository;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Торговый календарь с кэшем в PostgreSQL и дозагрузкой из MOEX ISS.
 *
 * <p>Источник торговых дней — история сделок ISS по эталонному инструменту {@code
 * calendarSecurity}: даты, за которые есть сделки, и есть торговые дни. Отдельного публичного
 * эндпоинта расписания ISS не публикует (см. {@code https://iss.moex.com/iss/reference/}), а
 * эндпоинт {@code .../boards/{board}/trading-schedule} отдаёт только карточку режима торгов,
 * поэтому используется проверенный {@code /iss/history/engines/{engine}/markets/{market}/boards/
 * {board}/securities/{security}.json}.
 *
 * <p>Границы сессии дня — решение владельца (2026-11-02): берутся из данных биржи, а не из
 * конфигурации. Открытие — начало первой минутной свечи дня, закрытие — конец последней свечи
 * вечернего фрагмента (запрос с 18:00 по Москве; в сутках меньше 500 таких баров — лимит ISS). Если
 * биржа не отдала данные, используется fallback-окно настроек {@code session-open}/{@code
 * session-close}.
 *
 * <p>Кэш: если все даты диапазона уже есть в БД, HTTP не вызывается. Дни после последней даты с
 * опубликованной историей ISS не кэшируются как неторговые: история за текущий день появляется
 * только после закрытия, и такая запись навсегда закрыла бы день в календаре.
 *
 * <p>Сервис не использует системные часы: все моменты времени приходят параметрами.
 */
@Service
public class TradingCalendarService implements TradingCalendar {

  private static final Logger log = LoggerFactory.getLogger(TradingCalendarService.class);

  /** Часовой пояс биржи: торговые даты и время сессий задаются по Москве. */
  static final ZoneId MOSCOW = ZoneId.of("Europe/Moscow");

  /** Единственная сессия упрощённой модели эмулятора. */
  static final int SESSION_ID = 1;

  static final String SESSION_NAME = "Основная сессия";

  /** Старт вечернего фрагмента для определения закрытия: основная сессия уже прошла. */
  private static final LocalTime CLOSE_CHUNK_START = LocalTime.of(18, 0);

  private static final DateTimeFormatter ISS_DATE_TIME =
      DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

  private final TradingCalendarRepository calendar;
  private final MarketDataProperties properties;
  private final TradingCalendarMapper mapper;
  private final ObjectMapper objectMapper;
  private final RestClient restClient;

  public TradingCalendarService(
      TradingCalendarRepository calendar,
      MarketDataProperties properties,
      ObjectMapper objectMapper) {
    this.calendar = Objects.requireNonNull(calendar, "calendar");
    this.properties = Objects.requireNonNull(properties, "properties");
    this.mapper = new TradingCalendarMapper();
    this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
    SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
    requestFactory.setConnectTimeout(properties.connectTimeout());
    requestFactory.setReadTimeout(properties.readTimeout());
    this.restClient = RestClient.builder().requestFactory(requestFactory).build();
  }

  @Override
  public List<TradingDay> tradingDays(Instant from, Instant to) {
    Objects.requireNonNull(from, "from");
    Objects.requireNonNull(to, "to");
    LocalDate fromDate = from.atZone(MOSCOW).toLocalDate();
    LocalDate toDate = to.atZone(MOSCOW).toLocalDate();
    if (toDate.isBefore(fromDate)) {
      return List.of();
    }
    List<LocalDate> dates = fromDate.datesUntil(toDate.plusDays(1)).toList();
    List<TradingCalendarEntity> cached = loadRange(fromDate, toDate);
    Map<LocalDate, TradingDay> known = new LinkedHashMap<>();
    for (TradingCalendarEntity entity : cached) {
      known.put(entity.getTradeDate(), mapper.toDomain(entity));
    }

    boolean complete = dates.stream().allMatch(known::containsKey);
    if (!complete && !synchronize(fromDate, toDate, known)) {
      // Сеть недоступна: отдаём то, что уже есть в БД, не роняя вызов.
      return cached.stream().map(mapper::toDomain).toList();
    }

    return dates.stream()
        .map(date -> known.getOrDefault(date, new TradingDay(date, false, null, null)))
        .toList();
  }

  @Override
  public TradingSession tradingSession(Instant at) {
    Objects.requireNonNull(at, "at");
    LocalDate date = at.atZone(MOSCOW).toLocalDate();
    Optional<TradingCalendarEntity> today = findDay(date);
    if (today.isPresent()) {
      TradingDay day = mapper.toDomain(today.get());
      if (day.trading() && day.openUtc() != null && day.closeUtc() != null) {
        if (!at.isBefore(day.openUtc()) && !at.isAfter(day.closeUtc())) {
          return new TradingSession(SESSION_ID, SESSION_NAME, true, day.closeUtc());
        }
        if (at.isBefore(day.openUtc())) {
          return new TradingSession(SESSION_ID, SESSION_NAME, false, day.openUtc());
        }
      }
    }
    Instant nextSessionAt = nextTradingDay(date).map(TradingDay::openUtc).orElse(null);
    return new TradingSession(
        SESSION_ID, SESSION_NAME, false, nextSessionAt != null ? nextSessionAt : at);
  }

  private List<TradingCalendarEntity> loadRange(LocalDate from, LocalDate to) {
    return calendar.findByEngineAndMarketAndBoardAndTradeDateBetweenOrderByTradeDate(
        properties.calendarEngine(),
        properties.calendarMarket(),
        properties.calendarBoard(),
        from,
        to);
  }

  private Optional<TradingCalendarEntity> findDay(LocalDate date) {
    return calendar.findByEngineAndMarketAndBoardAndTradeDate(
        properties.calendarEngine(), properties.calendarMarket(), properties.calendarBoard(), date);
  }

  private Optional<TradingDay> nextTradingDay(LocalDate date) {
    return calendar
        .findTopByEngineAndMarketAndBoardAndTradeDateGreaterThanAndTradingTrueOrderByTradeDateAsc(
            properties.calendarEngine(),
            properties.calendarMarket(),
            properties.calendarBoard(),
            date)
        .map(mapper::toDomain);
  }

  /**
   * Догружает диапазон из ISS и идемпотентно пишет дни в БД. Возвращает {@code false}, если
   * источник недоступен: вызывающий отдаёт частичный результат из кэша.
   */
  private boolean synchronize(LocalDate from, LocalDate to, Map<LocalDate, TradingDay> known) {
    Optional<Set<LocalDate>> fetched = fetchTradingDates(from, to);
    if (fetched.isEmpty()) {
      return false;
    }
    Set<LocalDate> tradingDates = fetched.get();
    if (tradingDates.isEmpty()) {
      // История за диапазон ещё не опубликована: не фиксируем дни как неторговые.
      log.warn("ISS не вернул торговых дней за {}..{}: кэш не обновлён", from, to);
      return true;
    }
    LocalDate lastTraded = tradingDates.stream().max(LocalDate::compareTo).orElseThrow();
    for (LocalDate date : from.datesUntil(to.plusDays(1)).toList()) {
      if (known.containsKey(date)) {
        continue;
      }
      boolean trading = tradingDates.contains(date);
      if (!trading && date.isAfter(lastTraded)) {
        continue;
      }
      TradingDay day = trading ? tradingDay(date) : new TradingDay(date, false, null, null);
      calendar.upsert(
          properties.calendarEngine(),
          properties.calendarMarket(),
          properties.calendarBoard(),
          date,
          day.trading(),
          day.openUtc(),
          day.closeUtc());
      known.put(date, day);
    }
    return true;
  }

  /** Торговый день с окном сессии из данных биржи; при недоступности — fallback на настройки. */
  private TradingDay tradingDay(LocalDate date) {
    Optional<SessionBounds> bounds = fetchSessionBounds(date);
    if (bounds.isPresent()) {
      return new TradingDay(date, true, bounds.get().openUtc(), bounds.get().closeUtc());
    }
    log.warn("ISS не отдал границы сессии за {}: используются настройки", date);
    Instant openUtc = date.atTime(properties.sessionOpen()).atZone(MOSCOW).toInstant();
    Instant closeUtc = date.atTime(properties.sessionClose()).atZone(MOSCOW).toInstant();
    return new TradingDay(date, true, openUtc, closeUtc);
  }

  /**
   * Границы сессии дня из минутных свечей биржи: открытие — начало первого бара дня, закрытие —
   * конец последнего бара вечернего фрагмента. Пусто, если данные биржи недоступны.
   */
  private Optional<SessionBounds> fetchSessionBounds(LocalDate date) {
    try {
      Instant openUtc = firstMinuteBarBegin(date);
      Instant closeUtc = lastMinuteBarEnd(date);
      if (openUtc == null || closeUtc == null) {
        return Optional.empty();
      }
      return Optional.of(new SessionBounds(openUtc, closeUtc));
    } catch (RestClientException | JacksonException | AlgoApiException exception) {
      log.error("Не удалось получить границы сессии {} из ISS: {}", date, exception.getMessage());
      return Optional.empty();
    }
  }

  /** Начало первой минутной свечи дня: запрос только по дате отдаёт бары с открытия дня. */
  private Instant firstMinuteBarBegin(LocalDate date) {
    TableReader candles = minuteCandles(date.toString(), date);
    return candles.size() == 0 ? null : candles.dateTime(0, "begin");
  }

  /** Конец последней свечи вечернего фрагмента (запрос с 18:00 по Москве — хвост дня). */
  private Instant lastMinuteBarEnd(LocalDate date) {
    TableReader candles = minuteCandles(CLOSE_CHUNK_START.atDate(date).format(ISS_DATE_TIME), date);
    return candles.size() == 0 ? null : candles.dateTime(candles.size() - 1, "end");
  }

  /** Минутные свечи эталонного инструмента; {@code from} — дата либо дата-время по Москве. */
  private TableReader minuteCandles(String from, LocalDate date) {
    String uri =
        UriComponentsBuilder.fromUriString(properties.issBaseUrl())
            .pathSegment(
                "iss",
                "engines",
                properties.calendarEngine(),
                "markets",
                properties.calendarMarket(),
                "boards",
                properties.calendarBoard(),
                "securities",
                properties.calendarSecurity(),
                "candles.json")
            .queryParam("interval", "1")
            .queryParam("from", from)
            .queryParam("till", date.toString())
            .queryParam("iss.meta", "off")
            .toUriString();
    String body = restClient.get().uri(uri).retrieve().body(String.class);
    return TableReader.of(objectMapper.readTree(body), "candles");
  }

  /** Границы торговой сессии дня (UTC). */
  private record SessionBounds(Instant openUtc, Instant closeUtc) {}

  private Optional<Set<LocalDate>> fetchTradingDates(LocalDate from, LocalDate to) {
    String uri =
        UriComponentsBuilder.fromUriString(properties.issBaseUrl())
            .pathSegment(
                "iss",
                "history",
                "engines",
                properties.calendarEngine(),
                "markets",
                properties.calendarMarket(),
                "boards",
                properties.calendarBoard(),
                "securities",
                properties.calendarSecurity() + ".json")
            .queryParam("from", from.toString())
            .queryParam("to", to.toString())
            .queryParam("iss.meta", "off")
            .toUriString();
    try {
      String body = restClient.get().uri(uri).retrieve().body(String.class);
      return Optional.of(parseTradedDates(body));
    } catch (RestClientException | JacksonException exception) {
      log.error(
          "Не удалось загрузить календарь ISS за {}..{}: {}", from, to, exception.getMessage());
      return Optional.empty();
    }
  }

  /** Читает колонку {@code TRADEDATE} блока {@code history} ответа ISS. */
  private Set<LocalDate> parseTradedDates(String body) {
    if (body == null || body.isBlank()) {
      return Set.of();
    }
    JsonNode history = objectMapper.readTree(body).path("history");
    JsonNode columns = history.path("columns");
    int tradedDateIndex = -1;
    for (int i = 0; i < columns.size(); i++) {
      if ("TRADEDATE".equals(columns.get(i).asString())) {
        tradedDateIndex = i;
        break;
      }
    }
    if (tradedDateIndex < 0) {
      log.warn("В ответе ISS нет колонки TRADEDATE: календарь не обновлён");
      return Set.of();
    }
    Set<LocalDate> dates = new TreeSet<>();
    for (JsonNode row : history.path("data").values()) {
      JsonNode value = row.get(tradedDateIndex);
      if (value != null && !value.isNull()) {
        dates.add(LocalDate.parse(value.asString()));
      }
    }
    return dates;
  }
}
