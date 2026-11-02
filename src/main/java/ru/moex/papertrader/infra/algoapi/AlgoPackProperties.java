package ru.moex.papertrader.infra.algoapi;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Настройки HTTP-клиента AlgoPack. Record с Spring-биндингом {@code emulator.algoapi.*}; пакет
 * остаётся тестируемым в изоляции — свойства можно собрать вручную ({@link #defaults()}).
 *
 * <p>Токен передаётся в заголовке {@code X-AlgoPack-Token}. Значение по умолчанию пустое —
 * заголовок не отправляется; реальный токен задаётся только через переменную окружения (skill
 * marketdata-modes), в git и конфиг не попадает.
 *
 * <p>{@code baseUrl} по умолчанию — {@code https://algo-api.moex.com}; в актуальной документации
 * (moexalgo.github.io) публичный шлюз указан как {@code https://apim.moex.com}, поэтому значение
 * переопределяется конфигурацией.
 *
 * @param baseUrl базовый адрес API
 * @param token токен доступа AlgoPack (пустая строка — без авторизации)
 * @param connectTimeoutMs таймаут установления соединения, мс
 * @param readTimeoutMs таймаут чтения ответа, мс
 * @param maxRetries максимальное число повторных попыток при сетевой ошибке или 5xx
 * @param retryDelayMs пауза между попытками, мс
 * @param engine код торговой системы ISS (по умолчанию {@code stock})
 * @param market код рынка ISS (по умолчанию {@code shares})
 * @param board режим торгов ISS (по умолчанию {@code TQBR})
 */
@ConfigurationProperties(prefix = "emulator.algoapi")
public record AlgoPackProperties(
    String baseUrl,
    String token,
    int connectTimeoutMs,
    int readTimeoutMs,
    int maxRetries,
    long retryDelayMs,
    String engine,
    String market,
    String board) {

  /** Адрес API AlgoPack по умолчанию. */
  public static final String DEFAULT_BASE_URL = "https://algo-api.moex.com";

  public AlgoPackProperties {
    baseUrl = requireText(baseUrl, "baseUrl");
    token = token == null ? "" : token;
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
  public static AlgoPackProperties defaults() {
    return new AlgoPackProperties(
        DEFAULT_BASE_URL, "", 2_000, 5_000, 2, 500, "stock", "shares", "TQBR");
  }

  private static String requireText(String value, String name) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(name + " не может быть пустым");
    }
    return value;
  }
}
