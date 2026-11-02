package ru.moex.papertrader.infra.repository;

import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import ru.moex.papertrader.infra.model.MarketDataSnapshotEntity;

/** Снапшоты потока рыночных данных: запись RECORD и таймлайн REPLAY. */
public interface MarketDataSnapshotRepository
    extends JpaRepository<MarketDataSnapshotEntity, Long> {

  /** Снапшоты инструмента за окно {@code [from, to]} по возрастанию момента события. */
  List<MarketDataSnapshotEntity> findByTickerAndClassCodeAndTsBetweenOrderByTsAsc(
      String ticker, String classCode, Instant from, Instant to);

  /** Все снапшоты за окно {@code [from, to]} по возрастанию момента события (таймлайн REPLAY). */
  List<MarketDataSnapshotEntity> findByTsBetweenOrderByTsAsc(Instant from, Instant to);

  /** Момент самого раннего снапшота; {@code null} — хранилище пусто (старт таймлайна REPLAY). */
  @Query("SELECT MIN(s.ts) FROM MarketDataSnapshotEntity s")
  Instant earliestTimestamp();

  /** Удаляет снапшоты старше отсечки и возвращает число удалённых строк (retention RECORD). */
  long deleteByTsBefore(Instant cutoff);
}
