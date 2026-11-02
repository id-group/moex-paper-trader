package ru.moex.papertrader.infra.repository;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.moex.papertrader.infra.model.AccountEntity;

/** Счета портфеля. */
public interface AccountRepository extends JpaRepository<AccountEntity, String> {

  /**
   * Идемпотентно создаёт счёт. {@code ON CONFLICT DO NOTHING} вместо {@code save}: падение вставки
   * на уникальном ключе в PostgreSQL прерывает транзакцию и не даёт перечитать строку конкурента.
   */
  @Modifying(flushAutomatically = true)
  @Query(
      value =
          "INSERT INTO accounts (account_id, created_at) VALUES (:accountId, :createdAt)"
              + " ON CONFLICT (account_id) DO NOTHING",
      nativeQuery = true)
  int insertIfAbsent(@Param("accountId") String accountId, @Param("createdAt") Instant createdAt);

  /** Блокирует строку счёта до конца транзакции: под ней нумеруются заявки. */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select account from AccountEntity account where account.accountId = :accountId")
  Optional<AccountEntity> lockByAccountId(@Param("accountId") String accountId);
}
