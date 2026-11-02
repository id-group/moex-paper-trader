package ru.moex.papertrader.infra.repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.moex.papertrader.infra.model.PositionEntity;

/** Позиции счёта по инструментам. */
public interface PositionRepository extends JpaRepository<PositionEntity, Long> {

  Optional<PositionEntity> findByAccountIdAndInstrumentId(String accountId, Long instrumentId);

  List<PositionEntity> findByAccountId(String accountId);

  /**
   * Записывает позицию после сделки: вставляет новую строку или обновляет количество и среднюю цену
   * существующей. Сущность неизменяемая, поэтому обновление выполняется запросом.
   */
  @Modifying(flushAutomatically = true)
  @Query(
      value =
          "INSERT INTO positions (account_id, instrument_id, quantity, average_price, updated_at)"
              + " VALUES (:accountId, :instrumentId, :quantity, :averagePrice, :updatedAt)"
              + " ON CONFLICT (account_id, instrument_id) DO UPDATE"
              + " SET quantity = EXCLUDED.quantity,"
              + " average_price = EXCLUDED.average_price,"
              + " updated_at = EXCLUDED.updated_at",
      nativeQuery = true)
  int upsert(
      @Param("accountId") String accountId,
      @Param("instrumentId") Long instrumentId,
      @Param("quantity") long quantity,
      @Param("averagePrice") BigDecimal averagePrice,
      @Param("updatedAt") Instant updatedAt);

  /** Удаляет закрытую позицию: схема запрещает строку с нулевым количеством. */
  @Modifying(flushAutomatically = true)
  @Query(
      "delete from PositionEntity p"
          + " where p.accountId = :accountId and p.instrumentId = :instrumentId")
  int deleteByAccountIdAndInstrumentId(
      @Param("accountId") String accountId, @Param("instrumentId") Long instrumentId);
}
