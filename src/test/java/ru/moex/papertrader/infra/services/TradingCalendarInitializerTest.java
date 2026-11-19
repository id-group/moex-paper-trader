package ru.moex.papertrader.infra.services;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import ru.moex.papertrader.core.marketdata.VirtualClock;

/** Наполнение торгового календаря при старте: окно синхронизации и пропуск в режиме REPLAY. */
class TradingCalendarInitializerTest {

  private static final Instant NOW = Instant.parse("2026-11-02T10:00:00Z");

  private final TradingCalendarService calendar = mock(TradingCalendarService.class);
  private final ru.moex.papertrader.core.marketdata.Clock clock =
      mock(ru.moex.papertrader.core.marketdata.Clock.class);

  @Test
  void synchronizesWeekBackUpToNow() {
    when(clock.instant()).thenReturn(NOW);
    TradingCalendarInitializer initializer = new TradingCalendarInitializer(calendar, clock);

    initializer.synchronize();

    verify(calendar).tradingDays(NOW.minus(Duration.ofDays(7)), NOW);
  }

  @Test
  void refreshesBoundsOfAlreadyCachedDays() {
    // tradingDays() известные дни пропускает, поэтому границы, записанные вслепую (fallback-окно
    // настроек или плановое закрытие идущего дня), перезаписываются из данных биржи отдельно.
    when(clock.instant()).thenReturn(NOW);
    TradingCalendarInitializer initializer = new TradingCalendarInitializer(calendar, clock);

    initializer.synchronize();

    verify(calendar).refreshLiveBounds(NOW);
  }

  @Test
  void virtualClockMeansReplayAndIsSkipped() {
    TradingCalendarInitializer initializer =
        new TradingCalendarInitializer(calendar, new VirtualClock(NOW));

    initializer.synchronize();

    verifyNoInteractions(calendar);
  }

  @Test
  void failuresOfCalendarDoNotPropagate() {
    when(clock.instant()).thenReturn(NOW);
    org.mockito.Mockito.doThrow(new IllegalStateException("БД недоступна"))
        .when(calendar)
        .tradingDays(any(), any());
    TradingCalendarInitializer initializer = new TradingCalendarInitializer(calendar, clock);

    initializer.synchronize(); // не бросает наружу
  }

  @Test
  void failuresOfBoundsRefreshDoNotPropagate() {
    when(clock.instant()).thenReturn(NOW);
    org.mockito.Mockito.doThrow(new IllegalStateException("ISS недоступен"))
        .when(calendar)
        .refreshLiveBounds(any());
    TradingCalendarInitializer initializer = new TradingCalendarInitializer(calendar, clock);

    initializer.synchronize(); // не бросает наружу
  }
}
