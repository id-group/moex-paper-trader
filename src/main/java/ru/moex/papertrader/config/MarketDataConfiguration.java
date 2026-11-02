package ru.moex.papertrader.config;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import ru.moex.papertrader.core.domain.Instrument;
import ru.moex.papertrader.core.marketdata.Clock;
import ru.moex.papertrader.core.marketdata.MarketDataProvider;
import ru.moex.papertrader.core.marketdata.MarketDataSnapshot;
import ru.moex.papertrader.core.marketdata.OrderBookEvent;
import ru.moex.papertrader.core.marketdata.QuoteSnapshot;
import ru.moex.papertrader.core.marketdata.RecordingMarketDataProvider;
import ru.moex.papertrader.core.marketdata.ReplayMarketDataProvider;
import ru.moex.papertrader.core.marketdata.TradeEvent;
import ru.moex.papertrader.core.marketdata.TradingCalendar;
import ru.moex.papertrader.core.marketdata.TradingDay;
import ru.moex.papertrader.core.marketdata.VirtualClock;
import ru.moex.papertrader.infra.algoapi.AlgoPackClient;
import ru.moex.papertrader.infra.algoapi.AlgoPackMarketDataProvider;
import ru.moex.papertrader.infra.algoapi.AlgoPackProperties;
import ru.moex.papertrader.infra.algoapi.FallbackMarketDataProvider;
import ru.moex.papertrader.infra.algoapi.IssClient;
import ru.moex.papertrader.infra.algoapi.IssMarketDataProvider;
import ru.moex.papertrader.infra.algoapi.IssProperties;
import ru.moex.papertrader.infra.mapper.InstrumentMapper;
import ru.moex.papertrader.infra.model.InstrumentEntity;
import ru.moex.papertrader.infra.repository.InstrumentRepository;
import ru.moex.papertrader.infra.services.MarketDataSnapshotStore;
import ru.moex.papertrader.infra.services.ReplayProperties;
import ru.moex.papertrader.infra.services.StubMarketDataProperties;
import ru.moex.papertrader.infra.services.StubMarketDataProvider;

/**
 * Выбор провайдера рыночных данных по {@code emulator.marketdata.mode}.
 *
 * <p>STUB — заглушка (по умолчанию); LIVE — AlgoPack с деградацией до MOEX ISS (без токена AlgoPack
 * запросы падают с 4xx, и fallback отдаёт данные ISS); RECORD — LIVE + запись входящего потока в
 * {@code market_data_snapshots}; REPLAY — детерминированное воспроизведение записи по виртуальному
 * таймлайну ({@link VirtualClock} продвигает {@code ReplayClockAdvancer}).
 */
@Configuration
@EnableScheduling
public class MarketDataConfiguration {

  @Bean
  public MarketDataProvider marketDataProvider(
      @Value("${emulator.marketdata.mode:STUB}") String mode,
      StubMarketDataProperties stubProperties,
      AlgoPackProperties algoPackProperties,
      IssProperties issProperties,
      TradingCalendar calendar,
      MarketDataSnapshotStore store,
      InstrumentRepository instrumentRepository,
      InstrumentMapper instrumentMapper,
      ReplayProperties replay,
      Clock clock) {
    return switch (mode.trim().toUpperCase()) {
      case "STUB" -> new StubMarketDataProvider(stubProperties, clock);
      case "LIVE" -> liveProvider(algoPackProperties, issProperties, calendar, clock);
      case "RECORD" ->
          recordingProvider(
              liveProvider(algoPackProperties, issProperties, calendar, clock), store, clock);
      case "REPLAY" ->
          replayProvider(store, calendar, instrumentRepository, instrumentMapper, replay, clock);
      default -> throw new IllegalStateException("Неизвестный режим рыночных данных: " + mode);
    };
  }

  /** LIVE-стек: AlgoPack как основной источник, MOEX ISS — fallback. */
  private static MarketDataProvider liveProvider(
      AlgoPackProperties algoPackProperties,
      IssProperties issProperties,
      TradingCalendar calendar,
      Clock clock) {
    IssClient issClient = new IssClient(issProperties);
    IssMarketDataProvider issProvider =
        new IssMarketDataProvider(issClient, calendar, issProperties, clock);
    AlgoPackClient algoPackClient = new AlgoPackClient(algoPackProperties);
    AlgoPackMarketDataProvider algoPackProvider =
        new AlgoPackMarketDataProvider(
            algoPackClient, issClient, calendar, algoPackProperties, clock);
    return new FallbackMarketDataProvider(algoPackProvider, issProvider);
  }

  /** RECORD: декоратор LIVE, пишущий снапшоты в хранилище. */
  private static MarketDataProvider recordingProvider(
      MarketDataProvider live, MarketDataSnapshotStore store, Clock clock) {
    return new RecordingMarketDataProvider(live, snapshot -> store.save(List.of(snapshot)), clock);
  }

  /** REPLAY: таймлайн из записи, справочник инструментов из БД, календарь из кэша. */
  private static MarketDataProvider replayProvider(
      MarketDataSnapshotStore store,
      TradingCalendar calendar,
      InstrumentRepository instrumentRepository,
      InstrumentMapper instrumentMapper,
      ReplayProperties replay,
      Clock clock) {
    if (!(clock instanceof VirtualClock virtualClock)) {
      throw new IllegalStateException("В режиме REPLAY порт Clock должен быть VirtualClock");
    }
    Instant from = replay.start() != null ? replay.start() : Instant.EPOCH;
    List<MarketDataSnapshot> timeline = store.loadAll(from, Instant.MAX);
    Map<String, Instrument> instruments =
        instrumentsOf(timeline, instrumentRepository, instrumentMapper);
    Instant calendarTo = timeline.isEmpty() ? virtualClock.instant() : timelineLast(timeline);
    List<TradingDay> tradingDays = calendar.tradingDays(from, calendarTo.plus(Duration.ofDays(1)));
    return new ReplayMarketDataProvider(timeline, virtualClock, instruments, tradingDays);
  }

  /**
   * Справочник инструментов REPLAY: пары (ticker, classCode) из таймлайна → таблица instruments.
   */
  private static Map<String, Instrument> instrumentsOf(
      List<MarketDataSnapshot> timeline,
      InstrumentRepository instrumentRepository,
      InstrumentMapper instrumentMapper) {
    Map<String, Instrument> instruments = new HashMap<>();
    for (MarketDataSnapshot snapshot : timeline) {
      String ticker = snapshot.ticker();
      String classCode =
          switch (snapshot) {
            case QuoteSnapshot quote -> quote.quote().classCode();
            case OrderBookEvent book -> book.orderBook().classCode();
            case TradeEvent trade -> trade.trade().classCode();
          };
      String key = ticker + "|" + classCode;
      if (instruments.containsKey(key)) {
        continue;
      }
      Optional<InstrumentEntity> entity =
          instrumentRepository.findByTickerAndClassCode(ticker, classCode);
      entity.ifPresent(value -> instruments.put(key, instrumentMapper.toDomain(value)));
    }
    return Map.copyOf(instruments);
  }

  private static Instant timelineLast(List<MarketDataSnapshot> timeline) {
    Instant last = Instant.EPOCH;
    for (MarketDataSnapshot snapshot : timeline) {
      if (snapshot.timestamp().isAfter(last)) {
        last = snapshot.timestamp();
      }
    }
    return last;
  }
}
