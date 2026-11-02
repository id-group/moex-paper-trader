package ru.moex.papertrader.infra.services;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.moex.papertrader.core.marketdata.MarketDataSnapshot;
import ru.moex.papertrader.infra.mapper.MarketDataSnapshotMapper;
import ru.moex.papertrader.infra.model.MarketDataSnapshotEntity;
import ru.moex.papertrader.infra.repository.MarketDataSnapshotRepository;

/**
 * Хранилище снапшотов рыночных данных: запись RECORD и чтение таймлайна REPLAY.
 *
 * <p>Времена передаются вызывающим: сервис не обращается к системным часам, поэтому запись и
 * очистка детерминированы. Для очистки по retention вызывающий передаёт текущий момент в {@link
 * #purgeExpired(Instant)}, а отсечка вычисляется как {@code now - retentionDays}.
 */
@Service
public class MarketDataSnapshotStore {

  private final MarketDataSnapshotRepository snapshots;
  private final MarketDataSnapshotMapper mapper;
  private final MarketDataProperties properties;

  public MarketDataSnapshotStore(
      MarketDataSnapshotRepository snapshots,
      MarketDataSnapshotMapper mapper,
      MarketDataProperties properties) {
    this.snapshots = snapshots;
    this.mapper = mapper;
    this.properties = properties;
  }

  /** Записывает пачку снапшотов в одной транзакции RECORD. */
  @Transactional
  public void save(List<MarketDataSnapshot> batch) {
    Objects.requireNonNull(batch, "batch");
    if (batch.isEmpty()) {
      return;
    }
    List<MarketDataSnapshotEntity> entities = batch.stream().map(mapper::toEntity).toList();
    snapshots.saveAll(entities);
  }

  /** Снапшоты инструмента за окно {@code [from, to]} по возрастанию момента события. */
  public List<MarketDataSnapshot> load(String ticker, String classCode, Instant from, Instant to) {
    requireText(ticker, "ticker");
    requireText(classCode, "classCode");
    Objects.requireNonNull(from, "from");
    Objects.requireNonNull(to, "to");
    return snapshots
        .findByTickerAndClassCodeAndTsBetweenOrderByTsAsc(ticker, classCode, from, to)
        .stream()
        .map(mapper::toDomain)
        .toList();
  }

  /** Все снапшоты за окно {@code [from, to]} по возрастанию момента события (таймлайн REPLAY). */
  public List<MarketDataSnapshot> loadAll(Instant from, Instant to) {
    Objects.requireNonNull(from, "from");
    Objects.requireNonNull(to, "to");
    return snapshots.findByTsBetweenOrderByTsAsc(from, to).stream().map(mapper::toDomain).toList();
  }

  /** Момент самого раннего снапшота; {@code null} — хранилище пусто (старт таймлайна REPLAY). */
  public Instant earliestTimestamp() {
    return snapshots.earliestTimestamp();
  }

  /** Удаляет снапшоты старше отсечки; возвращает число удалённых строк. */
  @Transactional
  public long deleteBefore(Instant cutoff) {
    Objects.requireNonNull(cutoff, "cutoff");
    return snapshots.deleteByTsBefore(cutoff);
  }

  /**
   * Чистка по retention: удаляет снапшоты старше {@code now - retentionDays}. Текущий момент
   * передаёт вызывающий — сервис не использует системные часы.
   */
  @Transactional
  public long purgeExpired(Instant now) {
    Objects.requireNonNull(now, "now");
    Instant cutoff = now.minus(Duration.ofDays(properties.retentionDays()));
    return snapshots.deleteByTsBefore(cutoff);
  }

  private static void requireText(String value, String name) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(name + " не может быть пустым");
    }
  }
}
