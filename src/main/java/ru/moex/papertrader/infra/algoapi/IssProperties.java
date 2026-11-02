package ru.moex.papertrader.infra.algoapi;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Настройки HTTP-клиента MOEX ISS. Record с Spring-биндингом {@code emulator.iss.*}; пакет остаётся
 * тестируемым в изоляции — свойства можно собрать вручную ({@link #defaults()}).
 *
 * @param baseUrl базовый адрес ISS
 * @param connectTimeoutMs таймаут установления соединения, мс
 * @param readTimeoutMs таймаут чтения ответа, мс
 * @param maxRetries максимальное число повторных попыток при сетевой ошибке или 5xx
 * @param retryDelayMs пауза между попытками, мс
 * @param engine код торговой системы ISS (по умолчанию {@code stock})
 * @param market код рынка ISS (по умолчанию {@code shares})
 * @param board режим торгов ISS (по умолчанию {@code TQBR})
 */
@ConfigurationProperties(prefix = "emulator.iss")
public record IssProperties(
    String baseUrl,
    int connectTimeoutMs,
    int readTimeoutMs,
    int maxRetries,
    long retryDelayMs,
    String engine,
    String market,
    String board) {

  /** Адрес MOEX ISS по умолчанию: домен iss.moex.ru выведен из эксплуатации. */
  public static final String DEFAULT_BASE_URL = "https://iss.moex.com";

  public IssProperties {
    baseUrl = requireText(baseUrl, "baseUrl");
    engine = requireText(engine, "engine");
    market = requireText(market, "market");
    board = requireText(board, "board");
    if (connectTimeoutMs <= 0) {
      throw new IllegalArgumentException("connectTimeoutMs должен быть положительным");
    }
    if (readTimeoutMs <= 0) {
      throw new IllegalArgumentException("readTimeoutMs должен быть положительным");
    }
    if (maxRetries < 0) {
      throw new IllegalArgumentException("maxRetries не может быть отрицательным");
    }
    if (retryDelayMs < 0) {
      throw new IllegalArgumentException("retryDelayMs не может быть отрицательным");
    }
  }

  /** Настройки по умолчанию для акций основного режима TQBR. */
  public static IssProperties defaults() {
    return new IssProperties(DEFAULT_BASE_URL, 2_000, 5_000, 2, 500, "stock", "shares", "TQBR");
  }

  private static String requireText(String value, String name) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(name + " не может быть пустым");
    }
    return value;
  }
}
