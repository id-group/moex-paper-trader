package ru.moex.papertrader.infra.algoapi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.moex.papertrader.TestInstruments;
import ru.moex.papertrader.core.marketdata.CandleSeries;
import ru.moex.papertrader.core.marketdata.MarketDataProvider;
import ru.moex.papertrader.core.marketdata.OrderBookSnapshot;
import ru.moex.papertrader.core.marketdata.Quote;
import ru.moex.papertrader.core.marketdata.TimeFrame;
import ru.moex.papertrader.core.marketdata.Trade;
import ru.moex.papertrader.core.marketdata.TradeDirection;
import ru.moex.papertrader.core.marketdata.TradingDay;
import ru.moex.papertrader.core.marketdata.TradingSession;

/** Тесты провайдера с резервированием AlgoPack → MOEX ISS. */
@ExtendWith(MockitoExtension.class)
class FallbackMarketDataProviderTest {

  private static final Instant NOW = Instant.parse("2026-11-02T16:00:00Z");
  private static final Quote QUOTE =
      new Quote(
          "SBER",
          "TQBR",
          NOW,
          17,
          "RUB",
          new BigDecimal("298.88"),
          new BigDecimal("298.90"),
          null,
          new BigDecimal("298.89"),
          null,
          null,
          new BigDecimal("298.89"),
          BigDecimal.ZERO,
          BigDecimal.ZERO);

  @Mock private MarketDataProvider primary;
  @Mock private MarketDataProvider fallback;

  private FallbackMarketDataProvider provider;

  @BeforeEach
  void setUp() {
    provider = new FallbackMarketDataProvider(primary, fallback);
  }

  @Test
  void quoteFallsBackWhenPrimaryReturnsEmpty() {
    when(primary.quote(TestInstruments.sber())).thenReturn(Optional.empty());
    when(fallback.quote(TestInstruments.sber())).thenReturn(Optional.of(QUOTE));

    assertThat(provider.quote(TestInstruments.sber())).contains(QUOTE);
  }

  @Test
  void quoteFallsBackWhenPrimaryThrows() {
    when(primary.quote(TestInstruments.sber())).thenThrow(new AlgoApiException("down"));
    when(fallback.quote(TestInstruments.sber())).thenReturn(Optional.of(QUOTE));

    assertThat(provider.quote(TestInstruments.sber())).contains(QUOTE);
  }

  @Test
  void quoteUsesPrimaryWhenPresent() {
    when(primary.quote(TestInstruments.sber())).thenReturn(Optional.of(QUOTE));

    assertThat(provider.quote(TestInstruments.sber())).contains(QUOTE);
    verifyNoInteractions(fallback);
  }

  @Test
  void instrumentsFallBackWhenPrimaryIsEmptyOrFails() {
    when(primary.instruments(List.of("SBER")))
        .thenReturn(List.of())
        .thenThrow(new AlgoApiException("down"));
    when(fallback.instruments(List.of("SBER"))).thenReturn(List.of(TestInstruments.sber()));

    assertThat(provider.instruments(List.of("SBER"))).containsExactly(TestInstruments.sber());
    assertThat(provider.instruments(List.of("SBER"))).containsExactly(TestInstruments.sber());
  }

  @Test
  void instrumentsUsePrimaryWhenNotEmpty() {
    when(primary.instruments(List.of("SBER"))).thenReturn(List.of(TestInstruments.sber()));

    assertThat(provider.instruments(List.of("SBER"))).containsExactly(TestInstruments.sber());
    verifyNoInteractions(fallback);
  }

  @Test
  void instrumentFallsBackWhenPrimaryIsEmpty() {
    when(primary.instrument("SBER", "TQBR")).thenReturn(Optional.empty());
    when(fallback.instrument("SBER", "TQBR")).thenReturn(Optional.of(TestInstruments.sber()));

    assertThat(provider.instrument("SBER", "TQBR")).isPresent();
  }

  @Test
  void instrumentUsesPrimaryWhenPresent() {
    when(primary.instrument("SBER", "TQBR")).thenReturn(Optional.of(TestInstruments.sber()));

    assertThat(provider.instrument("SBER", "TQBR")).isPresent();
    verifyNoInteractions(fallback);
  }

  @Test
  void orderBookFallsBackOnFailure() {
    OrderBookSnapshot book = new OrderBookSnapshot("SBER", "TQBR", 1, NOW, List.of(), List.of());
    when(primary.orderBook(TestInstruments.sber(), 1)).thenThrow(new AlgoApiException("down"));
    when(fallback.orderBook(TestInstruments.sber(), 1)).thenReturn(book);

    assertThat(provider.orderBook(TestInstruments.sber(), 1)).isEqualTo(book);
  }

  @Test
  void candlesFallBackOnFailure() {
    CandleSeries series = new CandleSeries("SBER", "TQBR", NOW, NOW, TimeFrame.D, List.of());
    when(primary.candles(TestInstruments.sber(), NOW, NOW, TimeFrame.D))
        .thenThrow(new AlgoApiException("down"));
    when(fallback.candles(TestInstruments.sber(), NOW, NOW, TimeFrame.D)).thenReturn(series);

    assertThat(provider.candles(TestInstruments.sber(), NOW, NOW, TimeFrame.D)).isEqualTo(series);
  }

  @Test
  void tradesFallBackOnFailure() {
    List<Trade> trades =
        List.of(new Trade(NOW, "SBER", "TQBR", BigDecimal.TEN, 1, TradeDirection.BUY));
    when(primary.trades(TestInstruments.sber(), NOW, NOW)).thenThrow(new AlgoApiException("down"));
    when(fallback.trades(TestInstruments.sber(), NOW, NOW)).thenReturn(trades);

    assertThat(provider.trades(TestInstruments.sber(), NOW, NOW)).isEqualTo(trades);
  }

  @Test
  void advFallsBackOnFailure() {
    when(primary.adv(TestInstruments.sber())).thenThrow(new AlgoApiException("down"));
    when(fallback.adv(TestInstruments.sber())).thenReturn(new BigDecimal("12345.67"));

    assertThat(provider.adv(TestInstruments.sber())).isEqualByComparingTo("12345.67");
  }

  @Test
  void priceMethodsUsePrimaryWithoutFallback() {
    OrderBookSnapshot book = new OrderBookSnapshot("SBER", "TQBR", 1, NOW, List.of(), List.of());
    when(primary.orderBook(TestInstruments.sber(), 1)).thenReturn(book);
    when(primary.adv(TestInstruments.sber())).thenReturn(BigDecimal.ONE);

    assertThat(provider.orderBook(TestInstruments.sber(), 1)).isEqualTo(book);
    assertThat(provider.adv(TestInstruments.sber())).isEqualByComparingTo("1");
    verify(fallback, never()).orderBook(any(), anyInt());
    verify(fallback, never()).adv(any());
  }

  @Test
  void tradingCalendarAlwaysComesFromPrimary() {
    TradingSession session = new TradingSession(1, "Основная", true, NOW);
    TradingDay day = new TradingDay(LocalDate.of(2026, 11, 2), true, NOW, NOW);
    when(primary.tradingSession(NOW)).thenReturn(session);
    when(primary.tradingDays(NOW, NOW.plusSeconds(60))).thenReturn(List.of(day));

    assertThat(provider.tradingSession(NOW)).isEqualTo(session);
    assertThat(provider.tradingDays(NOW, NOW.plusSeconds(60))).containsExactly(day);
    verifyNoInteractions(fallback);
  }
}
