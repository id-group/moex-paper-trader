package ru.moex.papertrader.bks.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;

class BksTradesApiTest extends BksApiTestSupport {

  private static final String TRADES = BASE_PATH + "/v1/trades/search";

  @Test
  void searchReturnsEmptyPageUntilExecutionAppears() {
    ApiResponse response = post(TRADES, tradeToken(), Map.of());

    assertThat(response.status()).isEqualTo(200);
    assertThat(response.json().path("records").isArray()).isTrue();
    assertThat(response.json().path("records").size()).isZero();
    assertThat(response.json().path("totalRecords").asLong()).isZero();
    assertThat(response.json().path("totalPages").asInt()).isZero();
  }

  @Test
  void searchWithInvalidSideReturnsBadRequest() {
    ApiResponse response = post(TRADES, tradeToken(), Map.of("side", "9"));

    assertThat(response.status()).isEqualTo(400);
    assertThat(response.json().path("type").asText()).isEqualTo("VALIDATION_ERROR");
  }

  @Test
  void searchWithInvalidPageReturnsBadRequest() {
    ApiResponse response = post(TRADES + "?page=-1", tradeToken(), Map.of());

    assertThat(response.status()).isEqualTo(400);
  }

  @Test
  void searchWithoutTokenReturnsUnauthorized() {
    ApiResponse response = post(TRADES, null, Map.of());

    assertThat(response.status()).isEqualTo(401);
  }
}
