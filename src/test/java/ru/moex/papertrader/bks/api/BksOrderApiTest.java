package ru.moex.papertrader.bks.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

class BksOrderApiTest extends BksApiTestSupport {

  private static final String ORDERS = BASE_PATH + "/v1/orders";

  @Test
  void createSyncReturnsFullOrderState() {
    String clientOrderId = newUuid();

    ApiResponse response = post(ORDERS + "/sync", tradeToken(), limitOrder(clientOrderId));

    assertThat(response.status()).isEqualTo(200);
    JsonNode body = response.json();
    assertThat(body.path("clientOrderId").asText()).isEqualTo(clientOrderId);
    JsonNode order = body.path("data").get(0);
    assertThat(order.path("orderStatus").asText()).isEqualTo("0");
    assertThat(order.path("executionType").asText()).isEqualTo("0");
    assertThat(order.path("ticker").asText()).isEqualTo("SBER");
    assertThat(order.path("classCode").asText()).isEqualTo("TQBR");
    assertThat(order.path("side").asText()).isEqualTo("1");
    assertThat(order.path("orderType").asText()).isEqualTo("2");
    assertThat(order.path("orderQuantity").asDouble()).isEqualTo(10.0);
    assertThat(order.path("remainedQuantity").asDouble()).isEqualTo(10.0);
    assertThat(order.path("price").asDouble()).isEqualTo(285.5);
    assertThat(order.path("currency").asText()).isEqualTo("RUB");
    assertThat(order.path("timeInForce").asInt()).isEqualTo(1);
    assertThat(order.path("securityExchange").asText()).isEqualTo("MOEX");
    assertThat(order.path("transactionTime").asText()).isNotBlank();
    assertThat(order.path("transactionTime").asText())
        .matches("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(\\.\\d+)?Z");
    assertThat(order.path("tradeDate").asText()).matches("\\d{4}-\\d{2}-\\d{2}");
    assertThat(order.path("orderId").asText()).matches("\\d{8}-TQBR-\\d+");
    assertThat(order.path("orderNumber").asText()).isNotBlank();
  }

  @Test
  void createAsyncReturnsShortResponse() {
    String clientOrderId = newUuid();

    ApiResponse response = post(ORDERS, tradeToken(), limitOrder(clientOrderId));

    assertThat(response.status()).isEqualTo(200);
    assertThat(response.json().path("clientOrderId").asText()).isEqualTo(clientOrderId);
    assertThat(response.json().path("status").asText()).isEqualTo("OK");
  }

  @Test
  void marketOrderWithoutPriceIsAccepted() {
    Map<String, Object> request = new LinkedHashMap<>();
    request.put("clientOrderId", newUuid());
    request.put("side", "2");
    request.put("orderType", "1");
    request.put("orderQuantity", 5);
    request.put("ticker", "GAZP");
    request.put("classCode", "TQBR");

    ApiResponse response = post(ORDERS + "/sync", tradeToken(), request);

    assertThat(response.status()).isEqualTo(200);
    assertThat(response.json().path("data").get(0).path("price").isMissingNode()).isTrue();
  }

  @Test
  void statusIsAvailableByClientAndExchangeIdentifiers() {
    String clientOrderId = newUuid();
    JsonNode created = post(ORDERS + "/sync", tradeToken(), limitOrder(clientOrderId)).json();
    String exchangeOrderId = created.path("data").get(0).path("orderId").asText();

    ApiResponse byClient = get(ORDERS + "?orderIdType=1&orderId=" + clientOrderId, tradeToken());
    ApiResponse byExchange =
        get(ORDERS + "?orderIdType=2&orderId=" + exchangeOrderId, tradeToken());

    assertThat(byClient.status()).isEqualTo(200);
    assertThat(byClient.json().path("clientOrderId").asText()).isEqualTo(clientOrderId);
    assertThat(byExchange.status()).isEqualTo(200);
    assertThat(byExchange.json().path("data").get(0).path("orderId").asText())
        .isEqualTo(exchangeOrderId);
  }

  @Test
  void cancelSyncReturnsCancelledStateAndReleasesOrder() {
    String clientOrderId = newUuid();
    post(ORDERS + "/sync", tradeToken(), limitOrder(clientOrderId));
    String cancelRequestId = newUuid();

    ApiResponse response =
        post(ORDERS + "/cancel/sync", tradeToken(), cancelRequest(clientOrderId, cancelRequestId));

    assertThat(response.status()).isEqualTo(200);
    JsonNode order = response.json().path("data").get(0);
    assertThat(order.path("orderStatus").asText()).isEqualTo("4");
    ApiResponse status = get(ORDERS + "?orderId=" + clientOrderId, tradeToken());
    assertThat(status.json().path("data").get(0).path("orderStatus").asText()).isEqualTo("4");
  }

  @Test
  void cancelAsyncReturnsRequestIdentifierAndOkStatus() {
    String clientOrderId = newUuid();
    post(ORDERS, tradeToken(), limitOrder(clientOrderId));
    String cancelRequestId = newUuid();

    ApiResponse response =
        post(ORDERS + "/cancel", tradeToken(), cancelRequest(clientOrderId, cancelRequestId));

    assertThat(response.status()).isEqualTo(200);
    assertThat(response.json().path("clientOrderId").asText()).isEqualTo(cancelRequestId);
    assertThat(response.json().path("status").asText()).isEqualTo("OK");
  }

  @Test
  void searchFindsOrderByNumberWithContractFields() {
    JsonNode created = post(ORDERS + "/sync", tradeToken(), limitOrder(newUuid())).json();
    long orderNum = Long.parseLong(created.path("data").get(0).path("orderNumber").asText());

    ApiResponse response =
        post(
            ORDERS + "/search",
            tradeToken(),
            Map.of("orderNums", List.of(orderNum), "orderStatus", List.of(3)));

    assertThat(response.status()).isEqualTo(200);
    assertThat(response.json().path("totalRecords").asLong()).isEqualTo(1L);
    JsonNode record = response.json().path("records").get(0);
    assertThat(record.path("orderNum").asLong()).isEqualTo(orderNum);
    assertThat(record.path("orderStatus").asInt()).isEqualTo(3);
    assertThat(record.path("orderType").asInt()).isEqualTo(2);
    assertThat(record.path("side").asInt()).isEqualTo(1);
    assertThat(record.path("ticker").asText()).isEqualTo("SBER");
    assertThat(record.path("settlementCurrency").asText()).isEqualTo("RUB");
    assertThat(record.path("orderQuantityLots").asDouble()).isEqualTo(1.0);
    assertThat(record.path("orderDateTime").asText()).isNotBlank();
    assertThat(record.path("tradeDate").asText()).matches("\\d{4}-\\d{2}-\\d{2}");
  }

  @Test
  void searchWithUnsupportedOrderTypeReturnsEmptyPage() {
    ApiResponse response =
        post(ORDERS + "/search", tradeToken(), Map.of("orderTypes", List.of(10)));

    assertThat(response.status()).isEqualTo(200);
    assertThat(response.json().path("records").size()).isZero();
    assertThat(response.json().path("totalRecords").asLong()).isZero();
  }

  @Test
  void searchWithInvalidPageReturnsBadRequest() {
    ApiResponse response = post(ORDERS + "/search?page=-1", tradeToken(), Map.of());

    assertThat(response.status()).isEqualTo(400);
    assertThat(response.json().path("type").asText()).isEqualTo("VALIDATION_ERROR");
    assertThat(response.json().path("traceId").asText()).isNotBlank();
  }

  @Test
  void createWithoutTokenReturnsUnauthorized() {
    ApiResponse response = post(ORDERS, null, limitOrder(newUuid()));

    assertThat(response.status()).isEqualTo(401);
    assertThat(response.json().path("type").asText()).isEqualTo("UNAUTHORIZED");
  }

  @Test
  void readOnlyTokenCannotCreateOrder() {
    ApiResponse response = post(ORDERS, readToken(), limitOrder(newUuid()));

    assertThat(response.status()).isEqualTo(403);
    assertThat(response.json().path("type").asText()).isEqualTo("FORBIDDEN");
  }

  @Test
  void invalidSideReturnsBadRequest() {
    Map<String, Object> request = limitOrder(newUuid());
    request.put("side", "9");

    ApiResponse response = post(ORDERS, tradeToken(), request);

    assertThat(response.status()).isEqualTo(400);
    assertThat(response.json().path("errors").get(0).path("field").isMissingNode()).isTrue();
  }

  @Test
  void limitOrderWithoutPriceReturnsUnprocessableEntity() {
    Map<String, Object> request = limitOrder(newUuid());
    request.remove("price");

    ApiResponse response = post(ORDERS, tradeToken(), request);

    assertThat(response.status()).isEqualTo(422);
    assertThat(response.json().path("type").asText()).isEqualTo("VALIDATION_ERROR");
  }

  @Test
  void duplicateClientOrderIdReturnsUnprocessableEntity() {
    String clientOrderId = newUuid();
    post(ORDERS, tradeToken(), limitOrder(clientOrderId));

    ApiResponse response = post(ORDERS, tradeToken(), limitOrder(clientOrderId));

    assertThat(response.status()).isEqualTo(422);
  }

  @Test
  void cancelledOrderCannotBeCancelledAgain() {
    String clientOrderId = newUuid();
    post(ORDERS, tradeToken(), limitOrder(clientOrderId));
    post(ORDERS + "/cancel", tradeToken(), cancelRequest(clientOrderId, newUuid()));

    ApiResponse response =
        post(ORDERS + "/cancel", tradeToken(), cancelRequest(clientOrderId, newUuid()));

    assertThat(response.status()).isEqualTo(422);
  }

  @Test
  void unknownOrderReturnsNotFound() {
    ApiResponse response = get(ORDERS + "?orderId=" + newUuid(), tradeToken());

    assertThat(response.status()).isEqualTo(404);
    assertThat(response.json().path("type").asText()).isEqualTo("NOT_FOUND");
  }

  @Test
  void unknownEndpointReturnsNotFound() {
    ApiResponse response = get(BASE_PATH + "/v1/does-not-exist", tradeToken());

    assertThat(response.status()).isEqualTo(404);
    assertThat(response.json().path("type").asText()).isEqualTo("NOT_FOUND");
  }

  @Test
  void missingOrderIdReturnsBadRequest() {
    ApiResponse response = get(ORDERS, tradeToken());

    assertThat(response.status()).isEqualTo(400);
  }

  @Test
  void malformedJsonReturnsBadRequest() {
    ApiResponse response = postJson(ORDERS, tradeToken(), "{\"clientOrderId\": ");

    assertThat(response.status()).isEqualTo(400);
    assertThat(response.json().path("type").asText()).isEqualTo("VALIDATION_ERROR");
  }

  @Test
  void cancelRequestEchoesRequestIdentifier() {
    String clientOrderId = newUuid();
    post(ORDERS, tradeToken(), limitOrder(clientOrderId));
    String cancelRequestId = newUuid();

    ApiResponse response =
        post(ORDERS + "/cancel/sync", tradeToken(), cancelRequest(clientOrderId, cancelRequestId));

    assertThat(response.status()).isEqualTo(200);
    assertThat(response.json().path("clientOrderId").asText()).isEqualTo(clientOrderId);
  }

  private Map<String, Object> limitOrder(String clientOrderId) {
    Map<String, Object> request = new LinkedHashMap<>();
    request.put("clientOrderId", clientOrderId);
    request.put("side", "1");
    request.put("orderType", "2");
    request.put("timeInForce", "1");
    request.put("orderQuantity", 10);
    request.put("ticker", "SBER");
    request.put("classCode", "TQBR");
    request.put("price", 285.5);
    return request;
  }

  private Map<String, Object> cancelRequest(String clientOrderId, String requestId) {
    return Map.of("orderIdType", "1", "orderId", clientOrderId, "clientOrderId", requestId);
  }

  private static String newUuid() {
    return UUID.randomUUID().toString();
  }
}
