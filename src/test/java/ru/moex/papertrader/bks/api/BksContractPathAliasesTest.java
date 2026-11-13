package ru.moex.papertrader.bks.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Боевые пути контракта БКС Trade API — {@code /api/v1/**} и токен у корня хоста — открыты
 * параллельно изолированному префиксу {@code /api/bks/v1}: клиенты, написанные строго по {@code
 * docs/bcs-api}, работают без перемонтирования путей.
 */
class BksContractPathAliasesTest extends BksApiTestSupport {

  private static final String CONTRACT_API = "/api/v1";

  @Test
  void portfolioIsAvailableByContractPath() {
    ApiResponse response = get(CONTRACT_API + "/portfolio", tradeToken());

    assertThat(response.status()).isEqualTo(200);
    assertThat(response.json().isArray()).isTrue();
  }

  @Test
  void limitsAreAvailableByContractPath() {
    ApiResponse response = get(CONTRACT_API + "/limits", tradeToken());

    assertThat(response.status()).isEqualTo(200);
    assertThat(response.json().path("moneyLimits").isArray()).isTrue();
  }

  @Test
  void ordersSyncIsAvailableByContractPath() {
    ApiResponse response = post(CONTRACT_API + "/orders/sync", tradeToken(), limitOrder());

    assertThat(response.status()).isEqualTo(200);
    assertThat(response.json().path("clientOrderId").asText()).isNotBlank();
  }

  @Test
  void tokenIsAvailableAtContractRootPath() {
    String form =
        "client_id="
            + WRITE_CLIENT
            + "&refresh_token="
            + TRADE_REFRESH_TOKEN
            + "&grant_type=refresh_token";

    ApiResponse response = postForm(BksContractPaths.TOKEN_PATH, form);

    assertThat(response.status()).isEqualTo(200);
    assertThat(response.json().path("access_token").asText()).startsWith("bks-");
  }

  @Test
  void tradeOperationsByContractPathStillRequireTradeRights() {
    // Регрессия ADR-7: алиас /api/v1/orders не обходит проверку прав trade-api-write.
    ApiResponse response = post(CONTRACT_API + "/orders", readToken(), limitOrder());

    assertThat(response.status()).isEqualTo(403);
  }

  private static Map<String, Object> limitOrder() {
    Map<String, Object> request = new LinkedHashMap<>();
    request.put("clientOrderId", java.util.UUID.randomUUID().toString());
    request.put("side", "1");
    request.put("orderType", "2");
    request.put("timeInForce", "1");
    request.put("orderQuantity", 10);
    request.put("ticker", "SBER");
    request.put("classCode", "TQBR");
    request.put("price", 285.5);
    return request;
  }
}
