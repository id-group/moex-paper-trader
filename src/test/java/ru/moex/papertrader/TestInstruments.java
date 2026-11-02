package ru.moex.papertrader;

import java.math.BigDecimal;
import ru.moex.papertrader.core.domain.Instrument;

/** Общие тестовые инструменты. */
public final class TestInstruments {

  private TestInstruments() {}

  public static Instrument sber() {
    return new Instrument(
        "SBER",
        "TQBR",
        "STOCK",
        "Сбербанк",
        "Сбербанк",
        "RU0009029540",
        new BigDecimal("10"),
        new BigDecimal("0.01"),
        "RUB");
  }

  public static Instrument gazp() {
    return new Instrument(
        "GAZP",
        "TQBR",
        "STOCK",
        "Газпром",
        "Газпром",
        "RU0007661625",
        new BigDecimal("10"),
        new BigDecimal("0.01"),
        "RUB");
  }

  /** Фьючерс SiU6 — инструмент фьючерсных сценариев ГО и вариационной маржи. */
  public static Instrument siu6() {
    return new Instrument(
        "SiU6",
        "SPBFUT",
        "FUTURES",
        "Si-12.26",
        "Фьючерс на доллар США",
        "",
        BigDecimal.ONE,
        BigDecimal.ONE,
        "RUB");
  }
}
