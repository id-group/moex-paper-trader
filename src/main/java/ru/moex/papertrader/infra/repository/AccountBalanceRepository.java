package ru.moex.papertrader.infra.repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.moex.papertrader.infra.model.AccountBalanceEntity;

/** Денежные средства и блокировки по валютам. */
public interface AccountBalanceRepository extends JpaRepository<AccountBalanceEntity, Long> {

  List<AccountBalanceEntity> findByAccountId(String accountId);

  Optional<AccountBalanceEntity> findByAccountIdAndCurrency(String accountId, String currency);

  /**
   * Идемпотентно создаёт остаток по валюте. Существующая строка не изменяется: накопленный баланс
   * после перезапуска сохраняется.
   */
  @Modifying(flushAutomatically = true)
  @Query(
      value =
          "INSERT INTO account_balances"
              + " (account_id, currency, cash, locked, updated_at)"
              + " VALUES (:accountId, :currency, :cash, :locked, :updatedAt)"
              + " ON CONFLICT (account_id, currency) DO NOTHING",
      nativeQuery = true)
  int insertIfAbsent(
      @Param("accountId") String accountId,
      @Param("currency") String currency,
      @Param("cash") BigDecimal cash,
      @Param("locked") BigDecimal locked,
      @Param("updatedAt") Instant updatedAt);

  /**
   * Записывает денежные средства и блокировку баланса после сделки. Сущность неизменяемая и
   * сеттеров не имеет, поэтому обновление выполняется запросом.
   */
  @Modifying(flushAutomatically = true)
  @Query(
      "update AccountBalanceEntity b"
          + " set b.cash = :cash, b.locked = :locked, b.updatedAt = :updatedAt"
          + " where b.accountId = :accountId and b.currency = :currency")
  int updateBalances(
      @Param("accountId") String accountId,
      @Param("currency") String currency,
      @Param("cash") BigDecimal cash,
      @Param("locked") BigDecimal locked,
      @Param("updatedAt") Instant updatedAt);
}
