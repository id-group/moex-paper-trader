package ru.moex.papertrader.infra.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import ru.moex.papertrader.infra.model.NavDailyEntity;

/** Ежедневные срезы NAV. */
public interface NavDailyRepository extends JpaRepository<NavDailyEntity, Long> {

  Optional<NavDailyEntity> findByAccountIdAndNavDate(String accountId, LocalDate navDate);

  List<NavDailyEntity> findByAccountIdOrderByNavDateAsc(String accountId);

  /**
   * Срезы NAV счёта за период включительно по обеим датам — кривая отчёта этапа 6. Границы
   * открытого интервала по времени журнал считает отдельно, здесь важен только день.
   */
  List<NavDailyEntity> findByAccountIdAndNavDateBetweenOrderByNavDateAsc(
      String accountId, LocalDate from, LocalDate to);
}
