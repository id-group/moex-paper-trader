package ru.moex.papertrader.bks.adapter;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;
import ru.moex.papertrader.bks.contract.DepoLimitResponse;
import ru.moex.papertrader.bks.contract.FutureHoldingResponse;
import ru.moex.papertrader.bks.contract.FuturesLimitResponse;
import ru.moex.papertrader.bks.contract.LimitQuantity;
import ru.moex.papertrader.bks.contract.LimitResponse;
import ru.moex.papertrader.bks.contract.MoneyLimitResponse;
import ru.moex.papertrader.bks.contract.Portfolio;
import ru.moex.papertrader.core.commission.InstrumentType;
import ru.moex.papertrader.core.domain.PortfolioSnapshot;
import ru.moex.papertrader.core.domain.Position;

/**
 * Раскладывает брокер-независимый {@link PortfolioSnapshot} в контрактные записи БКС (ADR-5). На
 * этапе 2 используется только T0 в рублях; фьючерсные позиции выносятся в {@code futuresHolding} и
 * {@code futuresLimits}.
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
      entries.add(
          isFutures(position)
              ? futuresHoldingEntry(snapshot, position)
              : depoEntry(snapshot, position));
    }
    return entries;
  }

  public LimitResponse toLimits(PortfolioSnapshot snapshot) {
    List<DepoLimitResponse> depoLimits = new ArrayList<>();
    List<FutureHoldingResponse> futureHolding = new ArrayList<>();
    for (Position position : snapshot.positions()) {
      if (isFutures(position)) {
        futureHolding.add(futureHolding(position));
      } else {
        depoLimits.add(depoLimit(snapshot, position));
      }
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
    return new LimitResponse(
        depoLimits, futureHolding, List.of(moneyLimit), futuresLimits(snapshot, futureHolding));
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
        snapshot.lockedForFutures(),
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

  /**
   * Запись {@code futuresHolding} портфеля: только поля, выводимые из снапшота. Рублёвая стоимость
   * позиции и вариационная маржа без стоимости шага не считаются и остаются {@code null}.
   */
  private Portfolio futuresHoldingEntry(PortfolioSnapshot snapshot, Position position) {
    return new Portfolio(
        "futuresHolding",
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
        BigDecimal.valueOf(position.quantity()),
        BigDecimal.ZERO,
        position.averagePrice(),
        position.currentPrice(),
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
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

  private static boolean isFutures(Position position) {
    return InstrumentType.FUTURES.name().equalsIgnoreCase(position.instrument().type());
  }

  private DepoLimitResponse depoLimit(PortfolioSnapshot snapshot, Position position) {
    BigDecimal lots =
        BigDecimal.valueOf(position.quantity())
            .divide(position.instrument().lotSize(), 0, RoundingMode.DOWN);
    return new DepoLimitResponse(
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
        BigDecimal.ZERO);
  }

  /**
   * Держание фьючерса по схеме {@code FutureHoldingResponse}. Снапшот не хранит вариационную маржу,
   * стоимость шага и дату сделки, поэтому соответствующие поля остаются {@code null} и не попадают
   * в JSON.
   */
  private FutureHoldingResponse futureHolding(Position position) {
    return new FutureHoldingResponse(
        position.instrument().ticker(),
        position.instrument().classCode(),
        EXCHANGE,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        position.averagePrice(),
        position.instrument().type(),
        null);
  }

  /**
   * Фьючерсный лимит по схеме {@code FuturesLimitResponse}. ГО разделено по описаниям полей: {@code
   * cbplUsedForOrders} — ГО, зарезервированное под клиентские заявки, {@code cbplUsedForPositions}
   * — ГО под открытые позиции. Описание {@code cbplUsed} («позиции после последнего клиринга»)
   * неоднозначно, поэтому отдаётся суммарное ГО ({@code lockedForFutures}). Размер лимита ГО и
   * планируемые сделки не моделируются и остаются {@code null}.
   */
  private List<FuturesLimitResponse> futuresLimits(
      PortfolioSnapshot snapshot, List<FutureHoldingResponse> futureHolding) {
    BigDecimal margin = snapshot.lockedForFutures();
    if (futureHolding.isEmpty() && margin.signum() == 0) {
      return List.of();
    }
    return List.of(
        new FuturesLimitResponse(
            snapshot.currency(),
            EXCHANGE,
            null,
            null,
            margin,
            null,
            snapshot.lockedForFuturesOrders(),
            snapshot.lockedForFuturesPositions(),
            null,
            InstrumentType.FUTURES.name(),
            offset(snapshot),
            null,
            null));
  }
}
