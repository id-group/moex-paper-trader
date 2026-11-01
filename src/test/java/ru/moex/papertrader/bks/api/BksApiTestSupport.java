package ru.moex.papertrader.bks.api;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * База интеграционных тестов API БКС: поднимает приложение на случайном порту и ходит по HTTP.
 *
 * <p>DTO-тесты проверяют реальный JSON, поэтому клиент работает с сырыми строками и деревом
 * Jackson, а не с контрактными record-классами.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
abstract class BksApiTestSupport {

  protected static final String BASE_PATH = "/api/bks";
  protected static final String TOKEN_PATH =
      BASE_PATH + "/trade-api-keycloak/realms/tradeapi/protocol/openid-connect/token";
  protected static final String TRADE_REFRESH_TOKEN = "test-trade-refresh-token";
  protected static final String READ_REFRESH_TOKEN = "test-read-refresh-token";
  protected static final String WRITE_CLIENT = "trade-api-write";
  protected static final String READ_CLIENT = "trade-api-read";

  private static final JsonMapper JSON = JsonMapper.builder().build();
  private static final HttpClient HTTP = HttpClient.newHttpClient();

  @LocalServerPort private int port;

  private String tradeToken;
  private String readToken;

  protected String tradeToken() {
    if (tradeToken == null) {
      tradeToken = accessToken(TRADE_REFRESH_TOKEN, WRITE_CLIENT);
    }
    return tradeToken;
  }

  protected String readToken() {
    if (readToken == null) {
      readToken = accessToken(READ_REFRESH_TOKEN, READ_CLIENT);
    }
    return readToken;
  }

  protected String accessToken(String refreshToken, String clientId) {
    String form =
        "client_id="
            + encode(clientId)
            + "&refresh_token="
            + encode(refreshToken)
            + "&grant_type=refresh_token";
    ApiResponse response = postForm(TOKEN_PATH, form);
    if (response.status() != 200) {
      throw new IllegalStateException("Не удалось получить access-токен: " + response.body());
    }
    return response.json().path("access_token").asText();
  }

  protected ApiResponse get(String path, String token) {
    HttpRequest.Builder builder = request(path).GET();
    return send(builder, token);
  }

  protected ApiResponse post(String path, String token, Object body) {
    return postJson(path, token, JSON.writeValueAsString(body));
  }

  protected ApiResponse postJson(String path, String token, String body) {
    HttpRequest.Builder builder =
        request(path)
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
    return send(builder, token);
  }

  protected ApiResponse postForm(String path, String form) {
    HttpRequest.Builder builder =
        request(path)
            .header("Content-Type", "application/x-www-form-urlencoded")
            .POST(HttpRequest.BodyPublishers.ofString(form, StandardCharsets.UTF_8));
    return send(builder, null);
  }

  private HttpRequest.Builder request(String path) {
    return HttpRequest.newBuilder(URI.create("http://localhost:" + port + path));
  }

  private ApiResponse send(HttpRequest.Builder builder, String token) {
    if (token != null) {
      builder.header("Authorization", "Bearer " + token);
    }
    try {
      HttpResponse<String> response =
          HTTP.send(builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
      return new ApiResponse(response.statusCode(), response.body());
    } catch (IOException exception) {
      throw new IllegalStateException("HTTP-вызов не удался", exception);
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("HTTP-вызов прерван", exception);
    }
  }

  private static String encode(String value) {
    return URLEncoder.encode(value, StandardCharsets.UTF_8);
  }

  /** Ответ API с разбором JSON по запросу. */
  protected record ApiResponse(int status, String body) {

    protected JsonNode json() {
      return JSON.readTree(body);
    }

    protected String text(String field) {
      return json().path(field).asText();
    }

    protected static JsonNode from(Map<String, Object> values) {
      return JSON.valueToTree(values);
    }
  }
}
