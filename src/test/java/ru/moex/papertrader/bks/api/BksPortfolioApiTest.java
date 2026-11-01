package ru.moex.papertrader.bks.api;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class BksPortfolioApiTest extends BksApiTestSupport {

  @Test
  void portfolioReturnsMoneyLimitEntry() {
    ApiResponse response = get(BASE_PATH + "/v1/portfolio", tradeToken());

    assertThat(response.status()).isEqualTo(200);
    assertThat(response.json().isArray()).isTrue();
    assertThat(response.json().size()).isGreaterThanOrEqualTo(1);
    var money = response.json().get(0);
    assertThat(money.path("type").asText()).isEqualTo("moneyLimit");
    assertThat(money.path("account").asText()).isEqualTo("PAPER-0001");
    assertThat(money.path("currency").asText()).isEqualTo("RUB");
    assertThat(money.path("term").asText()).isEqualTo("T0");
    assertThat(money.path("upperType").asText()).isEqualTo("RUSSIA");
    assertThat(money.path("quantity").asDouble()).isEqualTo(1_000_000.0);
    assertThat(money.path("currentValueRub").asDouble()).isEqualTo(1_000_000.0);
    assertThat(money.path("portfolioShare").asDouble()).isEqualTo(100.0);
  }

  @Test
  void limitsReturnMoneyLimitWithTerm() {
    ApiResponse response = get(BASE_PATH + "/v1/limits", tradeToken());

    assertThat(response.status()).isEqualTo(200);
    var moneyLimits = response.json().path("moneyLimits");
    assertThat(moneyLimits.isArray()).isTrue();
    assertThat(moneyLimits.size()).isEqualTo(1);
    var moneyLimit = moneyLimits.get(0);
    assertThat(moneyLimit.path("currencyCode").asText()).isEqualTo("RUB");
    assertThat(moneyLimit.path("instrumentType").asText()).isEqualTo("CURRENCY");
    assertThat(moneyLimit.path("quantity").path("type").asText()).isEqualTo("T0");
    assertThat(moneyLimit.path("quantity").path("value").asDouble()).isEqualTo(1_000_000.0);
    assertThat(moneyLimit.path("loadDate").asText()).isNotBlank();
    assertThat(response.json().path("depoLimit").isArray()).isTrue();
    assertThat(response.json().path("futureHolding").isArray()).isTrue();
    assertThat(response.json().path("futuresLimits").isArray()).isTrue();
  }

  @Test
  void portfolioWithoutTokenReturnsUnauthorized() {
    ApiResponse response = get(BASE_PATH + "/v1/portfolio", null);

    assertThat(response.status()).isEqualTo(401);
    assertThat(response.json().path("type").asText()).isEqualTo("UNAUTHORIZED");
  }

  @Test
  void limitsWithoutTokenReturnsUnauthorized() {
    ApiResponse response = get(BASE_PATH + "/v1/limits", "unknown-token");

    assertThat(response.status()).isEqualTo(401);
  }
}
