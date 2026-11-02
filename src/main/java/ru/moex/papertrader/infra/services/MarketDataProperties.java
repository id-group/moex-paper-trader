package ru.moex.papertrader.infra.services;

import java.time.Duration;
import java.time.LocalTime;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Настройки хранения рыночных данных и торгового календаря.
 *
 * <p>Биндинг из {@code emulator.marketdata.*}: ключи yml — {@code retention-days}, {@code
 * calendar-engine/market/board/security}, {@code iss-base-url}, {@code connect/read-timeout-ms},
 * {@code session-open/close} (МСК). Все значения имеют безопасные умолчания, поэтому сервис можно
 * собрать и без конфигурации.
 *
 * <p>Умолчания: срок хранения снапшотов — 7 дней, календарь акций основного режима TQBR, базовый
 * адрес MOEX ISS — {@code https://iss.moex.com} (домен {@code iss.moex.ru} выведен из эксплуатации
 * и не разрешается), fallback-окно сессии — 10:00–18:50 по Москве (основное окно — из данных
 * биржи).
 *
 * @param retentionDays срок хранения записанных снапшотов, дни
 * @param calendarEngine движок ISS для календаря ({@code stock})
 * @param calendarMarket рынок ISS ({@code shares})
 * @param calendarBoard режим торгов ISS ({@code TQBR})
 * @param calendarSecurity эталонный инструмент ISS, по сделкам которого определяется торговый день
 * @param issBaseUrl базовый адрес MOEX ISS без завершающего слэша
 * @param connectTimeoutMs таймаут установки соединения, мс
 * @param readTimeoutMs таймаут чтения ответа, мс
 * @param sessionOpen fallback-открытие сессии по Москве (если биржа не отдала границы)
 * @param sessionClose fallback-закрытие сессии по Москве (если биржа не отдала границы)
 */
@ConfigurationProperties(prefix = "emulator.marketdata")
public record MarketDataProperties(
    int retentionDays,
    String calendarEngine,
    String calendarMarket,
    String calendarBoard,
    String calendarSecurity,
    String issBaseUrl,
    int connectTimeoutMs,
    int readTimeoutMs,
    LocalTime sessionOpen,
    LocalTime sessionClose) {

  public static final int DEFAULT_RETENTION_DAYS = 7;
  public static final String DEFAULT_CALENDAR_ENGINE = "stock";
  public static final String DEFAULT_CALENDAR_MARKET = "shares";
  public static final String DEFAULT_CALENDAR_BOARD = "TQBR";
  public static final String DEFAULT_CALENDAR_SECURITY = "SBER";
  public static final String DEFAULT_ISS_BASE_URL = "https://iss.moex.com";
  public static final int DEFAULT_CONNECT_TIMEOUT_MS = 2_000;
  public static final int DEFAULT_READ_TIMEOUT_MS = 5_000;
  public static final LocalTime DEFAULT_SESSION_OPEN = LocalTime.of(10, 0);
  public static final LocalTime DEFAULT_SESSION_CLOSE = LocalTime.of(18, 50);

  public MarketDataProperties {
    retentionDays = retentionDays > 0 ? retentionDays : DEFAULT_RETENTION_DAYS;
    calendarEngine = textOr(calendarEngine, DEFAULT_CALENDAR_ENGINE);
    calendarMarket = textOr(calendarMarket, DEFAULT_CALENDAR_MARKET);
    calendarBoard = textOr(calendarBoard, DEFAULT_CALENDAR_BOARD);
    calendarSecurity = textOr(calendarSecurity, DEFAULT_CALENDAR_SECURITY);
    issBaseUrl = textOr(issBaseUrl, DEFAULT_ISS_BASE_URL);
    connectTimeoutMs = connectTimeoutMs > 0 ? connectTimeoutMs : DEFAULT_CONNECT_TIMEOUT_MS;
    readTimeoutMs = readTimeoutMs > 0 ? readTimeoutMs : DEFAULT_READ_TIMEOUT_MS;
    sessionOpen = sessionOpen != null ? sessionOpen : DEFAULT_SESSION_OPEN;
    sessionClose = sessionClose != null ? sessionClose : DEFAULT_SESSION_CLOSE;
  }

  /** Настройки по умолчанию — для тестов и запуска без конфигурации. */
  public static MarketDataProperties defaults() {
    return new MarketDataProperties(0, null, null, null, null, null, 0, 0, null, null);
  }

  /** Таймаут установки соединения в форме, пригодной для HTTP-клиента. */
  public Duration connectTimeout() {
    return Duration.ofMillis(connectTimeoutMs);
  }

  /** Таймаут чтения ответа в форме, пригодной для HTTP-клиента. */
  public Duration readTimeout() {
    return Duration.ofMillis(readTimeoutMs);
  }

  private static String textOr(String value, String fallback) {
    return value == null || value.isBlank() ? fallback : value;
  }
}
