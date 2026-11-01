package ru.moex.papertrader.bks.adapter;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;
import ru.moex.papertrader.bks.contract.DepoLimitResponse;
import ru.moex.papertrader.bks.contract.LimitQuantity;
import ru.moex.papertrader.bks.contract.LimitResponse;
import ru.moex.papertrader.bks.contract.MoneyLimitResponse;
import ru.moex.papertrader.bks.contract.Portfolio;
import ru.moex.papertrader.core.domain.PortfolioSnapshot;
import ru.moex.papertrader.core.domain.Position;

/**
 * Раскладывает брокер-независимый {@link PortfolioSnapshot} в контрактные записи БКС (ADR-5). На
 * этапе 2 используется только T0 в рублях.
 */
@Component
public class BksPortfolioMapper {

  private static final String EXCHANGE = "MOEX";
  private static final String TERM = "T0";
  private static final String RUSSIA = "RUSSIA";
  private static final String CURRENCY = "CURRENCY";
  private static final ZoneOffset UTC = ZoneOffset.UTC;

  public List<Portfolio> toPortfolio(PortfolioSnapshot snapshot) {
    List<Portfolio> entries = new ArrayList<>();
    entries.add(moneyEntry(snapshot));
    for (Position position : snapshot.positions()) {
      entries.add(depoEntry(snapshot, position));
    }
    return entries;
  }

  public LimitResponse toLimits(PortfolioSnapshot snapshot) {
    List<DepoLimitResponse> depoLimits = new ArrayList<>();
    for (Position position : snapshot.positions()) {
      BigDecimal lots =
          BigDecimal.valueOf(position.quantity())
              .divide(position.instrument().lotSize(), 0, RoundingMode.DOWN);
      depoLimits.add(
          new DepoLimitResponse(
              position.instrument().ticker(),
              position.instrument().classCode(),
              EXCHANGE,
              position.averagePrice(),
              new LimitQuantity(TERM, BigDecimal.valueOf(position.quantity())),
              new LimitQuantity(TERM, lots),
              position.instrument().type(),
              offset(snapshot),
              BigDecimal.ZERO,
              BigDecimal.ZERO,
              BigDecimal.ZERO,
              BigDecimal.ZERO));
    }
    MoneyLimitResponse moneyLimit =
        new MoneyLimitResponse(
            EXCHANGE,
            snapshot.currency(),
            snapshot.lockedCash(),
            BigDecimal.ONE,
            CURRENCY,
            new LimitQuantity(TERM, snapshot.cash()),
            offset(snapshot));
    return new LimitResponse(depoLimits, List.of(), List.of(moneyLimit), List.of());
  }

  private Portfolio moneyEntry(PortfolioSnapshot snapshot) {
    BigDecimal cash = snapshot.cash();
    return new Portfolio(
        "moneyLimit",
        null,
        null,
        snapshot.account(),
        EXCHANGE,
        snapshot.currency(),
        "Российский рубль",
        null,
        snapshot.currency(),
        RUSSIA,
        CURRENCY,
        TERM,
        cash,
        snapshot.lockedCash(),
        BigDecimal.ONE,
        BigDecimal.ONE,
        cash,
        cash,
        null,
        null,
        cash,
        cash,
        null,
        null,
        BigDecimal.ZERO,
        BigDecimal.ZERO,
        BigDecimal.ZERO,
        BigDecimal.ZERO,
        BigDecimal.valueOf(100),
        2,
        new BigDecimal("0.01"),
        CURRENCY,
        snapshot.currency(),
        null,
        BigDecimal.ZERO,
        null,
        false,
        false,
        BigDecimal.ZERO,
        BigDecimal.ONE,
        null);
  }

  private Portfolio depoEntry(PortfolioSnapshot snapshot, Position position) {
    BigDecimal quantity = BigDecimal.valueOf(position.quantity());
    BigDecimal balanceValue = position.averagePrice().multiply(quantity);
    BigDecimal currentValue = position.currentPrice().multiply(quantity);
    BigDecimal unrealized = currentValue.subtract(balanceValue);
    BigDecimal unrealizedPercent =
        position.averagePrice().signum() == 0
            ? BigDecimal.ZERO
            : position
                .currentPrice()
                .subtract(position.averagePrice())
                .divide(position.averagePrice(), 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100));
    return new Portfolio(
        "depoLimit",
        null,
        null,
        snapshot.account(),
        EXCHANGE,
        position.instrument().ticker(),
        position.instrument().displayName(),
        null,
        position.instrument().currency(),
        RUSSIA,
        position.instrument().type(),
        TERM,
        quantity,
        BigDecimal.ZERO,
        position.averagePrice(),
        position.currentPrice(),
        balanceValue,
        balanceValue,
        null,
        null,
        currentValue,
        currentValue,
        null,
        null,
        unrealized,
        unrealizedPercent,
        BigDecimal.ZERO,
        BigDecimal.ZERO,
        BigDecimal.ZERO,
        position.instrument().minimumStep().scale(),
        position.instrument().minimumStep(),
        position.instrument().classCode(),
        position.instrument().currency(),
        null,
        BigDecimal.ZERO,
        null,
        false,
        false,
        BigDecimal.ZERO,
        BigDecimal.ONE,
        null);
  }

  private static OffsetDateTime offset(PortfolioSnapshot snapshot) {
    return OffsetDateTime.ofInstant(snapshot.asOf(), UTC);
  }
}
