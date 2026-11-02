package ru.moex.papertrader.infra.repository;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.moex.papertrader.infra.model.TradingCalendarEntity;

/** Кэш торгового календаря MOEX (движок, рынок, режим торгов). */
public interface TradingCalendarRepository extends JpaRepository<TradingCalendarEntity, Long> {

  /** Дни диапазона дат по возрастанию даты; включает и неторговые дни. */
  List<TradingCalendarEntity> findByEngineAndMarketAndBoardAndTradeDateBetweenOrderByTradeDate(
      String engine, String market, String board, LocalDate from, LocalDate to);

  /** Строка календаря на конкретный день, если она уже закэширована. */
  Optional<TradingCalendarEntity> findByEngineAndMarketAndBoardAndTradeDate(
      String engine, String market, String board, LocalDate tradeDate);

  /** Ближайший торговый день после указанного — для {@code TradingSession.nextSessionAt}. */
  Optional<TradingCalendarEntity>
      findTopByEngineAndMarketAndBoardAndTradeDateGreaterThanAndTradingTrueOrderByTradeDateAsc(
          String engine, String market, String board, LocalDate tradeDate);

  /**
   * Идемпотентно записывает день календаря. Повторная загрузка диапазона не создаёт дубликатов и
   * обновляет окно сессии: прошлые дни не меняются, но перезапись безопасна.
   */
  @Modifying(flushAutomatically = true)
  @Query(
      value =
          "INSERT INTO trading_calendar"
              + " (engine, market, board, trade_date, is_trading, open_utc, close_utc)"
              + " VALUES (:engine, :market, :board, :tradeDate, :trading,"
              + " CAST(:openUtc AS TIMESTAMP WITH TIME ZONE),"
              + " CAST(:closeUtc AS TIMESTAMP WITH TIME ZONE))"
              + " ON CONFLICT (engine, market, board, trade_date) DO UPDATE"
              + " SET is_trading = EXCLUDED.is_trading,"
              + " open_utc = EXCLUDED.open_utc,"
              + " close_utc = EXCLUDED.close_utc",
      nativeQuery = true)
  int upsert(
      @Param("engine") String engine,
      @Param("market") String market,
      @Param("board") String board,
      @Param("tradeDate") LocalDate tradeDate,
      @Param("trading") boolean trading,
      @Param("openUtc") Instant openUtc,
      @Param("closeUtc") Instant closeUtc);
}
