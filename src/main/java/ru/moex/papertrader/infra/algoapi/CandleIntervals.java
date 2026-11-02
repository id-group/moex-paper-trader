package ru.moex.papertrader.infra.algoapi;

import java.util.OptionalInt;
import ru.moex.papertrader.core.marketdata.TimeFrame;

/**
 * Соответствие таймфреймов core интервалам свечей ISS/AlgoPack.
 *
 * <p>Обе площадки поддерживают только интервалы 1, 10, 60, 24, 7, 31 минуты/часы/дни (4 — квартал):
 * часовые {@code H4}, а также {@code M5}, {@code M15}, {@code M30} недоступны и возвращаются как
 * пустая серия с предупреждением в лог.
 */
final class CandleIntervals {

  private CandleIntervals() {}

  /** Код интервала ISS/AlgoPack для таймфрейма; пусто, если площадка его не поддерживает. */
  static OptionalInt toIntervalCode(TimeFrame timeFrame) {
    if (timeFrame == null) {
      return OptionalInt.empty();
    }
    return switch (timeFrame) {
      case M1 -> OptionalInt.of(1);
      case H1 -> OptionalInt.of(60);
      case D -> OptionalInt.of(24);
      case W -> OptionalInt.of(7);
      case MN -> OptionalInt.of(31);
      case M5, M15, M30, H4 -> OptionalInt.empty();
    };
  }
}
