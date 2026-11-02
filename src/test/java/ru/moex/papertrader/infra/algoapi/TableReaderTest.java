package ru.moex.papertrader.infra.algoapi;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Тесты разбора табличной схемы и времени ISS/AlgoPack. */
class TableReaderTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  @Test
  void readsValuesByColumnNameRegardlessOfOrder() {
    JsonNode root =
        MAPPER.readTree(
            """
            {"block": {"columns": ["B", "A"], "data": [["text", 42.5]]}}
            """);
    TableReader reader = TableReader.of(root, "block");

    assertThat(reader.size()).isEqualTo(1);
    assertThat(reader.text(0, "B")).isEqualTo("text");
    assertThat(reader.decimal(0, "A")).isEqualByComparingTo("42.5");
    assertThat(reader.longValue(0, "A", 0L)).isEqualTo(42L);
    assertThat(reader.text(0, "MISSING")).isNull();
    assertThat(reader.decimal(1, "A")).isNull();
  }

  @Test
  void missingBlockIsSchemaError() {
    JsonNode root = MAPPER.readTree("{}");

    assertThat(TableReader.optional(root, "block")).isNull();
    org.assertj.core.api.Assertions.assertThatThrownBy(() -> TableReader.of(root, "block"))
        .isInstanceOf(AlgoApiException.class)
        .hasMessageContaining("block");
  }

  @Test
  void parsesMoscowDateTimeIntoUtc() {
    assertThat(TableReader.toInstant("2026-11-02 19:34:55"))
        .isEqualTo(Instant.parse("2026-11-02T16:34:55Z"));
    assertThat(TableReader.toInstant("2026-11-02"))
        .isEqualTo(Instant.parse("2026-11-01T21:00:00Z"));
    assertThat(TableReader.toInstant("2026-11-02T16:34:55Z"))
        .isEqualTo(Instant.parse("2026-11-02T16:34:55Z"));
    assertThat(TableReader.toInstant("2026-11-02T19:34:55+03:00"))
        .isEqualTo(Instant.parse("2026-11-02T16:34:55Z"));
    assertThat(TableReader.toInstant("19:34:55")).isNull();
    assertThat(TableReader.toInstant(null)).isNull();
    assertThat(TableReader.combine("2026-11-02", "19:34:55"))
        .isEqualTo(Instant.parse("2026-11-02T16:34:55Z"));
    assertThat(TableReader.combine("2026-11-02", null)).isNull();
  }
}
