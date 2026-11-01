package ru.moex.papertrader.bks.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class BksInstrumentApiTest extends BksApiTestSupport {

  private static final String V1 = BASE_PATH + "/v1";

  @Test
  void instrumentsByTickersReturnDirectoryFields() {
    ApiResponse response =
        post(V1 + "/instruments/by-tickers", tradeToken(), Map.of("tickers", List.of("SBER")));

    assertThat(response.status()).isEqualTo(200);
    assertThat(response.json().isArray()).isTrue();
    var instrument = response.json().get(0);
    assertThat(instrument.path("ticker").asText()).isEqualTo("SBER");
    assertThat(instrument.path("type").asText()).isEqualTo("STOCK");
    assertThat(instrument.path("instrumentType").asText()).isEqualTo("STOCK");
    assertThat(instrument.path("isin").asText()).isEqualTo("RU0009029540");
    assertThat(instrument.path("lotSize").asDouble()).isEqualTo(10.0);
    assertThat(instrument.path("primaryBoard").asText()).isEqualTo("TQBR");
    assertThat(instrument.path("boards").get(0).path("classCode").asText()).isEqualTo("TQBR");
    assertThat(instrument.path("boards").get(0).path("exchange").asText()).isEqualTo("MOEX");
    assertThat(instrument.path("isCanShort").asBoolean()).isTrue();
  }

  @Test
  void instrumentsByUnknownTickerReturnEmptyArray() {
    ApiResponse response =
        post(V1 + "/instruments/by-tickers", tradeToken(), Map.of("tickers", List.of("NOPE")));

    assertThat(response.status()).isEqualTo(200);
    assertThat(response.json().size()).isZero();
  }

  @Test
  void instrumentsWithoutTickersReturnBadRequest() {
    ApiResponse response =
        post(V1 + "/instruments/by-tickers", tradeToken(), Map.of("tickers", List.of()));

    assertThat(response.status()).isEqualTo(400);
  }

  @Test
  void tradingStatusReturnsOpenSession() {
    ApiResponse response = get(V1 + "/trading-schedule/status", tradeToken());

    assertThat(response.status()).isEqualTo(200);
    assertThat(response.json().path("tradingSessionTypeId").asInt()).isEqualTo(1);
    assertThat(response.json().path("tradingSessionType").asText()).isEqualTo("Основная сессия");
    assertThat(response.json().path("tradingSessionStatus").asText()).isEqualTo("OPEN");
    assertThat(response.json().path("nextSessionDate").asText()).isNotBlank();
  }

  @Test
  void instrumentsWithoutTokenReturnUnauthorized() {
    ApiResponse response =
        post(V1 + "/instruments/by-tickers", null, Map.of("tickers", List.of("SBER")));

    assertThat(response.status()).isEqualTo(401);
  }
}
