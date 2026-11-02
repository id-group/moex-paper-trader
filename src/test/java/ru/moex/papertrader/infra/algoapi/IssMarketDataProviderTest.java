package ru.moex.papertrader.infra.algoapi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.moex.papertrader.TestInstruments;
import ru.moex.papertrader.core.domain.Instrument;
import ru.moex.papertrader.core.marketdata.CandleSeries;
import ru.moex.papertrader.core.marketdata.OrderBookSnapshot;
import ru.moex.papertrader.core.marketdata.Quote;
import ru.moex.papertrader.core.marketdata.TimeFrame;
import ru.moex.papertrader.core.marketdata.Trade;
import ru.moex.papertrader.core.marketdata.TradeDirection;
import ru.moex.papertrader.core.marketdata.TradingCalendar;
import ru.moex.papertrader.core.marketdata.TradingDay;
import ru.moex.papertrader.core.marketdata.TradingSession;
import ru.moex.papertrader.infra.algoapi.dto.IssCandleDto;
import ru.moex.papertrader.infra.algoapi.dto.IssOrderBookDto;
import ru.moex.papertrader.infra.algoapi.dto.IssSecurityDto;
import ru.moex.papertrader.infra.algoapi.dto.IssTradeDto;

/** Тесты провайдера на MOEX ISS: маппинг стакана/свечей/сделок, ADV, календарь. */
@ExtendWith(MockitoExtension.class)
class IssMarketDataProviderTest {

  private static final Instant NOW = Instant.parse("2026-11-02T16:00:00Z");

  @Mock private IssClient iss;
  @Mock private TradingCalendar calendar;

  private IssMarketDataProvider provider;

  @BeforeEach
  void setUp() {
    provider = new IssMarketDataProvider(iss, calendar, IssProperties.defaults(), () -> NOW);
  }

  @Test
  void instrumentsAreLoadedOnceAndMappedToCore() {
    when(iss.securities("stock", "shares", "TQBR"))
        .thenReturn(
            List.of(
                new IssSecurityDto(
                    "SBER",
                    "TQBR",
                    "Сбербанк",
                    "Сбербанк ПАО",
                    "RU0009029540",
                    new BigDecimal("10"),
                    new BigDecimal("0.01"),
                    "SUR")));

    assertThat(provider.instruments(List.of("SBER")))
        .extracting(Instrument::ticker)
        .containsExactly("SBER");
    assertThat(provider.instrument("SBER", "TQBR")).isPresent();
    verify(iss, times(1)).securities("stock", "shares", "TQBR");
  }

  @Test
  void quoteUsesBestBidAsLastWhenNoLastField() {
    when(iss.orderBook("stock", "shares", "TQBR", "SBER"))
        .thenReturn(
            new IssOrderBookDto(
                "SBER",
                "TQBR",
                null,
                null,
                List.of(new IssOrderBookDto.Level(new BigDecimal("298.88"), 1510)),
                List.of(new IssOrderBookDto.Level(new BigDecimal("298.90"), 1200))));

    Quote quote = provider.quote(TestInstruments.sber()).orElseThrow();

    assertThat(quote.last()).isEqualByComparingTo("298.88");
    assertThat(quote.close()).isEqualByComparingTo("298.88");
    assertThat(quote.bid()).isEqualByComparingTo("298.88");
    assertThat(quote.offer()).isEqualByComparingTo("298.90");
    assertThat(quote.tradingStatus()).isEqualTo(17);
    assertThat(quote.currency()).isEqualTo("RUB");
    assertThat(quote.dateTime()).isEqualTo(NOW);
  }

  @Test
  void quotePrefersExplicitLastPrice() {
    when(iss.orderBook("stock", "shares", "TQBR", "SBER"))
        .thenReturn(
            new IssOrderBookDto(
                "SBER",
                "TQBR",
                Instant.parse("2026-11-02T15:59:00Z"),
                new BigDecimal("299.10"),
                List.of(new IssOrderBookDto.Level(new BigDecimal("298.88"), 1510)),
                List.of(new IssOrderBookDto.Level(new BigDecimal("298.90"), 1200))));

    Quote quote = provider.quote(TestInstruments.sber()).orElseThrow();

    assertThat(quote.last()).isEqualByComparingTo("299.10");
    assertThat(quote.dateTime()).isEqualTo(Instant.parse("2026-11-02T15:59:00Z"));
  }

  @Test
  void quoteWithoutPricesIsEmpty() {
    when(iss.orderBook("stock", "shares", "TQBR", "SBER"))
        .thenReturn(new IssOrderBookDto("SBER", "TQBR", null, null, List.of(), List.of()));

    assertThat(provider.quote(TestInstruments.sber())).isEmpty();
  }

  @Test
  void quoteFailureReturnsEmpty() {
    when(iss.orderBook("stock", "shares", "TQBR", "SBER"))
        .thenThrow(new AlgoApiException("ISS недоступен"));

    assertThat(provider.quote(TestInstruments.sber())).isEmpty();
  }

  @Test
  void orderBookIsLimitedToRequestedDepth() {
    when(iss.orderBook("stock", "shares", "TQBR", "SBER"))
        .thenReturn(
            new IssOrderBookDto(
                "SBER",
                "TQBR",
                null,
                null,
                List.of(
                    new IssOrderBookDto.Level(new BigDecimal("298.88"), 10),
                    new IssOrderBookDto.Level(new BigDecimal("298.87"), 20),
                    new IssOrderBookDto.Level(new BigDecimal("298.86"), 30)),
                List.of(
                    new IssOrderBookDto.Level(new BigDecimal("298.90"), 40),
                    new IssOrderBookDto.Level(new BigDecimal("298.91"), 50))));

    OrderBookSnapshot book = provider.orderBook(TestInstruments.sber(), 2);

    assertThat(book.bids()).hasSize(2);
    assertThat(book.asks()).hasSize(2);
    assertThat(book.dateTime()).isEqualTo(NOW);
    assertThat(book.bidVolume()).isEqualTo(30);
  }

  @Test
  void candlesUseIssIntervalCode() {
    Instant from = Instant.parse("2026-10-01T00:00:00Z");
    Instant to = Instant.parse("2026-10-02T00:00:00Z");
    when(iss.candles("stock", "shares", "TQBR", "SBER", 24, from, to))
        .thenReturn(
            List.of(
                new IssCandleDto(
                    from,
                    to,
                    new BigDecimal("298.00"),
                    new BigDecimal("299.00"),
                    new BigDecimal("300.00"),
                    new BigDecimal("297.00"),
                    new BigDecimal("100000"),
                    new BigDecimal("1000"))));

    CandleSeries series = provider.candles(TestInstruments.sber(), from, to, TimeFrame.D);

    assertThat(series.bars()).hasSize(1);
    assertThat(series.bars().get(0).time()).isEqualTo(from);
    assertThat(series.bars().get(0).volume()).isEqualByComparingTo("1000");
    verify(iss).candles("stock", "shares", "TQBR", "SBER", 24, from, to);
  }

  @Test
  void unsupportedTimeFrameReturnsEmptySeriesWithoutRequest() {
    Instant from = Instant.parse("2026-10-01T00:00:00Z");
    Instant to = Instant.parse("2026-10-02T00:00:00Z");

    CandleSeries series = provider.candles(TestInstruments.sber(), from, to, TimeFrame.M5);

    assertThat(series.bars()).isEmpty();
    verify(iss, never())
        .candles(anyString(), anyString(), anyString(), anyString(), anyInt(), any(), any());
  }

  @Test
  void tradesAreMappedWithDirection() {
    when(iss.trades(eq("stock"), eq("shares"), eq("TQBR"), eq("SBER"), any(), any()))
        .thenReturn(
            List.of(
                new IssTradeDto(
                    1,
                    NOW.minusSeconds(20),
                    "SBER",
                    new BigDecimal("298.89"),
                    10,
                    new BigDecimal("2988.9"),
                    "TQBR",
                    "S"),
                new IssTradeDto(
                    2,
                    NOW.minusSeconds(10),
                    "SBER",
                    new BigDecimal("298.90"),
                    5,
                    new BigDecimal("1494.5"),
                    "TQBR",
                    "B")));

    List<Trade> trades = provider.trades(TestInstruments.sber(), NOW.minusSeconds(60), NOW);

    assertThat(trades).hasSize(2);
    assertThat(trades)
        .extracting(Trade::direction)
        .containsExactly(TradeDirection.SELL, TradeDirection.BUY);
    assertThat(trades.get(0).quantity()).isEqualTo(10);
  }

  @Test
  void advAveragesDailyVolumeInShares() {
    Instant from = NOW.minus(30, ChronoUnit.DAYS);
    when(iss.candles("stock", "shares", "TQBR", "SBER", 24, from, NOW))
        .thenReturn(List.of(candle("1000"), candle("3000")));

    BigDecimal adv = provider.adv(TestInstruments.sber());

    assertThat(adv).isEqualByComparingTo("20000.00");
    assertThat(adv.scale()).isEqualTo(2);
  }

  @Test
  void advFailureReturnsZero() {
    when(iss.candles(eq("stock"), eq("shares"), eq("TQBR"), eq("SBER"), eq(24), any(), any()))
        .thenThrow(new AlgoApiException("нет данных"));

    assertThat(provider.adv(TestInstruments.sber())).isEqualByComparingTo("0");
  }

  @Test
  void tradingCalendarIsDelegated() {
    TradingSession session = new TradingSession(1, "Основная", false, NOW);
    TradingDay day = new TradingDay(LocalDate.of(2026, 11, 2), true, NOW, NOW.plusSeconds(3600));
    when(calendar.tradingSession(NOW)).thenReturn(session);
    when(calendar.tradingDays(NOW, NOW.plusSeconds(60))).thenReturn(List.of(day));

    assertThat(provider.tradingSession(NOW)).isEqualTo(session);
    assertThat(provider.tradingDays(NOW, NOW.plusSeconds(60))).containsExactly(day);
  }

  private static IssCandleDto candle(String volume) {
    return new IssCandleDto(
        NOW.minus(1, ChronoUnit.DAYS),
        NOW,
        new BigDecimal("298.00"),
        new BigDecimal("299.00"),
        new BigDecimal("300.00"),
        new BigDecimal("297.00"),
        new BigDecimal("100000"),
        new BigDecimal(volume));
  }
}
