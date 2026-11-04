package ru.moex.papertrader.core.reporting;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import ru.moex.papertrader.core.domain.Side;

/**
 * Сделка журнала отчётности: цена до и после проскальзывания, комиссия и метка стратегии.
 *
 * <p>Проскальзывание — неблагоприятное отклонение фактической цены от опорной (цены стакана до
 * применения модели): для покупки {@code price - referencePrice}, для продажи {@code referencePrice
 * - price}. Положительное значение — издержка, отрицательное — исполнение лучше опорной цены.
 */
public record TradeJournalEntry(
    Instant executedAt,
    String orderId,
    UUID clientOrderId,
    String ticker,
    String classCode,
    Side side,
    long quantity,
    BigDecimal referencePrice,
    BigDecimal price,
    BigDecimal commission,
    String strategyId) {

  private static final int SLIPPAGE_SCALE = 6;
  private static final int MONEY_SCALE = 2;

  public TradeJournalEntry {
    Objects.requireNonNull(executedAt, "executedAt");
    requireText(orderId, "orderId");
    Objects.requireNonNull(clientOrderId, "clientOrderId");
    requireText(ticker, "ticker");
    requireText(classCode, "classCode");
    Objects.requireNonNull(side, "side");
    if (quantity <= 0) {
      throw new IllegalArgumentException("quantity должен быть больше 0");
    }
    requirePositive(referencePrice, "referencePrice");
    requirePositive(price, "price");
    if (commission == null || commission.signum() < 0) {
      throw new IllegalArgumentException("commission не может быть отрицательной");
    }
    strategyId = normalizeStrategy(strategyId);
  }

  /** Проскальзывание на единицу инструмента; положительное — в неблагоприятную сторону. */
  public BigDecimal slippagePerUnit() {
    BigDecimal value =
        side == Side.BUY ? price.subtract(referencePrice) : referencePrice.subtract(price);
    return value.setScale(SLIPPAGE_SCALE, RoundingMode.HALF_UP);
  }

  /** Суммарное проскальзывание сделки в деньгах. */
  public BigDecimal slippageTotal() {
    return slippagePerUnit()
        .multiply(BigDecimal.valueOf(quantity))
        .setScale(MONEY_SCALE, RoundingMode.HALF_UP);
  }

  /** Представление для JSON/CSV: все показатели журнала в плоском виде. */
  public JournalEntryView view() {
    return new JournalEntryView(
        executedAt,
        orderId,
        clientOrderId,
        ticker,
        classCode,
        side,
        quantity,
        referencePrice,
        price,
        slippagePerUnit(),
        slippageTotal(),
        commission.setScale(MONEY_SCALE, RoundingMode.HALF_UP),
        strategyId);
  }

  private static String normalizeStrategy(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    String trimmed = value.trim();
    if (trimmed.length() > 64) {
      throw new IllegalArgumentException("strategyId не может быть длиннее 64 символов");
    }
    return trimmed;
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
