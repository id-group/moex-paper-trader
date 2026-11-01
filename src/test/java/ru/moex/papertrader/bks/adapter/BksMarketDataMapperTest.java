package ru.moex.papertrader.bks.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import ru.moex.papertrader.TestInstruments;
import ru.moex.papertrader.TestJson;
import ru.moex.papertrader.bks.contract.Instrument;
import ru.moex.papertrader.core.marketdata.Candle;
import ru.moex.papertrader.core.marketdata.CandleSeries;
import ru.moex.papertrader.core.marketdata.OrderBookSnapshot;
import ru.moex.papertrader.core.marketdata.Quote;
import ru.moex.papertrader.core.marketdata.TimeFrame;
import ru.moex.papertrader.core.marketdata.TradingSession;

class BksMarketDataMapperTest {

  private static final Instant NOW = Instant.parse("2026-11-01T10:00:00Z");

  private final BksMarketDataMapper mapper = new BksMarketDataMapper(TestJson.MAPPER);

  @Test
  void mapsQuoteToMarketDataResponse() {
    Quote quote =
        new Quote(
            "SBER",
            "TQBR",
            NOW,
            17,
            "RUB",
            new BigDecimal("285.49"),
            new BigDecimal("285.51"),
            new BigDecimal("285.50"),
            new BigDecimal("284.00"),
            new BigDecimal("286.00"),
            new BigDecimal("283.00"),
            new BigDecimal("285.50"),
            BigDecimal.ZERO,
            BigDecimal.ZERO);

    var response = mapper.toQuotes(List.of(quote)).records().get(0);

    assertThat(response.ticker()).isEqualTo("SBER");
    assertThat(response.classCode()).isEqualTo("TQBR");
    assertThat(response.securityTradingStatus()).isEqualTo(17);
    assertThat(response.bid()).isEqualByComparingTo("285.49");
    assertThat(response.offer()).isEqualByComparingTo("285.51");
    assertThat(response.last()).isEqualByComparingTo("285.50");
    assertThat(response.theoreticalPrice()).isNull();
    assertThat(response.dateTime()).isNotNull();
  }

  @Test
  void mapsOrderBookWithVolumes() {
    OrderBookSnapshot book =
        new OrderBookSnapshot(
            "SBER",
            "TQBR",
            2,
            NOW,
            List.of(
                new OrderBookSnapshot.Level(new BigDecimal("285.49"), 100),
                new OrderBookSnapshot.Level(new BigDecimal("285.48"), 200)),
            List.of(
                new OrderBookSnapshot.Level(new BigDecimal("285.51"), 300),
                new OrderBookSnapshot.Level(new BigDecimal("285.52"), 400)));

    var response = mapper.toOrderBook(book);

    assertThat(response.depth()).isEqualTo(2);
    assertThat(response.bidVolume()).isEqualTo(300);
    assertThat(response.askVolume()).isEqualTo(700);
    assertThat(response.bids()).hasSize(2);
    assertThat(response.bids().get(1).quantity()).isEqualTo(200L);
    assertThat(response.asks().get(0).price()).isEqualByComparingTo("285.51");
  }

  @Test
  void mapsCandlesWithTimeFrameName() {
    CandleSeries series =
        new CandleSeries(
            "SBER",
            "TQBR",
            NOW,
            NOW.plusSeconds(3600),
            TimeFrame.H1,
            List.of(
                new Candle(
                    NOW,
                    new BigDecimal("285.50"),
                    new BigDecimal("285.60"),
                    new BigDecimal("285.70"),
                    new BigDecimal("285.40"),
                    new BigDecimal("10000")),
                new Candle(
                    NOW.plusSeconds(3600),
                    new BigDecimal("285.60"),
                    new BigDecimal("285.50"),
                    new BigDecimal("285.80"),
                    new BigDecimal("285.30"),
                    new BigDecimal("10010"))));

    var response = mapper.toCandles(series);

    assertThat(response.timeFrame()).isEqualTo("H1");
    assertThat(response.bars()).hasSize(2);
    assertThat(response.bars().get(1).time()).isNotNull();
    assertThat(response.bars().get(0).open()).isEqualByComparingTo("285.50");
  }

  @Test
  void mapsDirectoryInstrument() {
    var instrument = mapper.toInstrument(TestInstruments.sber());

    assertThat(instrument.ticker()).isEqualTo("SBER");
    assertThat(instrument.type()).isEqualTo("STOCK");
    assertThat(instrument.instrumentType()).isEqualTo("STOCK");
    assertThat(instrument.primaryBoard()).isEqualTo("TQBR");
    assertThat(instrument.boards()).hasSize(1);
    assertThat(instrument.boards().get(0).exchange()).isEqualTo("MOEX");
    assertThat(instrument.lotSize()).isEqualByComparingTo("10");
    assertThat(instrument.minimumStep()).isEqualByComparingTo("0.01");
    assertThat(instrument.scale()).isEqualTo(2);
    assertThat(instrument.isCanShort()).isTrue();
    assertThat(instrument.couponRate()).isNull();
  }

  @Test
  void directoryInstrumentRoundTripsThroughContractJson() {
    var instrument = mapper.toInstrument(TestInstruments.sber());

    var restored =
        TestJson.MAPPER.readValue(TestJson.MAPPER.writeValueAsString(instrument), Instrument.class);

    assertThat(restored).isEqualTo(instrument);
  }

  @Test
  void mapsTradingSessionStatus() {
    var response = mapper.toTradingStatus(new TradingSession(1, "Основная сессия", true, NOW));

    assertThat(response.tradingSessionTypeId()).isEqualTo(1);
    assertThat(response.tradingSessionStatus()).isEqualTo("OPEN");
    assertThat(response.nextSessionDate()).isNotNull();
  }
}
