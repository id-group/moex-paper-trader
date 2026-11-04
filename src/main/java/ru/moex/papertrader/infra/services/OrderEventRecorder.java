package ru.moex.papertrader.infra.services;

import org.springframework.stereotype.Component;
import ru.moex.papertrader.core.execution.OrderEvent;
import ru.moex.papertrader.infra.mapper.OrderEventMapper;
import ru.moex.papertrader.infra.repository.OrderEventRepository;

/**
 * Записывает события жизненного цикла заявок в журнал {@code order_events}.
 *
 * <p>Компонент не открывает собственную транзакцию: событие должно попасть в ту же транзакцию, что
 * и изменение заявки, иначе журнал разойдётся с состоянием {@code orders}.
 */
@Component
public class OrderEventRecorder {

  private final OrderEventRepository events;
  private final OrderEventMapper mapper;

  public OrderEventRecorder(OrderEventRepository events, OrderEventMapper mapper) {
    this.events = events;
    this.mapper = mapper;
  }

  public void record(OrderEvent event) {
    events.save(mapper.toEntity(event));
  }
}
