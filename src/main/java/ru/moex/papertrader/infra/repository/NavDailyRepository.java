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
}
