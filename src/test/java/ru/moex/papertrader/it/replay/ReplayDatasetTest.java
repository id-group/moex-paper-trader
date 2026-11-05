package ru.moex.papertrader.it.replay;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import ru.moex.papertrader.core.domain.Side;
import ru.moex.papertrader.core.marketdata.MarketDataSnapshot;
import ru.moex.papertrader.core.marketdata.OrderBookEvent;
import ru.moex.papertrader.core.marketdata.OrderBookSnapshot;
import ru.moex.papertrader.core.marketdata.QuoteSnapshot;
import ru.moex.papertrader.core.marketdata.SnapshotKind;
import ru.moex.papertrader.core.marketdata.TradeEvent;
import ru.moex.papertrader.core.marketdata.TradingDay;

/** Свойства детерминированного REPLAY-набора и эталонного бэктеста этапа 7. */
class ReplayDatasetTest {

  private static final BigDecimal ADV_THRESHOLD_SHARE = new BigDecimal("0.05");

  @Test
  void datasetIsDeterministicAndWellFormed() {
    ReplayDataset first = ReplayDataset.standard();
    ReplayDataset second = ReplayDataset.standard();

    assertThat(first.snapshots()).isEqualTo(second.snapshots());
    assertThat(first.tradingDates()).isEqualTo(second.tradingDates());
    assertThat(first.start()).isEqualTo(second.start());
    assertThat(first.end()).isEqualTo(second.end());
  }

  @Test
  void tradingCalendarCoversFullWindowIncludingWeekends() {
    ReplayDataset dataset = ReplayDataset.standard();
    List<LocalDate> dates = dataset.tradingDates();

    assertThat(dates).hasSize(ReplayDataset.TRADING_DAYS);
    assertThat(dates)
        .allSatisfy(
            date -> assertThat(date.getDayOfWeek()).isNotIn(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY));

    List<TradingDay> calendar = dataset.tradingDays();
    assertThat(calendar).isNotEmpty();
    assertThat(calendar.get(0).date()).isEqualTo(dates.get(0).minusDays(1));
    assertThat(calendar.get(calendar.size() - 1).date())
        .isEqualTo(dates.get(dates.size() - 1).plusDays(1));
    assertThat(calendar)
        .allSatisfy(
            day -> {
              assertThat(day.trading()).isEqualTo(dates.contains(day.date()));
              if (day.trading()) {
                assertThat(day.openUtc()).isNotNull();
                assertThat(day.closeUtc()).isAfter(day.openUtc());
              } else {
                assertThat(day.openUtc()).isNull();
                assertThat(day.closeUtc()).isNull();
              }
            });
  }

  @Test
  void everyBarProducesQuoteBookAndTrades() {
    ReplayDataset dataset = ReplayDataset.standard();

    for (String ticker : List.of(ReplayDataset.SBER, ReplayDataset.GAZP, ReplayDataset.SI)) {
      assertThat(dataset.bars(ticker))
          .hasSize(ReplayDataset.TRADING_DAYS * ReplayDataset.BARS_PER_DAY);
    }

    Map<String, Integer> quotes = new HashMap<>();
    Map<String, Long> dailyVolume = new HashMap<>();
    for (MarketDataSnapshot snapshot : dataset.snapshots()) {
      if (snapshot.kind() == SnapshotKind.QUOTE) {
        QuoteSnapshot quote = (QuoteSnapshot) snapshot;
        assertThat(quote.quote().last()).isPositive();
        assertThat(quote.quote().bid()).isLessThan(quote.quote().offer());
        quotes.merge(key(snapshot), 1, Integer::sum);
      } else if (snapshot.kind() == SnapshotKind.ORDER_BOOK) {
        OrderBookSnapshot book = ((OrderBookEvent) snapshot).orderBook();
        assertThat(book.bids()).hasSize(10);
        assertThat(book.asks()).hasSize(10);
        assertThat(book.bids().get(0).price()).isLessThan(book.asks().get(0).price());
      } else {
        TradeEvent trade = (TradeEvent) snapshot;
        assertThat(trade.trade().quantity()).isPositive();
        dailyVolume.merge(key(snapshot), trade.trade().quantity(), Long::sum);
      }
    }

    int expectedQuotesPerInstrumentDay = ReplayDataset.BARS_PER_DAY;
    for (LocalDate date : dataset.tradingDates()) {
      for (String ticker : List.of(ReplayDataset.SBER, ReplayDataset.GAZP, ReplayDataset.SI)) {
        assertThat(quotes.get(ticker + "|" + date)).isEqualTo(expectedQuotesPerInstrumentDay);
        assertThat(dailyVolume.get(ticker + "|" + date))
            .isEqualTo(2 * ReplayDataset.TRADE_QUANTITY * ReplayDataset.BARS_PER_DAY);
      }
    }
  }

  @Test
  void strategySignalsAreLongOnlyAndSizedBelowAdvThreshold() {
    ReplayDataset dataset = ReplayDataset.standard();
    List<SmaCrossoverStrategy.Signal> signals = SmaCrossoverStrategy.signals(dataset);

    assertThat(signals).isNotEmpty();
    assertThat(signals)
        .isSortedAccordingTo(
            Comparator.comparing(SmaCrossoverStrategy.Signal::at)
                .thenComparing(SmaCrossoverStrategy.Signal::ticker));

    BigDecimal dailyAdv =
        BigDecimal.valueOf(2 * ReplayDataset.TRADE_QUANTITY * ReplayDataset.BARS_PER_DAY);
    BigDecimal limit = dailyAdv.multiply(ADV_THRESHOLD_SHARE);
    assertThat(signals)
        .allSatisfy(signal -> assertThat(BigDecimal.valueOf(signal.quantity())).isLessThan(limit));

    Map<String, Long> positions = new HashMap<>();
    for (SmaCrossoverStrategy.Signal signal : signals) {
      long held = positions.getOrDefault(signal.ticker(), 0L);
      if (signal.side() == Side.BUY) {
        assertThat(held).as("повторная покупка при открытой позиции: %s", signal).isZero();
        positions.put(signal.ticker(), signal.quantity());
      } else {
        assertThat(held).as("продажа без позиции: %s", signal).isEqualTo(signal.quantity());
        positions.put(signal.ticker(), 0L);
      }
    }
    assertThat(signals.stream().filter(signal -> signal.side() == Side.SELL).count())
        .as("закрытых сделок должно быть достаточно для win rate; сигналы: %s", signals)
        .isGreaterThanOrEqualTo(3);
    assertThat(signals)
        .anySatisfy(
            signal -> {
              assertThat(signal.ticker()).isEqualTo(ReplayDataset.SI);
              assertThat(signal.at().atZone(ZoneOffset.UTC).toLocalDate())
                  .isEqualTo(dataset.tradingDates().get(10));
            });
  }

  @Test
  void idealBacktestProducesComparableMetrics() {
    ReplayDataset dataset = ReplayDataset.standard();
    IdealBacktest.IdealResult result = IdealBacktest.run(dataset);

    assertThat(result.nav()).hasSize(ReplayDataset.TRADING_DAYS);
    assertThat(result.nav()).allSatisfy(point -> assertThat(point.nav()).isPositive());
    assertThat(result.closedTrades()).hasSizeGreaterThanOrEqualTo(3);

    assertThat(result.metrics()).containsKeys("cagr", "sharpe", "maxDrawdown", "winRate");
    assertThat(result.metrics().get("maxDrawdown")).isPositive();
    assertThat(result.metrics().get("winRate")).isBetween(BigDecimal.ZERO, BigDecimal.ONE);
    assertThat(result.metrics().get("sharpe")).isNotNull();
    assertThat(result.metrics().get("cagr"))
        .as("эталонный CAGR")
        .isGreaterThan(new BigDecimal("0.05"));
    assertThat(result.metrics().get("maxDrawdown"))
        .as("просадка эталона должна быть заметной для устойчивой сверки")
        .isGreaterThan(new BigDecimal("0.01"));
  }

  private static String key(MarketDataSnapshot snapshot) {
    return snapshot.ticker() + "|" + snapshot.timestamp().atZone(ZoneOffset.UTC).toLocalDate();
  }
}
