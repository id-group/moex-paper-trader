package ru.moex.papertrader.infra.repository;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import ru.moex.papertrader.infra.model.ReportEntity;

/** Сохранённые отчёты (этап 6): новые генерации — первыми. */
public interface ReportRepository extends JpaRepository<ReportEntity, Long> {

  List<ReportEntity> findByAccountIdOrderByGeneratedAtDescIdDesc(String accountId);

  List<ReportEntity> findAllByOrderByGeneratedAtDescIdDesc();
}
