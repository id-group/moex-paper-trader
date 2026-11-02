package ru.moex.papertrader.core.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class FillTest {

  private static final Instant NOW = Instant.parse("2026-11-02T10:00:00Z");

  @Test
  void storesExecutionDetails() {
    Fill fill = new Fill(new BigDecimal("285.50"), 10, new BigDecimal("2.86"), NOW);

    assertThat(fill.price()).isEqualByComparingTo("285.50");
    assertThat(fill.quantity()).isEqualTo(10);
    assertThat(fill.commission()).isEqualByComparingTo("2.86");
    assertThat(fill.executedAt()).isEqualTo(NOW);
  }

  @Test
  void rejectsNonPositivePrice() {
    assertThatThrownBy(() -> new Fill(BigDecimal.ZERO, 1, BigDecimal.ZERO, NOW))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("price");
  }

  @Test
  void rejectsNonPositiveQuantity() {
    assertThatThrownBy(() -> new Fill(BigDecimal.ONE, 0, BigDecimal.ZERO, NOW))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("quantity");
  }

  @Test
  void rejectsNegativeCommission() {
    assertThatThrownBy(() -> new Fill(BigDecimal.ONE, 1, new BigDecimal("-0.01"), NOW))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("commission");
  }

  @Test
  void rejectsNullExecutionMoment() {
    assertThatThrownBy(() -> new Fill(BigDecimal.ONE, 1, BigDecimal.ZERO, null))
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("executedAt");
  }
}
