package ru.moex.papertrader.infra.services;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import ru.moex.papertrader.TestInstruments;
import ru.moex.papertrader.core.domain.Instrument;
import ru.moex.papertrader.core.marketdata.CandleSeries;
import ru.moex.papertrader.core.marketdata.Clock;
import ru.moex.papertrader.core.marketdata.OrderBookSnapshot;
import ru.moex.papertrader.core.marketdata.Quote;
import ru.moex.papertrader.core.marketdata.TimeFrame;
import ru.moex.papertrader.core.marketdata.TradingSession;
import ru.moex.papertrader.testutil.FixedClock;

class StubMarketDataProviderTest {

  private static final Instant NOW = Instant.parse("2026-11-01T10:00:00Z");
  private static final Clock CLOCK = FixedClock.at(NOW);

  private final StubMarketDataProvider provider =
      new StubMarketDataProvider(new StubMarketDataProperties(List.of(sber(), gazp())), CLOCK);

  @Test
  void findsInstrumentByTickerAndClass() {
    assertThat(provider.instrument("SBER", "TQBR"))
        .get()
        .extracting(Instrument::ticker)
        .isEqualTo("SBER");
    assertThat(provider.instrument("SBER", "SPBFUT")).isEmpty();
    assertThat(provider.instrument("NOPE", "TQBR")).isEmpty();
  }

  @Test
  void instrumentsPreserveRequestOrderAndSkipUnknown() {
    List<Instrument> instruments = provider.instruments(List.of("GAZP", "NOPE", "SBER"));

    assertThat(instruments).extracting(Instrument::ticker).containsExactly("GAZP", "SBER");
  }

  @Test
  void quoteIsBuiltAroundBasePrice() {
    Quote quote = provider.quote(TestInstruments.sber()).orElseThrow();

    assertThat(quote.ticker()).isEqualTo("SBER");
    assertThat(quote.bid()).isEqualByComparingTo("285.49");
    assertThat(quote.offer()).isEqualByComparingTo("285.51");
    assertThat(quote.last()).isEqualByComparingTo("285.50");
    assertThat(quote.tradingStatus()).isEqualTo(17);
    assertThat(quote.dateTime()).isEqualTo(NOW);
  }

  @Test
  void orderBookHasRequestedDepthAndVolumes() {
    OrderBookSnapshot book = provider.orderBook(TestInstruments.sber(), 3);

    assertThat(book.bids()).hasSize(3);
    assertThat(book.asks()).hasSize(3);
    assertThat(book.bidVolume()).isEqualTo(600);
    assertThat(book.askVolume()).isEqualTo(600);
    assertThat(book.bids().get(0).price()).isEqualByComparingTo("285.49");
    assertThat(book.asks().get(2).price()).isEqualByComparingTo("285.53");
  }

  @Test
  void candlesCoverPeriodDeterministically() {
    Instant from = Instant.parse("2025-11-14T07:00:00Z");
    Instant to = Instant.parse("2025-11-14T09:00:00Z");

    CandleSeries series = provider.candles(TestInstruments.sber(), from, to, TimeFrame.H1);

    assertThat(series.bars()).hasSize(3);
    assertThat(series.bars().get(0).time()).isEqualTo(from);
    assertThat(series.bars().get(2).time()).isEqualTo(to);
    assertThat(series.bars().get(0).volume()).isEqualByComparingTo("10000");
    assertThat(series.bars().get(2).volume()).isEqualByComparingTo("10020");
  }

  @Test
  void tradingSessionIsOpen() {
    TradingSession session = provider.tradingSession(NOW);

    assertThat(session.open()).isTrue();
    assertThat(session.name()).isEqualTo("Основная сессия");
    assertThat(session.nextSessionAt()).isEqualTo(NOW.plusSeconds(3600));
  }

  private static StubMarketDataProperties.InstrumentDefinition sber() {
    return new StubMarketDataProperties.InstrumentDefinition(
        "SBER",
        "TQBR",
        "STOCK",
        "Сбербанк",
        "Сбербанк",
        "RU0009029540",
        new BigDecimal("10"),
        new BigDecimal("0.01"),
        "RUB",
        new BigDecimal("285.50"));
  }

  private static StubMarketDataProperties.InstrumentDefinition gazp() {
    return new StubMarketDataProperties.InstrumentDefinition(
        "GAZP",
        "TQBR",
        "STOCK",
        "Газпром",
        "Газпром",
        "RU0007661625",
        new BigDecimal("10"),
        new BigDecimal("0.01"),
        "RUB",
        new BigDecimal("165.20"));
  }
}
