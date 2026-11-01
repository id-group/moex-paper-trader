package ru.moex.papertrader.bks.api;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class BksTokenApiTest extends BksApiTestSupport {

  @Test
  void exchangesRefreshTokenForAccessToken() {
    ApiResponse response =
        postForm(
            TOKEN_PATH,
            "client_id="
                + WRITE_CLIENT
                + "&refresh_token="
                + TRADE_REFRESH_TOKEN
                + "&grant_type=refresh_token");

    assertThat(response.status()).isEqualTo(200);
    assertThat(response.json().path("access_token").asText()).startsWith("bks-");
    assertThat(response.json().path("token_type").asText()).isEqualTo("bearer");
    assertThat(response.json().path("expires_in").asLong()).isEqualTo(86_400L);
    assertThat(response.json().path("refresh_expires_in").asLong()).isEqualTo(7_776_000L);
    assertThat(response.json().path("refresh_token").asText()).isEqualTo(TRADE_REFRESH_TOKEN);
    assertThat(response.json().path("not-before-policy").asText()).isEqualTo("0");
    assertThat(response.json().path("session_state").asText()).isNotBlank();
    assertThat(response.json().path("scope").asText()).isEqualTo(WRITE_CLIENT);
  }

  @Test
  void rejectsUnknownRefreshToken() {
    ApiResponse response =
        postForm(
            TOKEN_PATH,
            "client_id=" + WRITE_CLIENT + "&refresh_token=unknown&grant_type=refresh_token");

    assertThat(response.status()).isEqualTo(400);
    assertThat(response.json().path("error").asText()).isEqualTo("invalid_grant");
    assertThat(response.json().path("error_description").asText()).isNotBlank();
  }

  @Test
  void rejectsClientIdMismatch() {
    ApiResponse response =
        postForm(
            TOKEN_PATH,
            "client_id="
                + READ_CLIENT
                + "&refresh_token="
                + TRADE_REFRESH_TOKEN
                + "&grant_type=refresh_token");

    assertThat(response.status()).isEqualTo(400);
    assertThat(response.json().path("error").asText()).isEqualTo("invalid_grant");
  }

  @Test
  void rejectsWrongGrantType() {
    ApiResponse response =
        postForm(
            TOKEN_PATH,
            "client_id="
                + WRITE_CLIENT
                + "&refresh_token="
                + TRADE_REFRESH_TOKEN
                + "&grant_type=password");

    assertThat(response.status()).isEqualTo(400);
    assertThat(response.json().path("error").asText()).isEqualTo("invalid_grant");
  }

  @Test
  void rejectsMissingFormFields() {
    ApiResponse response = postForm(TOKEN_PATH, "");

    assertThat(response.status()).isEqualTo(400);
    assertThat(response.json().path("error").asText()).isEqualTo("invalid_grant");
  }

  @Test
  void readClientGetsReadOnlyAccessToken() {
    String token = readToken();

    assertThat(token).startsWith("bks-");
    ApiResponse response = get(BASE_PATH + "/v1/portfolio", token);
    assertThat(response.status()).isEqualTo(200);
  }
}
