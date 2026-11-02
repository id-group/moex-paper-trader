package ru.moex.papertrader.core.portfolio;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import ru.moex.papertrader.core.domain.Fill;
import ru.moex.papertrader.core.domain.Instrument;
import ru.moex.papertrader.core.domain.InvalidOrderException;

/**
 * Виртуальный портфель: денежные средства по валютам, блокировки средств и позиции по инструментам.
 *
 * <p>Допущение T0: поставка и денежные расчёты происходят в день сделки; поставка T+1 в домене пока
 * не моделируется (бриф этапа 3). Акции покупаются и продаются по цене, переданной вызывающим
 * кодом, — проскальзывание появится в модели исполнения на этапе 5. Комиссия также передаётся
 * вызывающим кодом и никогда не рассчитывается портфелем.
 *
 * <p>Мультивалютность пока только структура: каждая валюта учитывается отдельно, базовая валюта —
 * {@link #BASE_CURRENCY}. Конвертация курсов не реализована — TODO(этап 4+).
 *
 * <p>Фьючерсы: открытие и закрытие позиции не списывают полную стоимость контракта. Денежные
 * эффекты фьючерсов (ГО и вариационная маржа) учитываются отдельно в задаче S3-1b.
 */
public final class VirtualPortfolio {

  /** Базовая валюта портфеля; остальные валюты — только структура до появления курсов. */
  public static final String BASE_CURRENCY = "RUB";

  private static final String FUTURES_TYPE = "FUTURES";

  /** Единственное место с масштабом и округлением денежных сумм. */
  private static final int MONEY_SCALE = 2;

  /** Средняя цена может быть дробной после взвешивания, поэтому масштаб выше денежного. */
  private static final int AVERAGE_PRICE_SCALE = 6;

  private static final RoundingMode ROUNDING = RoundingMode.HALF_UP;
  private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(MONEY_SCALE, ROUNDING);

  private final Map<String, BigDecimal> cash = new LinkedHashMap<>();
  private final Map<String, BigDecimal> locked = new LinkedHashMap<>();
  private final Map<String, PortfolioPosition> positions = new LinkedHashMap<>();

  /** Портфель с начальным остатком в базовой валюте. */
  public VirtualPortfolio(BigDecimal initialRub) {
    this(Map.of(BASE_CURRENCY, Objects.requireNonNull(initialRub, "initialRub")));
  }

  /** Портфель с начальными остатками по валютам; суммы нормализуются к денежному масштабу. */
  public VirtualPortfolio(Map<String, BigDecimal> initialCash) {
    Objects.requireNonNull(initialCash, "initialCash");
    for (Map.Entry<String, BigDecimal> entry : initialCash.entrySet()) {
      String currency = requireCurrency(entry.getKey());
      BigDecimal amount = entry.getValue();
      if (amount == null || amount.signum() < 0) {
        throw new IllegalArgumentException(
            "Начальный остаток " + currency + " не может быть отрицательным");
      }
      cash.put(currency, money(amount));
    }
  }

  /**
   * Пустой портфель для фабрики {@link #restoreForSettlement}; публичные конструкторы его не дают.
   */
  private VirtualPortfolio() {}

  /**
   * Покупает инструмент по переданной цене, списывая стоимость (для акций) и комиссию.
   *
   * <p>Для фьючерсов стоимость контракта не списывается, а комиссия — биржевой сбор — проводится
   * без проверки свободных средств: обеспечение считает сервис исполнения по дельте ГО. Иначе
   * закрытие убыточной позиции после отрицательной вариационной маржи было бы невозможно.
   *
   * @throws InvalidOrderException если не хватает свободных средств (акции) или параметры сделки
   *     некорректны
   */
  public Fill buy(
      Instrument instrument,
      long quantity,
      BigDecimal price,
      BigDecimal commission,
      Instant executedAt) {
    validateTrade(instrument, quantity, price, commission, executedAt);
    BigDecimal normalizedCommission = money(commission);
    String currency = instrument.currency();
    if (isFutures(instrument)) {
      chargeFuturesCommission(currency, normalizedCommission);
    } else {
      debit(
          currency, money(price.multiply(BigDecimal.valueOf(quantity))).add(normalizedCommission));
    }
    applyPosition(instrument, quantity, price);
    return new Fill(price, quantity, normalizedCommission, executedAt);
  }

  /**
   * Продаёт инструмент по переданной цене. Для акций позиция не может стать короткой, для фьючерсов
   * продажа сверх остатка открывает короткую позицию.
   *
   * <p>Комиссия фьючерсной сделки проводится без проверки свободных средств — см. {@link #buy}.
   *
   * @throws InvalidOrderException если продаётся больше акций, чем есть в позиции, или параметры
   *     сделки некорректны
   */
  public Fill sell(
      Instrument instrument,
      long quantity,
      BigDecimal price,
      BigDecimal commission,
      Instant executedAt) {
    validateTrade(instrument, quantity, price, commission, executedAt);
    BigDecimal normalizedCommission = money(commission);
    String currency = instrument.currency();
    if (isFutures(instrument)) {
      chargeFuturesCommission(currency, normalizedCommission);
    } else {
      long held = quantityOf(instrument);
      if (quantity > held) {
        throw new InvalidOrderException(
            "Нельзя продать "
                + quantity
                + " "
                + instrument.ticker()
                + ": в позиции только "
                + held);
      }
      BigDecimal proceeds =
          money(price.multiply(BigDecimal.valueOf(quantity))).subtract(normalizedCommission);
      cash.merge(currency, proceeds, BigDecimal::add);
    }
    applyPosition(instrument, -quantity, price);
    return new Fill(price, quantity, normalizedCommission, executedAt);
  }

  /** Резервирует средства в базовой валюте. */
  public void lock(BigDecimal amount) {
    lock(BASE_CURRENCY, amount);
  }

  /** Резервирует средства в указанной валюте; больше свободного остатка зарезервировать нельзя. */
  public void lock(String currency, BigDecimal amount) {
    String normalizedCurrency = requireCurrency(currency);
    BigDecimal normalizedAmount = positiveMoney(amount, "amount");
    BigDecimal available = availableCash(normalizedCurrency);
    if (available.compareTo(normalizedAmount) < 0) {
      throw new InvalidOrderException(
          "Недостаточно свободных средств "
              + normalizedCurrency
              + ": требуется "
              + normalizedAmount
              + ", доступно "
              + available);
    }
    locked.merge(normalizedCurrency, normalizedAmount, BigDecimal::add);
  }

  /** Освобождает ранее зарезервированные средства в базовой валюте. */
  public void release(BigDecimal amount) {
    release(BASE_CURRENCY, amount);
  }

  /** Освобождает резерв в указанной валюте; больше заблокированного освободить нельзя. */
  public void release(String currency, BigDecimal amount) {
    String normalizedCurrency = requireCurrency(currency);
    BigDecimal normalizedAmount = positiveMoney(amount, "amount");
    BigDecimal lockedAmount = lockedCash(normalizedCurrency);
    if (lockedAmount.compareTo(normalizedAmount) < 0) {
      throw new InvalidOrderException(
          "Нельзя освободить "
              + normalizedAmount
              + " "
              + normalizedCurrency
              + ": заблокировано только "
              + lockedAmount);
    }
    locked.merge(normalizedCurrency, normalizedAmount.negate(), BigDecimal::add);
  }

  /**
   * Начисляет вариационную маржу в кэш без проверки свободных средств.
   *
   * <p>ВМ — обязательный клиринговый платёж: убыток фиксируется сразу, поэтому кэш может уйти в
   * минус. Калькулятор суммы — {@link VariationMarginCalculator}, портфель только записывает
   * результат.
   */
  public void applyVariationMargin(BigDecimal amount) {
    applyVariationMargin(BASE_CURRENCY, amount);
  }

  /** Начисляет вариационную маржу в кэш указанной валюты (может быть отрицательной). */
  public void applyVariationMargin(String currency, BigDecimal amount) {
    String normalizedCurrency = requireCurrency(currency);
    if (amount == null) {
      throw new IllegalArgumentException("amount не может быть null");
    }
    cash.merge(normalizedCurrency, money(amount), BigDecimal::add);
  }

  /**
   * Переводит блокировку ГО на дельту без проверки свободных средств: рост резервирует, снижение
   * освобождает.
   *
   * <p>Клиринг обязан пересчитать ГО от новой расчётной цены даже после убыточной ВМ, когда
   * свободных средств уже нет. Проверка обеспечения остаётся только у сделок.
   */
  public void applyMarginChange(BigDecimal delta) {
    applyMarginChange(BASE_CURRENCY, delta);
  }

  /** Переводит блокировку ГО на дельту в указанной валюте без проверки свободного остатка. */
  public void applyMarginChange(String currency, BigDecimal delta) {
    String normalizedCurrency = requireCurrency(currency);
    if (delta == null) {
      throw new IllegalArgumentException("delta не может быть null");
    }
    BigDecimal normalizedDelta = money(delta);
    if (normalizedDelta.signum() > 0) {
      locked.merge(normalizedCurrency, normalizedDelta, BigDecimal::add);
    } else if (normalizedDelta.signum() < 0) {
      release(normalizedCurrency, normalizedDelta.negate());
    }
  }

  /**
   * Фиксирует расчётную цену дня в средней цене открытой позиции (клиринговое соглашение).
   * Количество и его знак не меняются.
   *
   * @throws IllegalArgumentException если позиция не открыта или цена не положительна
   */
  public void settlePosition(Instrument instrument, BigDecimal settlementPrice) {
    Objects.requireNonNull(instrument, "instrument");
    if (settlementPrice == null || settlementPrice.signum() <= 0) {
      throw new IllegalArgumentException("settlementPrice должен быть положительным");
    }
    String key = key(instrument);
    PortfolioPosition current = positions.get(key);
    if (current == null) {
      throw new IllegalArgumentException("Позиция " + key + " не открыта");
    }
    positions.put(
        key, new PortfolioPosition(current.instrument(), current.quantity(), settlementPrice));
  }

  /** Позиция по инструменту, если она открыта. */
  public Optional<PortfolioPosition> position(Instrument instrument) {
    Objects.requireNonNull(instrument, "instrument");
    return Optional.ofNullable(positions.get(key(instrument)));
  }

  /** Открытые позиции в порядке появления. */
  public List<PortfolioPosition> positions() {
    return List.copyOf(positions.values());
  }

  /** Денежные средства в базовой валюте. */
  public BigDecimal cash() {
    return cash(BASE_CURRENCY);
  }

  /** Денежные средства в указанной валюте; отсутствующая валюта трактуется как ноль. */
  public BigDecimal cash(String currency) {
    return cash.getOrDefault(requireCurrency(currency), ZERO);
  }

  /** Заблокированные средства в базовой валюте. */
  public BigDecimal lockedCash() {
    return lockedCash(BASE_CURRENCY);
  }

  /** Заблокированные средства в указанной валюте. */
  public BigDecimal lockedCash(String currency) {
    return locked.getOrDefault(requireCurrency(currency), ZERO);
  }

  /** Свободные средства (средства минус блокировки) в базовой валюте. */
  public BigDecimal availableCash() {
    return availableCash(BASE_CURRENCY);
  }

  /** Свободные средства (средства минус блокировки) в указанной валюте. */
  public BigDecimal availableCash(String currency) {
    return cash(currency).subtract(lockedCash(currency));
  }

  public Map<String, BigDecimal> cashByCurrency() {
    return Map.copyOf(cash);
  }

  public Map<String, BigDecimal> lockedCashByCurrency() {
    return Map.copyOf(locked);
  }

  /**
   * Восстанавливает портфель из сохранённого состояния (рестарт сервиса).
   *
   * <p>Фабрика добавлена для слоя персистентности: публичные конструкторы принимают только
   * начальные остатки и не позволяют задать блокировки и открытые позиции. Суммы нормализуются к
   * денежному масштабу, блокировки проверяются тем же правилом, что и {@link #lock(String,
   * BigDecimal)}.
   *
   * @throws IllegalArgumentException если остатки отрицательные или ключи валют/инструментов
   *     повторяются
   * @throws InvalidOrderException если заблокировано больше, чем есть на счёте
   */
  public static VirtualPortfolio restore(
      Map<String, BigDecimal> cashByCurrency,
      Map<String, BigDecimal> lockedByCurrency,
      List<PortfolioPosition> positions) {
    Objects.requireNonNull(cashByCurrency, "cashByCurrency");
    Objects.requireNonNull(lockedByCurrency, "lockedByCurrency");
    Objects.requireNonNull(positions, "positions");
    VirtualPortfolio portfolio = new VirtualPortfolio(cashByCurrency);
    for (Map.Entry<String, BigDecimal> entry : lockedByCurrency.entrySet()) {
      String currency = requireCurrency(entry.getKey());
      BigDecimal amount = entry.getValue();
      if (amount == null || amount.signum() < 0) {
        throw new IllegalArgumentException(
            "Заблокированная сумма " + currency + " не может быть отрицательной");
      }
      if (amount.signum() > 0) {
        portfolio.lock(currency, amount);
      }
    }
    addPositions(portfolio, positions);
    return portfolio;
  }

  /**
   * Восстанавливает портфель для клиринга: допускает отрицательный кэш и заблокированную сумму
   * больше остатка.
   *
   * <p>После убыточной вариационной маржи кэш может стать отрицательным, а ГО при этом остаётся
   * заблокированным. Обычная {@link #restore} такой снимок отвергает как нарушение инварианта
   * свободных средств, поэтому клиринг использует отдельную фабрику. Суммы нормализуются к
   * денежному масштабу; отрицательная блокировка по-прежнему считается ошибкой данных.
   *
   * @throws IllegalArgumentException если остатки не заданы или ключи инструментов повторяются
   */
  public static VirtualPortfolio restoreForSettlement(
      Map<String, BigDecimal> cashByCurrency,
      Map<String, BigDecimal> lockedByCurrency,
      List<PortfolioPosition> positions) {
    Objects.requireNonNull(cashByCurrency, "cashByCurrency");
    Objects.requireNonNull(lockedByCurrency, "lockedByCurrency");
    Objects.requireNonNull(positions, "positions");
    VirtualPortfolio portfolio = new VirtualPortfolio();
    for (Map.Entry<String, BigDecimal> entry : cashByCurrency.entrySet()) {
      String currency = requireCurrency(entry.getKey());
      BigDecimal amount = entry.getValue();
      if (amount == null) {
        throw new IllegalArgumentException("Остаток " + currency + " не задан");
      }
      portfolio.cash.put(currency, money(amount));
    }
    for (Map.Entry<String, BigDecimal> entry : lockedByCurrency.entrySet()) {
      String currency = requireCurrency(entry.getKey());
      BigDecimal amount = entry.getValue();
      if (amount == null || amount.signum() < 0) {
        throw new IllegalArgumentException(
            "Заблокированная сумма " + currency + " не может быть отрицательной");
      }
      portfolio.locked.put(currency, money(amount));
    }
    addPositions(portfolio, positions);
    return portfolio;
  }

  private static void addPositions(VirtualPortfolio portfolio, List<PortfolioPosition> positions) {
    for (PortfolioPosition position : positions) {
      Objects.requireNonNull(position, "position");
      String key = key(position.instrument());
      if (portfolio.positions.putIfAbsent(key, position) != null) {
        throw new IllegalArgumentException("Дубликат позиции " + key);
      }
    }
  }

  private long quantityOf(Instrument instrument) {
    PortfolioPosition position = positions.get(key(instrument));
    return position == null ? 0 : position.quantity();
  }

  private void applyPosition(Instrument instrument, long delta, BigDecimal price) {
    String key = key(instrument);
    PortfolioPosition current = positions.get(key);
    long oldQuantity = current == null ? 0 : current.quantity();
    BigDecimal oldAverage = current == null ? BigDecimal.ZERO : current.averagePrice();
    long newQuantity = oldQuantity + delta;
    if (newQuantity == 0) {
      positions.remove(key);
      return;
    }
    positions.put(
        key,
        new PortfolioPosition(
            instrument, newQuantity, nextAveragePrice(oldQuantity, oldAverage, delta, price)));
  }

  /**
   * Средняя цена при увеличении позиции пересчитывается взвешенно по абсолютным объёмам; при
   * сокращении не меняется; при перевороте через ноль равна цене новой сделки.
   */
  private static BigDecimal nextAveragePrice(
      long oldQuantity, BigDecimal oldAverage, long delta, BigDecimal price) {
    if (oldQuantity == 0) {
      return price;
    }
    long newQuantity = oldQuantity + delta;
    if (Long.signum(oldQuantity) == Long.signum(delta)) {
      if (Math.abs(newQuantity) <= Math.abs(oldQuantity)) {
        return oldAverage;
      }
      BigDecimal oldVolume = oldAverage.multiply(BigDecimal.valueOf(Math.abs(oldQuantity)));
      BigDecimal addedVolume = price.multiply(BigDecimal.valueOf(Math.abs(delta)));
      return oldVolume
          .add(addedVolume)
          .divide(BigDecimal.valueOf(Math.abs(newQuantity)), AVERAGE_PRICE_SCALE, ROUNDING);
    }
    return Long.signum(newQuantity) == Long.signum(oldQuantity) ? oldAverage : price;
  }

  /**
   * Списывает комиссию фьючерсной сделки без проверки свободных средств.
   *
   * <p>Обеспечение фьючерса проверяет сервис исполнения по дельте ГО: открытие и увеличение позиции
   * требуют покрытия дельты, закрытие и уменьшение — нет. Проверка свободного остатка здесь
   * запретила бы закрыть убыточную позицию после отрицательной вариационной маржи.
   */
  private void chargeFuturesCommission(String currency, BigDecimal commission) {
    cash.merge(currency, commission.negate(), BigDecimal::add);
  }

  private void debit(String currency, BigDecimal amount) {
    BigDecimal available = availableCash(currency);
    if (available.compareTo(amount) < 0) {
      throw new InvalidOrderException(
          "Недостаточно средств " + currency + ": требуется " + amount + ", доступно " + available);
    }
    cash.merge(currency, amount.negate(), BigDecimal::add);
  }

  private static boolean isFutures(Instrument instrument) {
    return FUTURES_TYPE.equalsIgnoreCase(instrument.type());
  }

  private static void validateTrade(
      Instrument instrument,
      long quantity,
      BigDecimal price,
      BigDecimal commission,
      Instant executedAt) {
    Objects.requireNonNull(instrument, "instrument");
    Objects.requireNonNull(executedAt, "executedAt");
    if (quantity <= 0) {
      throw new InvalidOrderException("orderQuantity должен быть больше 0");
    }
    if (price == null || price.signum() <= 0) {
      throw new InvalidOrderException("price должен быть положительным");
    }
    if (commission == null || commission.signum() < 0) {
      throw new InvalidOrderException("commission не может быть отрицательной");
    }
  }

  private static BigDecimal money(BigDecimal value) {
    return value.setScale(MONEY_SCALE, ROUNDING);
  }

  private static BigDecimal positiveMoney(BigDecimal value, String name) {
    if (value == null || value.signum() <= 0) {
      throw new InvalidOrderException(name + " должен быть положительным");
    }
    return money(value);
  }

  private static String requireCurrency(String currency) {
    if (currency == null || currency.isBlank()) {
      throw new IllegalArgumentException("currency не может быть пустой");
    }
    return currency;
  }

  private static String key(Instrument instrument) {
    return instrument.ticker() + "|" + instrument.classCode();
  }
}
