package ru.moex.papertrader.infra.services;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import ru.moex.papertrader.core.marketdata.Clock;
import ru.moex.papertrader.core.marketdata.VirtualClock;

/**
 * Наполнение торгового календаря в LIVE/RECORD: синхронизация с ISS при старте приложения и
 * периодически после. Без неё в LIVE календарь оставался бы пустым (вызовы {@code tradingDays()}
 * есть только у инициализации REPLAY), а статус сессии всегда определялся бы fallback-окном.
 *
 * <p>Диапазон — неделя назад от текущего момента: прошлые дни и вчера догружаются из истории ISS,
 * текущий день определяется по живым минутным свечам (см. {@link TradingCalendarService}).
 *
 * <p>В режиме REPLAY не работает: там часы — {@link VirtualClock}, а календарь строится из
 * записанных снапшотов. Ошибки источника не прерывают старт приложения.
 */
@Component
public class TradingCalendarInitializer {

  private static final Logger log = LoggerFactory.getLogger(TradingCalendarInitializer.class);

  /** Глубина дозагрузки прошлых дней при синхронизации. */
  private static final Duration PAST_WINDOW = Duration.ofDays(7);

  /** Период повторной синхронизации, мс (6 часов). */
  private static final long REFRESH_DELAY_MS = 6 * 60 * 60 * 1000L;

  private final TradingCalendarService calendar;
  private final Clock clock;

  public TradingCalendarInitializer(TradingCalendarService calendar, Clock clock) {
    this.calendar = Objects.requireNonNull(calendar, "calendar");
    this.clock = Objects.requireNonNull(clock, "clock");
  }

  @EventListener(ApplicationReadyEvent.class)
  @Scheduled(fixedDelay = REFRESH_DELAY_MS, initialDelay = REFRESH_DELAY_MS)
  public void synchronize() {
    if (clock instanceof VirtualClock) {
      return; // REPLAY: календарь из записанных снапшотов, сеть не нужна
    }
    Instant now = clock.instant();
    try {
      calendar.tradingDays(now.minus(PAST_WINDOW), now);
    } catch (RuntimeException exception) {
      // Деградация некритична: статус сессии временно определит fallback-окно настроек.
      log.warn("Синхронизация торгового календаря не удалась: {}", exception.getMessage());
    }
  }
}
