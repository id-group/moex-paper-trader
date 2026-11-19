package ru.moex.papertrader.infra.services;

import java.net.URI;
import java.time.DayOfWeek;
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
import org.springframework.transaction.annotation.Transactional;
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
 * конфигурации. Открытие — начало первой минутной свечи дня. Закрытие — конец последней свечи
 * вечернего фрагмента (запрос с 18:00 по Москве; в сутках меньше 500 таких баров — лимит ISS), но
 * только когда день уже закрылся: до планового закрытия вечерний фрагмент неполный, и по его
 * последнему бару день сузился бы задолго до конца сессии. Для идущего дня закрытие плановое из
 * {@code session-close}. Если биржа не отдала данные, используется fallback-окно настроек {@code
 * session-open}/{@code session-close} — полный торговый день TQBR 07:00–23:50 МСК.
 *
 * <p>Кэш: если все даты диапазона уже есть в БД, HTTP не вызывается. Дни после последней даты с
 * опубликованной историей ISS не кэшируются как неторговые: история за текущий день появляется
 * только после закрытия, и такая запись навсегда закрыла бы день в календаре. Вместо этого день
 * проверяется по живым минутным свечам: свечи есть — день кэшируется как торговый с границами
 * биржи, нет — остаётся некэшированным.
 *
 * <p>День, отсутствующий в БД (LIVE до первой синхронизации), не считается автоматически
 * неторговым: {@link #tradingSession(Instant)} применяет fallback-окно настроек {@code
 * session-open}/{@code session-close} для будних дней, как это делает {@code tradingDay()} при
 * недоступности ISS. Праздничные будни без записи в БД fallback не отличит от обычных.
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

  /**
   * Глубина периодической перезаписи границ, дни. Окно равно {@code TradingCalendarInitializer
   * .PAST_WINDOW}: ремонт нужен всем дням, записанным вслепую (fallback-окно или плановое
   * закрытие), а не только текущему.
   */
  private static final int BOUNDS_REFRESH_DAYS = 7;

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
  @Transactional
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
    if (!complete && !synchronize(fromDate, toDate, to, known)) {
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
          return new TradingSession(SESSION_ID, SESSION_NAME, true, day.closeUtc(), day.closeUtc());
        }
        if (at.isBefore(day.openUtc())) {
          return new TradingSession(SESSION_ID, SESSION_NAME, false, day.openUtc(), day.closeUtc());
        }
      }
      return closedSession(date);
    }
    // День в БД неизвестен (LIVE до первой синхронизации календаря): будний день считается
    // торговым с fallback-окном настроек — как в tradingDay() и StubMarketDataProvider.
    if (isWeekday(date)) {
      Instant openUtc = fallbackOpen(date);
      Instant closeUtc = date.atTime(properties.sessionClose()).atZone(MOSCOW).toInstant();
      if (!at.isBefore(openUtc) && !at.isAfter(closeUtc)) {
        return new TradingSession(SESSION_ID, SESSION_NAME, true, closeUtc, closeUtc);
      }
      if (at.isBefore(openUtc)) {
        return new TradingSession(SESSION_ID, SESSION_NAME, false, openUtc, closeUtc);
      }
    }
    return closedSession(date);
  }

  private TradingSession closedSession(LocalDate date) {
    Instant nextAt =
        nextTradingDay(date).map(TradingDay::openUtc).orElseGet(() -> nextWeekdayOpen(date));
    return new TradingSession(SESSION_ID, SESSION_NAME, false, nextAt, lastCloseUtc(date));
  }

  /** Закрытие последнего торгового дня, не позднее {@code date}; без БД — fallback-окно будня. */
  private Instant lastCloseUtc(LocalDate date) {
    return calendar
        .findTopByEngineAndMarketAndBoardAndTradeDateLessThanEqualAndTradingTrueOrderByTradeDateDesc(
            properties.calendarEngine(),
            properties.calendarMarket(),
            properties.calendarBoard(),
            date)
        .map(mapper::toDomain)
        .map(TradingDay::closeUtc)
        .orElseGet(() -> lastWeekdayClose(date));
  }

  /** Закрытие последнего будня, не позднее {@code date}, по fallback-окну настроек (UTC). */
  private Instant lastWeekdayClose(LocalDate date) {
    LocalDate last = date;
    while (isWeekend(last)) {
      last = last.minusDays(1);
    }
    return last.atTime(properties.sessionClose()).atZone(MOSCOW).toInstant();
  }

  /** Открытие следующего буднего дня по fallback-окну настроек (UTC). */
  private Instant nextWeekdayOpen(LocalDate date) {
    LocalDate next = date.plusDays(1);
    while (isWeekend(next)) {
      next = next.plusDays(1);
    }
    return fallbackOpen(next);
  }

  private Instant fallbackOpen(LocalDate date) {
    return date.atTime(properties.sessionOpen()).atZone(MOSCOW).toInstant();
  }

  private static boolean isWeekday(LocalDate date) {
    return !isWeekend(date);
  }

  private static boolean isWeekend(LocalDate date) {
    DayOfWeek dayOfWeek = date.getDayOfWeek();
    return dayOfWeek == DayOfWeek.SATURDAY || dayOfWeek == DayOfWeek.SUNDAY;
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
   *
   * <p>{@code asOf} — момент оценки состояния дня: день, закрытие которого ещё не наступило, не
   * может получить фактическую границу закрытия.
   */
  private boolean synchronize(
      LocalDate from, LocalDate to, Instant asOf, Map<LocalDate, TradingDay> known) {
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
        // История ISS не знает день (обычно текущий — она публикуется только после закрытия).
        // Спрашиваем живые минутные свечи: свечи есть — день торговый, кэшируем с границами
        // биржи; свечей нет — день не кэшируем, статус определит fallback-окно настроек.
        cacheLiveDay(date, asOf, known);
        continue;
      }
      TradingDay day = trading ? tradingDay(date, asOf) : new TradingDay(date, false, null, null);
      upsertDay(date, day);
      known.put(date, day);
    }
    return true;
  }

  /** Кэширует день как торговый, если по нему уже есть минутные свечи (торги идут). */
  private void cacheLiveDay(LocalDate date, Instant asOf, Map<LocalDate, TradingDay> known) {
    Optional<SessionBounds> bounds = fetchSessionBounds(date, asOf);
    if (bounds.isEmpty()) {
      return;
    }
    TradingDay day = new TradingDay(date, true, bounds.get().openUtc(), bounds.get().closeUtc());
    upsertDay(date, day);
    known.put(date, day);
  }

  private void upsertDay(LocalDate date, TradingDay day) {
    calendar.upsert(
        properties.calendarEngine(),
        properties.calendarMarket(),
        properties.calendarBoard(),
        date,
        day.trading(),
        day.openUtc(),
        day.closeUtc());
  }

  /** Торговый день с окном сессии из данных биржи; при недоступности — fallback на настройки. */
  private TradingDay tradingDay(LocalDate date, Instant asOf) {
    Optional<SessionBounds> bounds = fetchSessionBounds(date, asOf);
    if (bounds.isPresent()) {
      return new TradingDay(date, true, bounds.get().openUtc(), bounds.get().closeUtc());
    }
    log.warn("ISS не отдал границы сессии за {}: используются настройки", date);
    Instant openUtc = date.atTime(properties.sessionOpen()).atZone(MOSCOW).toInstant();
    Instant closeUtc = date.atTime(properties.sessionClose()).atZone(MOSCOW).toInstant();
    return new TradingDay(date, true, openUtc, closeUtc);
  }

  /**
   * Границы сессии дня из минутных свечей биржи: открытие — начало первого бара дня; закрытие —
   * конец последнего бара вечернего фрагмента, но только когда день уже закрылся ({@code at} не
   * раньше планового закрытия). Пока день идёт, вечерний фрагмент неполный и по его последнему бару
   * день сузился бы задолго до реального конца сессии (заявки встали бы вечером), поэтому закрытие
   * берётся плановым из {@code session-close}.
   *
   * <p>Пусто, если биржа не отдала открывающий бар или ответ вышел за пределы запрошенного дня: в
   * прогонах ISS возвращал бары с датами за пределами окна (close уезжал на месяц вперёд), и без
   * проверки некорректная граница закреплялась в кэше навсегда, отключая экспирацию DAY. Одна из
   * причин такого ответа — испорченный {@code from} (двойное percent-encoding, см. {@link
   * #get(URI)}): ISS не распознаёт дату и отдаёт бары чужого дня.
   */
  private Optional<SessionBounds> fetchSessionBounds(LocalDate date, Instant at) {
    return fetchCandleBounds(date)
        .flatMap(
            candles ->
                Optional.ofNullable(closeOf(date, at, candles, null))
                    .filter(close -> boundsWithinDay(date, candles.openUtc(), close))
                    .map(close -> new SessionBounds(candles.openUtc(), close)));
  }

  /**
   * Закрытие дня: фактический конец вечерних свечей, если день уже закрылся ({@code at} не раньше
   * планового закрытия); иначе — плановое из настроек, расширенное до уже закэшированного.
   *
   * <p>Расширение до закэшированного нужно сбою источника: он не должен сужать реальный конец дня.
   * Плановое закрытие приоритетнее закэшированного fallback-окна, иначе идущий день оставался бы
   * запертым в узком окне настроек, найденном дефекте.
   */
  private Instant closeOf(LocalDate date, Instant at, CandleBounds candles, TradingDay cached) {
    Instant planned = plannedClose(date);
    if (candles.eveningCloseUtc() != null && !at.isBefore(planned)) {
      return candles.eveningCloseUtc();
    }
    return widen(planned, cached);
  }

  /**
   * Плановое закрытие, расширенное до уже закэшированного: сбой источника не должен сужать день.
   */
  private static Instant widen(Instant planned, TradingDay cached) {
    Instant cachedClose = cached == null ? null : cached.closeUtc();
    return cachedClose != null && cachedClose.isAfter(planned) ? cachedClose : planned;
  }

  /**
   * Периодическая перезапись границ сессии недельного окна по данным биржи.
   *
   * <p>Смысл: {@link #tradingDays(Instant, Instant)} пропускает дни, уже присутствующие в кэше (при
   * полном кэше HTTP не вызывается вовсе), поэтому границы, записанные вслепую — из fallback-окна
   * настроек или плановым закрытием, — застыли бы навсегда. Заявка DAY экспирировалась бы не по
   * биржевой границе, а вне fallback-окна вообще не исполнялась бы (найденный дефект).
   *
   * <p>Ничего не пишется, если биржа не отдала открывающий бар дня: сбой источника не должен ни
   * сужать, ни расширять уже зафиксированный день.
   *
   * <p>Только для LIVE/RECORD: вызывающий ({@code TradingCalendarInitializer}) пропускает REPLAY,
   * где календарь строится из записанных снапшотов и сеть не используется.
   */
  @Transactional
  public void refreshLiveBounds(Instant at) {
    Objects.requireNonNull(at, "at");
    LocalDate toDate = at.atZone(MOSCOW).toLocalDate();
    for (LocalDate date = toDate.minusDays(BOUNDS_REFRESH_DAYS);
        !date.isAfter(toDate);
        date = date.plusDays(1)) {
      refreshBounds(date, at);
    }
  }

  /** Перезаписывает границы одного дня, если биржа отдала их отличными от закэшированных. */
  private void refreshBounds(LocalDate date, Instant at) {
    TradingDay cached = findDay(date).map(mapper::toDomain).orElse(null);
    if (cached != null && !cached.trading()) {
      return; // неторговый день календаря: границ не бывает
    }
    Optional<CandleBounds> candles = fetchCandleBounds(date);
    if (candles.isEmpty()) {
      return;
    }
    Instant openUtc = candles.get().openUtc();
    Instant closeUtc = closeOf(date, at, candles.get(), cached);
    if (!boundsWithinDay(date, openUtc, closeUtc)
        || (cached != null
            && openUtc.equals(cached.openUtc())
            && closeUtc.equals(cached.closeUtc()))) {
      return; // некорректные границы или уже закэшированы без изменений
    }
    upsertDay(date, new TradingDay(date, true, openUtc, closeUtc));
    log.info("Границы сессии {} обновлены из биржи: {} – {}", date, openUtc, closeUtc);
  }

  /**
   * Открытие и (возможное) вечернее закрытие дня из минутных свечей; пусто, если дня нет в свечах
   * или вечерний хвост вышел за его пределы.
   *
   * <p>Запрос вечернего фрагмента делается даже без открывающего бара: число обращений к ISS на
   * день стабильно (два) и закреплено тестами календаря, а экономия одного пустого запроса не стоит
   * разрыва этого контракта.
   */
  private Optional<CandleBounds> fetchCandleBounds(LocalDate date) {
    try {
      Instant openUtc = firstMinuteBarBegin(date);
      Instant eveningCloseUtc = lastMinuteBarEnd(date);
      if (openUtc == null) {
        return Optional.empty();
      }
      if (eveningCloseUtc != null && !boundsWithinDay(date, openUtc, eveningCloseUtc)) {
        return Optional.empty();
      }
      return Optional.of(new CandleBounds(openUtc, eveningCloseUtc));
    } catch (RestClientException | JacksonException | AlgoApiException exception) {
      log.error("Не удалось получить границы сессии {} из ISS: {}", date, exception.getMessage());
      return Optional.empty();
    }
  }

  /** Плановое закрытие торгового дня по Москве из настроек ({@code session-close}) в UTC. */
  private Instant plannedClose(LocalDate date) {
    return date.atTime(properties.sessionClose()).atZone(MOSCOW).toInstant();
  }

  /**
   * Обе границы обязаны лежать внутри запрошенной календарной даты по Москве, а закрытие — строго
   * после открытия. Вечерний фрагмент сессии заканчивается в тот же календарный день, поэтому выход
   * за полночь — признак некорректного ответа источника.
   */
  private static boolean boundsWithinDay(LocalDate date, Instant openUtc, Instant closeUtc) {
    Instant dayStart = date.atStartOfDay(MOSCOW).toInstant();
    Instant dayEnd = date.plusDays(1).atStartOfDay(MOSCOW).toInstant();
    return !openUtc.isBefore(dayStart)
        && openUtc.isBefore(dayEnd)
        && closeUtc.isAfter(openUtc)
        && !closeUtc.isAfter(dayEnd);
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
    URI uri = candlesUri(from, date);
    String body = get(uri);
    TableReader candles = TableReader.of(objectMapper.readTree(body), "candles");
    // Пустой ответ при живых торгах означает испорченный запрос (см. get(URI)), а не выходной.
    log.debug("Минутные свечи {}: {} строк ({})", date, candles.size(), uri);
    return candles;
  }

  /**
   * GET ISS по готовому {@link URI}. Строка в {@code RestClient.uri(String)} трактуется как
   * URI-шаблон и кодируется повторно: {@code from=2026-11-18 18:00:00} превращается в {@code
   * from=...%252018:00:00}, биржа дату не распознаёт и отдаёт бары чужого дня (замерено на живом
   * ISS). Поэтому URI собирается с одним {@code encode()} и передаётся объектом.
   */
  private String get(URI uri) {
    return restClient.get().uri(uri).retrieve().body(String.class);
  }

  /** Запрос минутных свечей дня: {@code from} — дата (открытие) или дата-время 18:00 (хвост). */
  private URI candlesUri(String from, LocalDate date) {
    return UriComponentsBuilder.fromUriString(properties.issBaseUrl())
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
        .build()
        .encode()
        .toUri();
  }

  /** Границы торговой сессии дня (UTC). */
  private record SessionBounds(Instant openUtc, Instant closeUtc) {}

  /**
   * Границы, отданные минутными свечами дня: {@code openUtc} — всегда известно, {@code
   * eveningCloseUtc} — {@code null}, пока вечернего фрагмента торгов нет (день идёт).
   */
  private record CandleBounds(Instant openUtc, Instant eveningCloseUtc) {}

  private Optional<Set<LocalDate>> fetchTradingDates(LocalDate from, LocalDate to) {
    URI uri =
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
            .build()
            .encode()
            .toUri();
    try {
      String body = get(uri);
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
