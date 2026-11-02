package ru.moex.papertrader.infra.algoapi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.moex.papertrader.TestInstruments;
import ru.moex.papertrader.core.domain.Instrument;
import ru.moex.papertrader.core.marketdata.Candle;
import ru.moex.papertrader.core.marketdata.CandleSeries;
import ru.moex.papertrader.core.marketdata.OrderBookSnapshot;
import ru.moex.papertrader.core.marketdata.Quote;
import ru.moex.papertrader.core.marketdata.TimeFrame;
import ru.moex.papertrader.core.marketdata.Trade;
import ru.moex.papertrader.core.marketdata.TradeDirection;
import ru.moex.papertrader.core.marketdata.TradingCalendar;
import ru.moex.papertrader.core.marketdata.TradingDay;
import ru.moex.papertrader.core.marketdata.TradingSession;
import ru.moex.papertrader.infra.algoapi.dto.AlgoPackCandleDto;
import ru.moex.papertrader.infra.algoapi.dto.AlgoPackOrderBookDto;
import ru.moex.papertrader.infra.algoapi.dto.AlgoPackQuoteDto;
import ru.moex.papertrader.infra.algoapi.dto.AlgoPackTradeDto;
import ru.moex.papertrader.infra.algoapi.dto.IssSecurityDto;

/** Тесты провайдера на AlgoPack: маппинг DTO → core, кэш справочника, ADV. */
@ExtendWith(MockitoExtension.class)
class AlgoPackMarketDataProviderTest {

  private static final Instant NOW = Instant.parse("2026-11-02T16:00:00Z");

  @Mock private AlgoPackClient algoPack;
  @Mock private IssClient iss;
  @Mock private TradingCalendar calendar;

  private AlgoPackMarketDataProvider provider;

  @BeforeEach
  void setUp() {
    provider =
        new AlgoPackMarketDataProvider(
            algoPack, iss, calendar, AlgoPackProperties.defaults(), () -> NOW);
  }

  @Test
  void instrumentsAreLoadedOnceAndMappedToCore() {
    when(iss.securities("stock", "shares", "TQBR"))
        .thenReturn(
            List.of(
                sber(),
                gazp(),
                new IssSecurityDto(
                    "", "TQBR", "bad", "bad", null, BigDecimal.ONE, BigDecimal.ONE, "RUB")));

    List<Instrument> first = provider.instruments(List.of("SBER", "UNKNOWN", "GAZP"));
    List<Instrument> second = provider.instruments(List.of("SBER"));

    assertThat(first).extracting(Instrument::ticker).containsExactly("SBER", "GAZP");
    assertThat(second).hasSize(1);
    Instrument sber = provider.instrument("SBER", "TQBR").orElseThrow();
    assertThat(sber.type()).isEqualTo("STOCK");
    assertThat(sber.classCode()).isEqualTo("TQBR");
    assertThat(sber.shortName()).isEqualTo("Сбербанк");
    assertThat(sber.displayName()).isEqualTo("Сбербанк ПАО");
    assertThat(sber.isin()).isEqualTo("RU0009029540");
    assertThat(sber.lotSize()).isEqualByComparingTo("10");
    assertThat(sber.minimumStep()).isEqualByComparingTo("0.01");
    assertThat(sber.currency()).isEqualTo("RUB");
    assertThat(provider.instrument("SBER", "SPBFUT")).isEmpty();
    verify(iss, times(1)).securities("stock", "shares", "TQBR");
  }

  @Test
  void quoteIsMappedWithChangeAndRate() {
    when(algoPack.quote("SBER"))
        .thenReturn(
            new AlgoPackQuoteDto(
                "SBER",
                "TQBR",
                new BigDecimal("298.88"),
                new BigDecimal("298.90"),
                new BigDecimal("299.00"),
                new BigDecimal("300.10"),
                new BigDecimal("297.00"),
                new BigDecimal("298.89"),
                new BigDecimal("298.00"),
                null,
                null,
                "T",
                NOW));

    Quote quote = provider.quote(TestInstruments.sber()).orElseThrow();

    assertThat(quote.ticker()).isEqualTo("SBER");
    assertThat(quote.classCode()).isEqualTo("TQBR");
    assertThat(quote.dateTime()).isEqualTo(NOW);
    assertThat(quote.tradingStatus()).isEqualTo(17);
    assertThat(quote.currency()).isEqualTo("RUB");
    assertThat(quote.bid()).isEqualByComparingTo("298.88");
    assertThat(quote.offer()).isEqualByComparingTo("298.90");
    assertThat(quote.last()).isEqualByComparingTo("298.89");
    assertThat(quote.close()).isEqualByComparingTo("298.89");
    assertThat(quote.change()).isEqualByComparingTo("0.89");
    assertThat(quote.changeRate()).isEqualByComparingTo("0.30");
  }

  @Test
  void quoteWithoutPreviousCloseHasZeroChange() {
    when(algoPack.quote("SBER"))
        .thenReturn(
            new AlgoPackQuoteDto(
                "SBER",
                "TQBR",
                new BigDecimal("298.88"),
                null,
                null,
                null,
                null,
                new BigDecimal("298.89"),
                null,
                null,
                null,
                null,
                null));

    Quote quote = provider.quote(TestInstruments.sber()).orElseThrow();

    assertThat(quote.dateTime()).isEqualTo(NOW);
    assertThat(quote.change()).isEqualByComparingTo("0");
    assertThat(quote.changeRate()).isEqualByComparingTo("0");
  }

  @Test
  void quoteFailureReturnsEmpty() {
    when(algoPack.quote("SBER")).thenThrow(new AlgoApiException("AlgoPack недоступен"));

    assertThat(provider.quote(TestInstruments.sber())).isEmpty();
  }

  @Test
  void orderBookIsMappedWithRequestedDepth() {
    when(algoPack.orderBook("SBER", 2))
        .thenReturn(
            new AlgoPackOrderBookDto(
                "SBER",
                "TQBR",
                null,
                List.of(
                    new AlgoPackOrderBookDto.Level(new BigDecimal("298.88"), 1510),
                    new AlgoPackOrderBookDto.Level(new BigDecimal("298.87"), 2697)),
                List.of(new AlgoPackOrderBookDto.Level(new BigDecimal("298.90"), 1200))));

    OrderBookSnapshot book = provider.orderBook(TestInstruments.sber(), 2);

    assertThat(book.depth()).isEqualTo(2);
    assertThat(book.dateTime()).isEqualTo(NOW);
    assertThat(book.bidVolume()).isEqualTo(4207);
    assertThat(book.askVolume()).isEqualTo(1200);
    assertThat(book.bids().get(0).price()).isEqualByComparingTo("298.88");
  }

  @Test
  void candlesUseAlgoPackIntervalCode() {
    Instant from = Instant.parse("2026-10-01T00:00:00Z");
    Instant to = Instant.parse("2026-10-02T00:00:00Z");
    when(algoPack.candles("SBER", "60", from, to))
        .thenReturn(
            List.of(
                new AlgoPackCandleDto(
                    from,
                    from.plusSeconds(3599),
                    new BigDecimal("298.00"),
                    new BigDecimal("299.00"),
                    new BigDecimal("300.00"),
                    new BigDecimal("297.00"),
                    new BigDecimal("100000"),
                    new BigDecimal("1000"))));

    CandleSeries series = provider.candles(TestInstruments.sber(), from, to, TimeFrame.H1);

    assertThat(series.timeFrame()).isEqualTo(TimeFrame.H1);
    assertThat(series.bars()).hasSize(1);
    Candle bar = series.bars().get(0);
    assertThat(bar.time()).isEqualTo(from);
    assertThat(bar.open()).isEqualByComparingTo("298.00");
    assertThat(bar.close()).isEqualByComparingTo("299.00");
    assertThat(bar.volume()).isEqualByComparingTo("1000");
    verify(algoPack).candles("SBER", "60", from, to);
  }

  @Test
  void unsupportedTimeFrameReturnsEmptySeriesWithoutRequest() {
    Instant from = Instant.parse("2026-10-01T00:00:00Z");
    Instant to = Instant.parse("2026-10-02T00:00:00Z");

    CandleSeries series = provider.candles(TestInstruments.sber(), from, to, TimeFrame.M15);

    assertThat(series.bars()).isEmpty();
    verify(algoPack, never()).candles(anyString(), anyString(), any(), any());
  }

  @Test
  void tradesAreMappedAndDefaultDirectionIsBuy() {
    when(algoPack.allTrades(eq("SBER"), any(), any()))
        .thenReturn(
            List.of(
                trade(1, "B", "298.89", 10),
                trade(2, "S", "298.90", 20),
                trade(3, null, "298.91", 30)));

    List<Trade> trades = provider.trades(TestInstruments.sber(), NOW.minusSeconds(60), NOW);

    assertThat(trades).hasSize(3);
    assertThat(trades)
        .extracting(Trade::direction)
        .containsExactly(TradeDirection.BUY, TradeDirection.SELL, TradeDirection.BUY);
    assertThat(trades.get(0).price()).isEqualByComparingTo("298.89");
    assertThat(trades.get(0).quantity()).isEqualTo(10);
    assertThat(trades.get(0).ticker()).isEqualTo("SBER");
  }

  @Test
  void advAveragesDailyVolumeInShares() {
    Instant from = NOW.minus(30, ChronoUnit.DAYS);
    when(algoPack.candles("SBER", "24", from, NOW))
        .thenReturn(List.of(candle("1000"), candle("2000")));

    BigDecimal adv = provider.adv(TestInstruments.sber());

    assertThat(adv).isEqualByComparingTo("15000.00");
    assertThat(adv.scale()).isEqualTo(2);
  }

  @Test
  void advFailureReturnsZero() {
    when(algoPack.candles(eq("SBER"), eq("24"), any(), any()))
        .thenThrow(new AlgoApiException("нет данных"));

    assertThat(provider.adv(TestInstruments.sber())).isEqualByComparingTo("0");
  }

  @Test
  void tradingCalendarIsDelegated() {
    TradingSession session = new TradingSession(1, "Основная", true, NOW.plusSeconds(3600));
    TradingDay day = new TradingDay(java.time.LocalDate.of(2026, 11, 2), true, NOW, NOW);
    when(calendar.tradingSession(NOW)).thenReturn(session);
    when(calendar.tradingDays(NOW, NOW.plusSeconds(60))).thenReturn(List.of(day));

    assertThat(provider.tradingSession(NOW)).isEqualTo(session);
    assertThat(provider.tradingDays(NOW, NOW.plusSeconds(60))).containsExactly(day);
  }

  private static IssSecurityDto sber() {
    return new IssSecurityDto(
        "SBER",
        "TQBR",
        "Сбербанк",
        "Сбербанк ПАО",
        "RU0009029540",
        new BigDecimal("10"),
        new BigDecimal("0.01"),
        "SUR");
  }

  private static IssSecurityDto gazp() {
    return new IssSecurityDto(
        "GAZP",
        "TQBR",
        "Газпром",
        "Газпром ПАО",
        "RU0007661625",
        new BigDecimal("10"),
        new BigDecimal("0.01"),
        "SUR");
  }

  private static AlgoPackTradeDto trade(long number, String buySell, String price, long quantity) {
    return new AlgoPackTradeDto(
        number,
        NOW.minusSeconds(10),
        "TQBR",
        "SBER",
        new BigDecimal(price),
        quantity,
        new BigDecimal(price).multiply(BigDecimal.valueOf(quantity)),
        buySell,
        "0");
  }

  private static AlgoPackCandleDto candle(String volume) {
    return new AlgoPackCandleDto(
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
