package ru.moex.papertrader.bks.contract;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import ru.moex.papertrader.TestJson;

class BksContractSerializationTest {

  @Test
  void tokenResponseUsesKeycloakFieldNames() {
    TokenResponse token =
        new TokenResponse(
            "bks-token", 86400, 7776000, "refresh", "bearer", "0", "session", "trade-api-write");

    var json = TestJson.MAPPER.valueToTree(token);

    assertThat(json.path("access_token").asText()).isEqualTo("bks-token");
    assertThat(json.path("expires_in").asLong()).isEqualTo(86400);
    assertThat(json.path("refresh_expires_in").asLong()).isEqualTo(7776000);
    assertThat(json.path("refresh_token").asText()).isEqualTo("refresh");
    assertThat(json.path("token_type").asText()).isEqualTo("bearer");
    assertThat(json.path("not-before-policy").asText()).isEqualTo("0");
    assertThat(json.path("session_state").asText()).isEqualTo("session");
  }

  @Test
  void tokenResponseRoundTripsFromContractJson() {
    String json =
        """
        {"access_token":"a","expires_in":86400,"refresh_expires_in":7776000,
         "refresh_token":"r","token_type":"bearer","not-before-policy":"0",
         "session_state":"s","scope":"trade-api-read"}
        """;

    TokenResponse token = TestJson.MAPPER.readValue(json, TokenResponse.class);

    assertThat(token.accessToken()).isEqualTo("a");
    assertThat(token.notBeforePolicy()).isEqualTo("0");
    assertThat(token.scope()).isEqualTo("trade-api-read");
  }

  @Test
  void errorResponseFollowsCommonContract() {
    ErrorResponse error =
        new ErrorResponse(
            1760000000000L,
            "trace",
            ErrorType.VALIDATION_ERROR,
            List.of(
                new ErrorDetail(
                    "VALIDATION_ERROR", "side", Map.of("message", Map.of("text", "bad side")))),
            Map.of());

    var json = TestJson.MAPPER.valueToTree(error);

    assertThat(json.path("timestamp").asLong()).isEqualTo(1760000000000L);
    assertThat(json.path("traceId").asText()).isEqualTo("trace");
    assertThat(json.path("type").asText()).isEqualTo("VALIDATION_ERROR");
    assertThat(json.path("errors").get(0).path("type").asText()).isEqualTo("VALIDATION_ERROR");
    assertThat(json.path("errors").get(0).path("field").asText()).isEqualTo("side");
    assertThat(json.path("errors").get(0).path("payload").path("message").path("text").asText())
        .isEqualTo("bad side");
  }

  @Test
  void orderDataSerializesDatesAsIsoStringsAndOmitsNulls() {
    OrderData data =
        new OrderData(
            "ORDER_STATUS",
            "0",
            "0",
            BigDecimal.TEN,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.TEN,
            "SBER",
            "TQBR",
            "1",
            "2",
            null,
            "20261101-TQBR-1",
            null,
            new BigDecimal("285.50"),
            "RUB",
            "PAPER-0001",
            OffsetDateTime.parse("2026-11-01T10:00:00Z"),
            LocalDate.parse("2026-11-01"),
            "1",
            null,
            null,
            null,
            "MOEX",
            null,
            1,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null);

    var json = TestJson.MAPPER.valueToTree(data);

    assertThat(json.path("transactionTime").asText()).isEqualTo("2026-11-01T10:00:00Z");
    assertThat(json.path("tradeDate").asText()).isEqualTo("2026-11-01");
    assertThat(json.path("timeInForce").asInt()).isEqualTo(1);
    assertThat(json.path("stopPrice").isMissingNode()).isTrue();
    assertThat(json.path("cancelReason").isMissingNode()).isTrue();
  }

  @Test
  void orderSearchRecordRoundTrips() {
    OrdersSearchResponse record =
        new OrdersSearchResponse(
            1L,
            "20261101-TQBR-1",
            "PAPER-0001",
            null,
            BigDecimal.ZERO,
            OffsetDateTime.parse("2026-11-01T10:00:00Z"),
            LocalDate.parse("2026-11-01"),
            OffsetDateTime.parse("2026-11-01T10:00:00Z"),
            "SBER",
            "TQBR",
            null,
            null,
            new BigDecimal("285.50"),
            "RUB",
            BigDecimal.TEN,
            BigDecimal.TEN,
            BigDecimal.ZERO,
            null,
            null,
            null,
            null,
            3,
            2,
            1,
            BigDecimal.ONE,
            BigDecimal.ONE,
            BigDecimal.ZERO,
            null,
            null,
            null,
            null,
            null,
            null,
            null);

    OrdersSearchResponse restored =
        TestJson.MAPPER.readValue(
            TestJson.MAPPER.writeValueAsString(record), OrdersSearchResponse.class);

    assertThat(restored).isEqualTo(record);
  }

  @Test
  void portfolioSerializesContractFieldNames() {
    Portfolio portfolio =
        new Portfolio(
            "moneyLimit",
            null,
            null,
            "PAPER-0001",
            "MOEX",
            "RUB",
            "Российский рубль",
            null,
            "RUB",
            "RUSSIA",
            "CURRENCY",
            "T0",
            new BigDecimal("1000000.00"),
            BigDecimal.ZERO,
            BigDecimal.ONE,
            BigDecimal.ONE,
            new BigDecimal("1000000.00"),
            new BigDecimal("1000000.00"),
            null,
            null,
            new BigDecimal("1000000.00"),
            new BigDecimal("1000000.00"),
            null,
            null,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.valueOf(100),
            2,
            new BigDecimal("0.01"),
            "CURRENCY",
            "RUB",
            null,
            BigDecimal.ZERO,
            null,
            false,
            false,
            BigDecimal.ZERO,
            BigDecimal.ONE,
            null);

    var json = TestJson.MAPPER.valueToTree(portfolio);

    assertThat(json.path("balanceValueRub").asDouble()).isEqualTo(1_000_000.0);
    assertThat(json.path("lockedForFutures").asDouble()).isZero();
    assertThat(json.path("subAccountId").isMissingNode()).isTrue();

    Portfolio restored =
        TestJson.MAPPER.readValue(TestJson.MAPPER.writeValueAsString(portfolio), Portfolio.class);
    assertThat(restored).isEqualTo(portfolio);
  }

  @Test
  void createOrderRequestIgnoresUnknownFields() {
    String json =
        """
        {"clientOrderId":"00000000-0000-0000-0000-000000000001","side":"1",
         "orderType":"2","orderQuantity":1,"ticker":"SBER","classCode":"TQBR",
         "price":285.5,"futureField":"ignored"}
        """;

    CreateOrderRequest request = TestJson.MAPPER.readValue(json, CreateOrderRequest.class);

    assertThat(request.clientOrderId()).isEqualTo("00000000-0000-0000-0000-000000000001");
    assertThat(request.price()).isEqualByComparingTo("285.5");
  }

  @Test
  void orderBookResponseSerializesNestedVolumes() {
    OrderBookRestResponse book =
        new OrderBookRestResponse(
            1,
            OffsetDateTime.parse("2026-11-01T10:00:00Z"),
            "SBER",
            "TQBR",
            100,
            200,
            List.of(new Volume(new BigDecimal("285.49"), 100L)),
            List.of(new Volume(new BigDecimal("285.51"), 200L)));

    var json = TestJson.MAPPER.valueToTree(book);

    assertThat(json.path("bids").get(0).path("price").asDouble()).isEqualTo(285.49);
    assertThat(json.path("bids").get(0).path("quantity").asLong()).isEqualTo(100L);
    assertThat(json.path("asks").get(0).path("quantity").asLong()).isEqualTo(200L);
    assertThat(json.path("dateTime").asText()).isEqualTo("2026-11-01T10:00:00Z");
  }
}
