package ru.moex.papertrader.infra.repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.moex.papertrader.infra.model.FillEntity;

/** Исполненные сделки. */
public interface FillRepository extends JpaRepository<FillEntity, Long> {

  List<FillEntity> findByOrderId(String orderId);

  /**
   * Сумма комиссий счёта за полуинтервал времени {@code [from, to)}; {@code null}, если сделок нет.
   * Нужна компоненте комиссии дня NAV-среза.
   */
  @Query(
      "select sum(f.commission) from FillEntity f"
          + " where f.accountId = :accountId and f.executedAt >= :from and f.executedAt < :to")
  BigDecimal sumCommissionByAccountIdAndExecutedAtBetween(
      @Param("accountId") String accountId, @Param("from") Instant from, @Param("to") Instant to);
}
