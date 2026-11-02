package ru.moex.papertrader.core.commission;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Тарифы комиссии: базовый тариф по типу инструмента и необязательное переопределение для тикера.
 *
 * <p>Экземпляр неизменяем: {@link #withOverride(String, CommissionTariff)} возвращает новый реестр.
 */
public final class CommissionTariffRegistry {

  private final Map<InstrumentType, CommissionTariff> byType;
  private final Map<String, CommissionTariff> byTicker;

  /**
   * @param byType тарифы по типу инструмента; для используемых типов тариф должен быть задан
   * @param byTicker переопределения по тикеру (ключ нормализуется: trim + верхний регистр)
   */
  public CommissionTariffRegistry(
      Map<InstrumentType, CommissionTariff> byType, Map<String, CommissionTariff> byTicker) {
    if (byType == null) {
      throw new IllegalArgumentException("byType не может быть null");
    }
    if (byTicker == null) {
      throw new IllegalArgumentException("byTicker не может быть null");
    }
    EnumMap<InstrumentType, CommissionTariff> typeCopy = new EnumMap<>(InstrumentType.class);
    byType.forEach(
        (type, tariff) -> typeCopy.put(requireKey(type, "тип инструмента"), requireTariff(tariff)));
    Map<String, CommissionTariff> tickerCopy = new HashMap<>();
    byTicker.forEach(
        (ticker, tariff) -> tickerCopy.put(requireTicker(ticker), requireTariff(tariff)));
    this.byType = Map.copyOf(typeCopy);
    this.byTicker = Map.copyOf(tickerCopy);
  }

  /** Эталонные тарифы проекта: акции — 0.1% с минимумом, фьючерсы — сбор за контракт. */
  public static CommissionTariffRegistry defaults() {
    EnumMap<InstrumentType, CommissionTariff> byType = new EnumMap<>(InstrumentType.class);
    byType.put(InstrumentType.STOCK, CommissionTariff.stock());
    byType.put(InstrumentType.FUTURES, CommissionTariff.futures());
    return new CommissionTariffRegistry(byType, Map.of());
  }

  /** Возвращает копию реестра с тарифом для конкретного тикера. */
  public CommissionTariffRegistry withOverride(String ticker, CommissionTariff tariff) {
    Map<String, CommissionTariff> overrides = new HashMap<>(byTicker);
    overrides.put(requireTicker(ticker), requireTariff(tariff));
    return new CommissionTariffRegistry(byType, overrides);
  }

  /**
   * Тариф для сделки: переопределение по тикеру приоритетнее тарифа по типу инструмента.
   *
   * @param ticker тикер инструмента; {@code null} — переопределение не ищется
   * @throws IllegalStateException если тариф для типа не настроен
   */
  public CommissionTariff tariffFor(InstrumentType type, String ticker) {
    CommissionTariff typeTariff = byType.get(requireKey(type, "тип инструмента"));
    if (typeTariff == null) {
      throw new IllegalStateException("тариф комиссии не настроен для типа " + type);
    }
    if (ticker == null) {
      return typeTariff;
    }
    String normalized = normalizeTicker(ticker);
    CommissionTariff override = byTicker.get(normalized);
    return override != null ? override : typeTariff;
  }

  private static InstrumentType requireKey(InstrumentType type, String name) {
    if (type == null) {
      throw new IllegalArgumentException(name + " не может быть null");
    }
    return type;
  }

  private static CommissionTariff requireTariff(CommissionTariff tariff) {
    if (tariff == null) {
      throw new IllegalArgumentException("тариф не может быть null");
    }
    return tariff;
  }

  private static String requireTicker(String ticker) {
    if (ticker == null || ticker.isBlank()) {
      throw new IllegalArgumentException("ticker не может быть пустым");
    }
    return normalizeTicker(ticker);
  }

  /** Тикеры Мосбиржи — латиница в верхнем регистре; сравнение не зависит от регистра ввода. */
  private static String normalizeTicker(String ticker) {
    return ticker.trim().toUpperCase(Locale.ROOT);
  }
}
