package ru.moex.papertrader.infra.repository;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import ru.moex.papertrader.infra.model.OrderEventEntity;

/** Журнал переходов жизненного цикла заявок. */
public interface OrderEventRepository extends JpaRepository<OrderEventEntity, Long> {

  /** События заявки в хронологическом порядке; id разрешает одинаковые метки времени. */
  List<OrderEventEntity> findByOrderIdOrderByOccurredAtAscIdAsc(String orderId);
}
