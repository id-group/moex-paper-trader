package ru.moex.papertrader.infra.algoapi;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Внутренний HTTP-слой клиентов рыночных данных: GET JSON с таймаутами и ручным ретраем.
 *
 * <p>Ретрай выполняется только для сетевых ошибок и ответов 5xx; 4xx сразу превращается в {@link
 * AlgoApiException}. Все исключения Spring/Jackson наружу не выходят.
 */
final class JsonHttpClient {

  private static final Logger log = LoggerFactory.getLogger(JsonHttpClient.class);
  private static final ObjectMapper MAPPER = new ObjectMapper();

  private final RestClient restClient;
  private final int maxRetries;
  private final long retryDelayMs;
  private final String apiName;
  private final Map<String, String> headers;

  JsonHttpClient(
      RestClient.Builder builder,
      String baseUrl,
      int connectTimeoutMs,
      int readTimeoutMs,
      int maxRetries,
      long retryDelayMs,
      String apiName,
      Map<String, String> headers) {
    Objects.requireNonNull(builder, "builder");
    Objects.requireNonNull(baseUrl, "baseUrl");
    HttpClient httpClient =
        HttpClient.newBuilder()
            .connectTimeout(Duration.ofMillis(connectTimeoutMs))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
    JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
    requestFactory.setReadTimeout(Duration.ofMillis(readTimeoutMs));
    this.restClient = builder.baseUrl(baseUrl).requestFactory(requestFactory).build();
    this.maxRetries = maxRetries;
    this.retryDelayMs = retryDelayMs;
    this.apiName = apiName;
    this.headers = Map.copyOf(headers);
  }

  /**
   * Выполняет GET и разбирает ответ как JSON.
   *
   * @param path путь относительно baseUrl (без шаблонов)
   * @param query параметры запроса; значения кодируются Spring
   */
  JsonNode get(String path, Map<String, String> query) {
    return withRetry(
        path,
        () -> {
          log.debug("{}: GET {}?{}", apiName, path, query);
          String body =
              restClient
                  .get()
                  .uri(
                      builder -> {
                        var uriBuilder = builder.path(path);
                        query.forEach(uriBuilder::queryParam);
                        return uriBuilder.build();
                      })
                  .headers(httpHeaders -> headers.forEach(httpHeaders::set))
                  .retrieve()
                  .body(String.class);
          return parse(path, body);
        });
  }

  private JsonNode parse(String path, String body) {
    if (body == null || body.isBlank()) {
      throw new AlgoApiException(apiName + ": пустой ответ на запрос " + path);
    }
    try {
      return MAPPER.readTree(body);
    } catch (JacksonException e) {
      throw new AlgoApiException(apiName + ": некорректный JSON в ответе на запрос " + path, e);
    }
  }

  private JsonNode withRetry(String path, Supplier<JsonNode> call) {
    for (int attempt = 0; ; attempt++) {
      try {
        return call.get();
      } catch (HttpClientErrorException e) {
        throw new AlgoApiException(
            apiName + ": запрос " + path + " отклонён со статусом " + e.getStatusCode(), e);
      } catch (RestClientException e) {
        if (!retryable(e) || attempt >= maxRetries) {
          throw new AlgoApiException(
              apiName + ": запрос " + path + " не выполнен после " + (attempt + 1) + " попыток", e);
        }
        log.warn(
            "{}: попытка {}/{} запроса {} не удалась: {}",
            apiName,
            attempt + 1,
            maxRetries + 1,
            path,
            e.getMessage());
        sleep();
      }
    }
  }

  private static boolean retryable(RestClientException e) {
    return e instanceof HttpServerErrorException || e instanceof ResourceAccessException;
  }

  private void sleep() {
    if (retryDelayMs <= 0) {
      return;
    }
    try {
      Thread.sleep(retryDelayMs);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new AlgoApiException(apiName + ": ожидание повтора прервано", e);
    }
  }
}
