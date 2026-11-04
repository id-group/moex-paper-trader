package ru.moex.papertrader.core.reporting;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import ru.moex.papertrader.core.domain.Side;

/** Сделка журнала в форме, пригодной для JSON- и CSV-выгрузки. */
public record JournalEntryView(
    Instant executedAt,
    String orderId,
    UUID clientOrderId,
    String ticker,
    String classCode,
    Side side,
    long quantity,
    BigDecimal referencePrice,
    BigDecimal price,
    BigDecimal slippagePerUnit,
    BigDecimal slippageTotal,
    BigDecimal commission,
    String strategyId) {

  public JournalEntryView {
    Objects.requireNonNull(executedAt, "executedAt");
    Objects.requireNonNull(orderId, "orderId");
    Objects.requireNonNull(clientOrderId, "clientOrderId");
    Objects.requireNonNull(ticker, "ticker");
    Objects.requireNonNull(classCode, "classCode");
    Objects.requireNonNull(side, "side");
    Objects.requireNonNull(referencePrice, "referencePrice");
    Objects.requireNonNull(price, "price");
    Objects.requireNonNull(slippagePerUnit, "slippagePerUnit");
    Objects.requireNonNull(slippageTotal, "slippageTotal");
    Objects.requireNonNull(commission, "commission");
  }
}
