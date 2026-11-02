package ru.moex.papertrader.infra.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import ru.moex.papertrader.infra.model.OrderEntity;

/** Заявки эмулятора. */
public interface OrderRepository extends JpaRepository<OrderEntity, String> {

  Optional<OrderEntity> findByClientOrderId(UUID clientOrderId);

  boolean existsByClientOrderId(UUID clientOrderId);

  /** Заявки счёта в стабильном порядке нумерации — база для поиска и активных заявок. */
  List<OrderEntity> findByAccountIdOrderByOrderNumAsc(String accountId);

  /**
   * Текущий максимум номера заявки. {@code order_num} уникален в таблице, поэтому нумерация ведётся
   * от глобального максимума, а не от максимума счёта.
   */
  @Query("select max(o.orderNum) from OrderEntity o")
  Optional<Long> findMaxOrderNum();
}
