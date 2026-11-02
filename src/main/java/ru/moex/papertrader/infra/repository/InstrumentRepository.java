package ru.moex.papertrader.infra.repository;

import java.math.BigDecimal;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.moex.papertrader.infra.model.InstrumentEntity;

/** Инструменты: доменная идентичность — пара (ticker, classCode). */
public interface InstrumentRepository extends JpaRepository<InstrumentEntity, Long> {

  Optional<InstrumentEntity> findByTickerAndClassCode(String ticker, String classCode);

  /**
   * Идемпотентно создаёт инструмент. {@code ON CONFLICT DO NOTHING} вместо {@code save}: гонка двух
   * контекстов не должна прерывать транзакцию заявки, после вставки строка перечитывается.
   */
  @Modifying(flushAutomatically = true)
  @Query(
      value =
          "INSERT INTO instruments"
              + " (ticker, class_code, type, short_name, display_name, isin, lot_size,"
              + " minimum_step, currency)"
              + " VALUES (:ticker, :classCode, :type, :shortName, :displayName, :isin,"
              + " :lotSize, :minimumStep, :currency)"
              + " ON CONFLICT (ticker, class_code) DO NOTHING",
      nativeQuery = true)
  int insertIfAbsent(
      @Param("ticker") String ticker,
      @Param("classCode") String classCode,
      @Param("type") String type,
      @Param("shortName") String shortName,
      @Param("displayName") String displayName,
      @Param("isin") String isin,
      @Param("lotSize") BigDecimal lotSize,
      @Param("minimumStep") BigDecimal minimumStep,
      @Param("currency") String currency);
}
