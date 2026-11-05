package ru.moex.papertrader.it;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import javax.sql.DataSource;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.jdbc.core.JdbcTemplate;
import ru.moex.papertrader.core.domain.Instrument;
import ru.moex.papertrader.core.marketdata.TradingDay;
import ru.moex.papertrader.infra.mapper.MarketDataSnapshotMapper;
import ru.moex.papertrader.infra.model.MarketDataSnapshotEntity;
import ru.moex.papertrader.infra.services.MarketDataProperties;
import ru.moex.papertrader.infra.services.MarketDataSnapshotStore;
import ru.moex.papertrader.it.replay.ReplayDataset;

/**
 * Наполняет БД детерминированным REPLAY-набором до создания провайдера рыночных данных.
 *
 * <p>Таймлайн REPLAY читается целиком в момент создания бина {@code MarketDataProvider}, поэтому
 * сидинг из {@code @BeforeEach} слишком поздний. Этот {@link BeanPostProcessor} срабатывает сразу
 * после инициализации {@link MarketDataSnapshotStore} (схема Flyway к этому моменту уже применена)
 * и пишет инструменты, торговый календарь и снапшоты обычным JDBC — без транзакционных прокси и без
 * зависимости от порядка создания остальных бинов.
 *
 * <p>Операция идемпотентна: строки окна набора удаляются перед вставкой, поэтому повторный запуск
 * теста в том же контейнере PostgreSQL не плодит дубликаты.
 */
final class ReplaySeedSupport implements BeanPostProcessor, BeanFactoryAware {

  private static final String INSERT_SNAPSHOT =
      "INSERT INTO market_data_snapshots (ts, kind, ticker, class_code, payload)"
          + " VALUES (?, ?, ?, ?, ?::jsonb)";

  private BeanFactory beanFactory;
  private boolean seeded;

  @Override
  public void setBeanFactory(BeanFactory beanFactory) {
    this.beanFactory = beanFactory;
  }

  @Override
  public Object postProcessAfterInitialization(Object bean, String beanName) {
    if (!seeded && bean instanceof MarketDataSnapshotStore) {
      seeded = true;
      seed();
    }
    return bean;
  }

  private void seed() {
    ReplayDataset dataset = ReplayDataset.standard();
    DataSource dataSource = beanFactory.getBean(DataSource.class);
    MarketDataSnapshotMapper mapper = beanFactory.getBean(MarketDataSnapshotMapper.class);
    MarketDataProperties properties = beanFactory.getBean(MarketDataProperties.class);
    JdbcTemplate jdbc = new JdbcTemplate(dataSource);

    deleteWindow(jdbc, dataset, properties);
    insertInstruments(jdbc, dataset.instruments());
    insertCalendar(jdbc, dataset.tradingDays(), properties);
    insertSnapshots(jdbc, dataset, mapper);
  }

  /** Удаляет данные предыдущего прогона в окне набора: сидинг должен быть повторяемым. */
  private void deleteWindow(
      JdbcTemplate jdbc, ReplayDataset dataset, MarketDataProperties properties) {
    OffsetDateTime from = utc(dataset.start().minus(Duration.ofDays(2)));
    OffsetDateTime to = utc(dataset.end().plus(Duration.ofDays(2)));
    jdbc.update("DELETE FROM market_data_snapshots WHERE ts BETWEEN ? AND ?", from, to);
    LocalDate first = dataset.tradingDates().get(0).minusDays(2);
    LocalDate last = dataset.tradingDates().get(dataset.tradingDates().size() - 1).plusDays(2);
    jdbc.update(
        "DELETE FROM trading_calendar"
            + " WHERE engine = ? AND market = ? AND board = ? AND trade_date BETWEEN ? AND ?",
        properties.calendarEngine(),
        properties.calendarMarket(),
        properties.calendarBoard(),
        first,
        last);
  }

  private void insertInstruments(JdbcTemplate jdbc, List<Instrument> instruments) {
    for (Instrument instrument : instruments) {
      jdbc.update(
          "INSERT INTO instruments"
              + " (ticker, class_code, type, short_name, display_name, isin, lot_size,"
              + "  minimum_step, currency)"
              + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)"
              + " ON CONFLICT (ticker, class_code) DO NOTHING",
          instrument.ticker(),
          instrument.classCode(),
          instrument.type(),
          instrument.shortName(),
          instrument.displayName(),
          instrument.isin(),
          instrument.lotSize(),
          instrument.minimumStep(),
          instrument.currency());
    }
  }

  private void insertCalendar(
      JdbcTemplate jdbc, List<TradingDay> tradingDays, MarketDataProperties properties) {
    for (TradingDay day : tradingDays) {
      LocalDate date = day.date();
      jdbc.update(
          "INSERT INTO trading_calendar"
              + " (engine, market, board, trade_date, is_trading, open_utc, close_utc)"
              + " VALUES (?, ?, ?, ?, ?, ?, ?)"
              + " ON CONFLICT (engine, market, board, trade_date)"
              + " DO UPDATE SET is_trading = EXCLUDED.is_trading,"
              + "               open_utc = EXCLUDED.open_utc,"
              + "               close_utc = EXCLUDED.close_utc",
          properties.calendarEngine(),
          properties.calendarMarket(),
          properties.calendarBoard(),
          date,
          day.trading(),
          day.openUtc() == null ? null : utc(day.openUtc()),
          day.closeUtc() == null ? null : utc(day.closeUtc()));
    }
  }

  private void insertSnapshots(
      JdbcTemplate jdbc, ReplayDataset dataset, MarketDataSnapshotMapper mapper) {
    List<MarketDataSnapshotEntity> entities =
        dataset.snapshots().stream().map(mapper::toEntity).toList();
    jdbc.batchUpdate(
        INSERT_SNAPSHOT,
        entities,
        500,
        (statement, entity) -> {
          statement.setObject(1, utc(entity.getTs()));
          statement.setString(2, entity.getKind());
          statement.setString(3, entity.getTicker());
          statement.setString(4, entity.getClassCode());
          statement.setString(5, entity.getPayload());
        });
  }

  /** Момент времени в UTC: драйвер PostgreSQL принимает {@link OffsetDateTime} для timestamptz. */
  private static OffsetDateTime utc(Instant instant) {
    return instant.atOffset(ZoneOffset.UTC);
  }
}
