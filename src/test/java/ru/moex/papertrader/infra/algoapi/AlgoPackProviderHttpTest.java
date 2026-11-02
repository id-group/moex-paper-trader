package ru.moex.papertrader.infra.algoapi;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;
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

/**
 * Сквозные тесты провайдера AlgoPack поверх настоящих HTTP-клиентов и фикстур API: проверяют связку
 * клиент → DTO → core и реальное число HTTP-запросов (кэш справочника).
 */
class AlgoPackProviderHttpTest {

  private static final Instant NOW = Instant.parse("2026-11-02T16:00:00Z");

  private AlgoApiTestServer server;
  private AlgoPackMarketDataProvider provider;

  @BeforeEach
  void setUp() throws Exception {
    server = AlgoApiTestServer.start();
    server.route("/securities/SBER.json", ok(AlgoApiFixtures.read("algoapi/quote.json")));
    server.route(
        "/securities/SBER/orderbook.json", ok(AlgoApiFixtures.read("algoapi/orderbook.json")));
    server.route("/securities/SBER/candles.json", ok(AlgoApiFixtures.read("algoapi/candles.json")));
    server.route("/securities/SBER/trades.json", ok(AlgoApiFixtures.read("algoapi/trades.json")));
    server.route("/securities.json", ok(AlgoApiFixtures.read("iss/securities.json")));

    AlgoPackProperties algoPackProperties =
        new AlgoPackProperties(
            server.baseUrl(), "token", 2_000, 2_000, 0, 0, "stock", "shares", "TQBR");
    IssProperties issProperties =
        new IssProperties(server.baseUrl(), 2_000, 2_000, 0, 0, "stock", "shares", "TQBR");
    provider =
        new AlgoPackMarketDataProvider(
            new AlgoPackClient(RestClient.builder(), algoPackProperties),
            new IssClient(RestClient.builder(), issProperties),
            new EmptyCalendar(),
            algoPackProperties,
            () -> NOW);
  }

  @AfterEach
  void tearDown() {
    server.close();
  }

  @Test
  void quoteOrderBookTradesAndCandlesComeFromAlgoPackFixtures() {
    Quote quote = provider.quote(TestInstruments.sber()).orElseThrow();
    assertThat(quote.last()).isEqualByComparingTo("298.89");
    assertThat(quote.change()).isEqualByComparingTo("0.89");
    assertThat(quote.changeRate()).isEqualByComparingTo("0.30");
    assertThat(quote.tradingStatus()).isEqualTo(17);

    OrderBookSnapshot book = provider.orderBook(TestInstruments.sber(), 2);
    assertThat(book.bids()).hasSize(2);
    assertThat(book.asks()).hasSize(2);
    assertThat(book.dateTime()).isEqualTo(Instant.parse("2026-11-02T16:34:55Z"));

    List<Trade> trades =
        provider.trades(TestInstruments.sber(), NOW.minusSeconds(3_600), NOW.plusSeconds(3_600));
    assertThat(trades).hasSize(3);
    assertThat(trades)
        .extracting(Trade::direction)
        .containsExactly(TradeDirection.SELL, TradeDirection.BUY, TradeDirection.BUY);

    CandleSeries series =
        provider.candles(TestInstruments.sber(), NOW.minusSeconds(86_400), NOW, TimeFrame.D);
    assertThat(series.timeFrame()).isEqualTo(TimeFrame.D);
    assertThat(series.bars()).hasSize(3);
    assertThat(series.bars().get(2).close()).isEqualByComparingTo("291.20");
  }

  @Test
  void instrumentsDirectoryIsLoadedFromIssOnlyOnce() {
    List<Instrument> first = provider.instruments(List.of("ABIO", "AFLT"));
    provider.instruments(List.of("ABIO"));
    provider.instrument("ABIO", "TQBR");

    assertThat(first).extracting(Instrument::ticker).containsExactly("ABIO", "AFLT");
    Instrument abio = provider.instrument("ABIO", "TQBR").orElseThrow();
    assertThat(abio.type()).isEqualTo("STOCK");
    assertThat(abio.shortName()).isEqualTo("iАРТГЕН ао");
    assertThat(abio.isin()).isEqualTo("RU000A0JNAB6");
    assertThat(abio.lotSize()).isEqualByComparingTo("1");
    assertThat(abio.minimumStep()).isEqualByComparingTo("0.02");
    assertThat(abio.currency()).isEqualTo("RUB");
    assertThat(server.requestCount()).isEqualTo(1);
  }

  @Test
  void advIsComputedFromDailyCandlesInShares() {
    assertThat(provider.adv(TestInstruments.sber())).isEqualByComparingTo("117379043.33");
  }

  private static AlgoApiTestServer.Response ok(String body) {
    return new AlgoApiTestServer.Response(200, body);
  }

  /** Календарь-заглушка: провайдер только делегирует, содержимое здесь не важно. */
  private static final class EmptyCalendar implements TradingCalendar {

    @Override
    public TradingSession tradingSession(Instant at) {
      return null;
    }

    @Override
    public List<TradingDay> tradingDays(Instant from, Instant to) {
      return List.of();
    }
  }
}
