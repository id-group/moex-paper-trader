package ru.moex.papertrader.infra.algoapi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/** Тесты record-настроек клиентов: значения по умолчанию и валидация. */
class AlgoApiPropertiesTest {

  @Test
  void algoPackDefaultsMatchSpecification() {
    AlgoPackProperties properties = AlgoPackProperties.defaults();

    assertThat(properties.baseUrl()).isEqualTo("https://algo-api.moex.com");
    assertThat(properties.token()).isEmpty();
    assertThat(properties.connectTimeoutMs()).isEqualTo(2_000);
    assertThat(properties.readTimeoutMs()).isEqualTo(5_000);
    assertThat(properties.maxRetries()).isEqualTo(2);
    assertThat(properties.retryDelayMs()).isEqualTo(500);
    assertThat(properties.engine()).isEqualTo("stock");
    assertThat(properties.market()).isEqualTo("shares");
    assertThat(properties.board()).isEqualTo("TQBR");
  }

  @Test
  void nullTokenBecomesEmptyString() {
    AlgoPackProperties properties =
        new AlgoPackProperties("http://localhost", null, 100, 100, 0, 0, "stock", "shares", "TQBR");

    assertThat(properties.token()).isEmpty();
  }

  @Test
  void algopackRejectsInvalidValues() {
    assertThatThrownBy(
            () ->
                new AlgoPackProperties(
                    "http://localhost", "", 0, 100, 0, 0, "stock", "shares", "TQBR"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("connectTimeoutMs");
    assertThatThrownBy(
            () ->
                new AlgoPackProperties(
                    "http://localhost", "", 100, 100, -1, 0, "stock", "shares", "TQBR"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("maxRetries");
    assertThatThrownBy(
            () -> new AlgoPackProperties("", "", 100, 100, 0, 0, "stock", "shares", "TQBR"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("baseUrl");
  }

  @Test
  void issDefaultsMatchSpecification() {
    IssProperties properties = IssProperties.defaults();

    // Домен iss.moex.ru выведен из эксплуатации (не разрешается в DNS), рабочий — iss.moex.com.
    assertThat(properties.baseUrl()).isEqualTo("https://iss.moex.com");
    assertThat(properties.connectTimeoutMs()).isEqualTo(2_000);
    assertThat(properties.readTimeoutMs()).isEqualTo(5_000);
    assertThat(properties.maxRetries()).isEqualTo(2);
    assertThat(properties.retryDelayMs()).isEqualTo(500);
    assertThat(properties.engine()).isEqualTo("stock");
    assertThat(properties.market()).isEqualTo("shares");
    assertThat(properties.board()).isEqualTo("TQBR");
  }

  @Test
  void issRejectsInvalidValues() {
    assertThatThrownBy(
            () -> new IssProperties("http://localhost", 100, 100, 0, -5, "stock", "shares", "TQBR"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("retryDelayMs");
    assertThatThrownBy(
            () -> new IssProperties("http://localhost", 100, 0, 0, 0, "stock", "shares", "TQBR"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("readTimeoutMs");
  }
}
