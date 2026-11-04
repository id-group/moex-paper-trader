package ru.moex.papertrader.core.execution;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;
import java.util.Random;
import ru.moex.papertrader.core.domain.Side;

/**
 * Проскальзывание по спецификации владельца (ADR-9): случайный базовый компонент 0.05–0.15% плюс
 * тир по размеру исполнения относительно ADV.
 *
 * <ul>
 *   <li>&lt; 1% ADV — +0.05%;
 *   <li>1–5% ADV — +0.1%;
 *   <li>&gt; 5% ADV — +0.2%.
 * </ul>
 *
 * <p>Базовый компонент всегда ухудшает цену. Если ADV неизвестен (0), применяется только базовый
 * компонент: тир считать не из чего. Генератор создаётся с фиксированным seed — в REPLAY одинаковый
 * порядок исполнений даёт одинаковые цены.
 */
public final class SpecSlippageModel implements SlippageModel {

  /** Нижняя граница базового случайного компонента: 0.05%. */
  public static final BigDecimal BASE_MIN = new BigDecimal("0.0005");

  /** Верхняя граница базового случайного компонента: 0.15%. */
  public static final BigDecimal BASE_MAX = new BigDecimal("0.0015");

  /** Тир &lt; 1% ADV: +0.05%. */
  public static final BigDecimal TIER_LOW = new BigDecimal("0.0005");

  /** Тир 1–5% ADV: +0.1%. */
  public static final BigDecimal TIER_MID = new BigDecimal("0.001");

  /** Тир &gt; 5% ADV: +0.2%. */
  public static final BigDecimal TIER_HIGH = new BigDecimal("0.002");

  private static final BigDecimal ONE_HUNDRED = BigDecimal.valueOf(100);
  private static final BigDecimal FIVE = BigDecimal.valueOf(5);
  private static final int SCALE = 8;

  private final Random random;
  private final BigDecimal baseMin;
  private final BigDecimal baseMax;

  /** Модель по умолчанию с базовым диапазоном 0.05–0.15%. */
  public SpecSlippageModel(long seed) {
    this(new Random(seed), BASE_MIN, BASE_MAX);
  }

  /** Модель с настраиваемым базовым диапазоном (доли, не проценты). */
  public SpecSlippageModel(long seed, BigDecimal baseMin, BigDecimal baseMax) {
    this(new Random(seed), baseMin, baseMax);
  }

  /** Тестовый конструктор: генератор передаётся снаружи для проверки границ. */
  SpecSlippageModel(Random random, BigDecimal baseMin, BigDecimal baseMax) {
    this.random = Objects.requireNonNull(random, "random");
    this.baseMin = Objects.requireNonNull(baseMin, "baseMin");
    this.baseMax = Objects.requireNonNull(baseMax, "baseMax");
    if (baseMin.signum() < 0 || baseMax.compareTo(baseMin) < 0) {
      throw new IllegalArgumentException("Требуется 0 <= baseMin <= baseMax");
    }
  }

  @Override
  public BigDecimal rate(Side side, BigDecimal referencePrice, long quantity, BigDecimal adv) {
    Objects.requireNonNull(side, "side");
    Objects.requireNonNull(referencePrice, "referencePrice");
    if (quantity <= 0) {
      throw new IllegalArgumentException("quantity должен быть больше 0");
    }
    BigDecimal base =
        baseMin.add(BigDecimal.valueOf(random.nextDouble()).multiply(baseMax.subtract(baseMin)));
    return base.add(tier(quantity, adv)).setScale(SCALE, RoundingMode.HALF_UP);
  }

  private static BigDecimal tier(long quantity, BigDecimal adv) {
    if (adv == null || adv.signum() <= 0) {
      return BigDecimal.ZERO;
    }
    BigDecimal volume = BigDecimal.valueOf(quantity);
    BigDecimal share = volume.multiply(ONE_HUNDRED);
    if (share.compareTo(adv) < 0) {
      return TIER_LOW;
    }
    if (share.compareTo(adv.multiply(FIVE)) <= 0) {
      return TIER_MID;
    }
    return TIER_HIGH;
  }
}
