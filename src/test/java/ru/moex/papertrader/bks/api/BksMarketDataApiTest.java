package ru.moex.papertrader.bks.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class BksMarketDataApiTest extends BksApiTestSupport {

  private static final String MARKET = BASE_PATH + "/v1";

  @Test
  void quotesReturnContractFields() {
    ApiResponse response =
        post(
            MARKET + "/quotes",
            tradeToken(),
            Map.of("instruments", List.of(Map.of("ticker", "SBER", "classCode", "TQBR"))));

    assertThat(response.status()).isEqualTo(200);
    var quote = response.json().path("records").get(0);
    assertThat(quote.path("ticker").asText()).isEqualTo("SBER");
    assertThat(quote.path("classCode").asText()).isEqualTo("TQBR");
    assertThat(quote.path("currency").asText()).isEqualTo("RUB");
    assertThat(quote.path("securityTradingStatus").asInt()).isEqualTo(17);
    assertThat(quote.path("bid").asDouble()).isEqualTo(285.49);
    assertThat(quote.path("offer").asDouble()).isEqualTo(285.51);
    assertThat(quote.path("last").asDouble()).isEqualTo(285.5);
    assertThat(quote.path("dateTime").asText()).isNotBlank();
  }

  @Test
  void quotesSkipUnknownInstruments() {
    ApiResponse response =
        post(
            MARKET + "/quotes",
            tradeToken(),
            Map.of("instruments", List.of(Map.of("ticker", "UNKNOWN", "classCode", "TQBR"))));

    assertThat(response.status()).isEqualTo(200);
    assertThat(response.json().path("records").size()).isZero();
  }

  @Test
  void quotesWithoutInstrumentsReturnBadRequest() {
    ApiResponse response = post(MARKET + "/quotes", tradeToken(), Map.of("instruments", List.of()));

    assertThat(response.status()).isEqualTo(400);
    assertThat(response.json().path("type").asText()).isEqualTo("VALIDATION_ERROR");
  }

  @Test
  void orderBookReturnsRequestedDepth() {
    ApiResponse response =
        get(MARKET + "/order-book?ticker=SBER&classCode=TQBR&depth=5", tradeToken());

    assertThat(response.status()).isEqualTo(200);
    var book = response.json();
    assertThat(book.path("depth").asInt()).isEqualTo(5);
    assertThat(book.path("bids").size()).isEqualTo(5);
    assertThat(book.path("asks").size()).isEqualTo(5);
    assertThat(book.path("bidVolume").asInt()).isEqualTo(1500);
    assertThat(book.path("askVolume").asInt()).isEqualTo(1500);
    assertThat(book.path("bids").get(0).path("quantity").asLong()).isEqualTo(100L);
    assertThat(book.path("asks").get(0).path("price").asDouble()).isEqualTo(285.51);
  }

  @Test
  void orderBookDepthAboveLimitReturnsBadRequest() {
    ApiResponse response =
        get(MARKET + "/order-book?ticker=SBER&classCode=TQBR&depth=21", tradeToken());

    assertThat(response.status()).isEqualTo(400);
  }

  @Test
  void orderBookForUnknownInstrumentReturnsNotFound() {
    ApiResponse response = get(MARKET + "/order-book?ticker=NOPE&classCode=TQBR", tradeToken());

    assertThat(response.status()).isEqualTo(404);
    assertThat(response.json().path("type").asText()).isEqualTo("NOT_FOUND");
  }

  @Test
  void candlesReturnBarsForTimeFrame() {
    ApiResponse response =
        get(
            MARKET
                + "/candles-chart?classCode=TQBR&ticker=SBER"
                + "&startDate=2025-11-14T07:00:00Z&endDate=2025-11-14T11:00:00Z&timeFrame=H1",
            tradeToken());

    assertThat(response.status()).isEqualTo(200);
    var chart = response.json();
    assertThat(chart.path("ticker").asText()).isEqualTo("SBER");
    assertThat(chart.path("classCode").asText()).isEqualTo("TQBR");
    assertThat(chart.path("timeFrame").asText()).isEqualTo("H1");
    assertThat(chart.path("bars").size()).isEqualTo(5);
    assertThat(chart.path("bars").get(0).path("open").asDouble()).isEqualTo(285.5);
    assertThat(chart.path("bars").get(0).path("time").asText()).isEqualTo("2025-11-14T07:00:00Z");
  }

  @Test
  void candlesWithUnknownTimeFrameReturnBadRequest() {
    ApiResponse response =
        get(
            MARKET
                + "/candles-chart?classCode=TQBR&ticker=SBER"
                + "&startDate=2025-11-14T07:00:00Z&endDate=2025-11-14T11:00:00Z&timeFrame=X1",
            tradeToken());

    assertThat(response.status()).isEqualTo(400);
  }

  @Test
  void candlesWithTooWideRangeReturnBadRequest() {
    ApiResponse response =
        get(
            MARKET
                + "/candles-chart?classCode=TQBR&ticker=SBER"
                + "&startDate=2025-01-01T00:00:00Z&endDate=2025-12-31T00:00:00Z&timeFrame=M1",
            tradeToken());

    assertThat(response.status()).isEqualTo(400);
  }

  @Test
  void candlesWithInvalidDateReturnBadRequest() {
    ApiResponse response =
        get(
            MARKET
                + "/candles-chart?classCode=TQBR&ticker=SBER"
                + "&startDate=not-a-date&endDate=2025-11-14T11:00:00Z&timeFrame=H1",
            tradeToken());

    assertThat(response.status()).isEqualTo(400);
  }

  @Test
  void marketDataWithoutTokenReturnsUnauthorized() {
    ApiResponse response = get(MARKET + "/order-book?ticker=SBER&classCode=TQBR", null);

    assertThat(response.status()).isEqualTo(401);
  }
}
