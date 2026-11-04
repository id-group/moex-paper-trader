package ru.moex.papertrader.core.reporting;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import ru.moex.papertrader.core.domain.Side;

/**
 * Закрытая FIFO-пара «вход → выход» с реализованным результатом.
 *
 * <p>{@code pnl} — результат после вычета доли комиссий входа и выхода; для короткой позиции
 * ({@code entrySide = SELL}) результат равен {@code (вход − выход) × количество} минус комиссии.
 * Метка стратегии берётся у сделки входа: результат относится к стратегии, открывшей позицию.
 */
public record ClosedTrade(
    String ticker,
    String classCode,
    String strategyId,
    Side entrySide,
    long quantity,
    BigDecimal entryPrice,
    BigDecimal exitPrice,
    BigDecimal pnl,
    Instant entryAt,
    Instant exitAt,
    String entryOrderId,
    String exitOrderId) {

  public ClosedTrade {
    requireText(ticker, "ticker");
    requireText(classCode, "classCode");
    Objects.requireNonNull(entrySide, "entrySide");
    if (quantity <= 0) {
      throw new IllegalArgumentException("quantity должен быть больше 0");
    }
    requirePositive(entryPrice, "entryPrice");
    requirePositive(exitPrice, "exitPrice");
    Objects.requireNonNull(pnl, "pnl");
    Objects.requireNonNull(entryAt, "entryAt");
    Objects.requireNonNull(exitAt, "exitAt");
    requireText(entryOrderId, "entryOrderId");
    requireText(exitOrderId, "exitOrderId");
  }

  /** Прибыльная сделка: строго положительный результат после комиссий. */
  public boolean profitable() {
    return pnl.signum() > 0;
  }

  private static void requireText(String value, String name) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(name + " не может быть пустым");
    }
  }

  private static void requirePositive(BigDecimal value, String name) {
    if (value == null || value.signum() <= 0) {
      throw new IllegalArgumentException(name + " должен быть положительным");
    }
  }
}
