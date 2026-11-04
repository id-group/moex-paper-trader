package ru.moex.papertrader.infra.repository;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import ru.moex.papertrader.infra.model.BacktestResultEntity;

/** Результаты сравнения с внешним бэктестом: новые сравнения — первыми. */
public interface BacktestResultRepository extends JpaRepository<BacktestResultEntity, Long> {

  List<BacktestResultEntity> findByAccountIdOrderByComparedAtDescIdDesc(String accountId);
}
