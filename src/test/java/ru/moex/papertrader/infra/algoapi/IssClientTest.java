package ru.moex.papertrader.infra.algoapi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;
import ru.moex.papertrader.infra.algoapi.dto.IssCandleDto;
import ru.moex.papertrader.infra.algoapi.dto.IssOrderBookDto;
import ru.moex.papertrader.infra.algoapi.dto.IssSecurityDto;
import ru.moex.papertrader.infra.algoapi.dto.IssTradeDto;

/** Тесты HTTP-клиента MOEX ISS на локальном сервере и реальных ответах ISS. */
class IssClientTest {

  private AlgoApiTestServer server;

  @BeforeEach
  void setUp() throws Exception {
    server = AlgoApiTestServer.start();
  }

  @AfterEach
  void tearDown() {
    server.close();
  }

  @Test
  void parsesSecuritiesDirectory() {
    server.route("/securities.json", ok(AlgoApiFixtures.read("iss/securities.json")));

    List<IssSecurityDto> securities = client().securities("stock", "shares", "TQBR");

    assertThat(securities).hasSize(6);
    IssSecurityDto first = securities.get(0);
    assertThat(first.secId()).isEqualTo("ABIO");
    assertThat(first.boardId()).isEqualTo("TQBR");
    assertThat(first.shortName()).isEqualTo("iАРТГЕН ао");
    assertThat(first.secName()).isEqualTo("ПАО \"Артген\"");
    assertThat(first.isin()).isEqualTo("RU000A0JNAB6");
    assertThat(first.lotSize()).isEqualByComparingTo("1");
    assertThat(first.minStep()).isEqualByComparingTo("0.02");
    assertThat(first.currencyId()).isEqualTo("SUR");
    String query = URLDecoder.decode(server.lastRequest().query(), StandardCharsets.UTF_8);
    assertThat(query).contains("iss.meta=off").contains("iss.only=securities");
  }

  @Test
  void parsesOrderBook() {
    server.route("/orderbook.json", ok(AlgoApiFixtures.read("iss/orderbook.json")));

    IssOrderBookDto book = client().orderBook("stock", "shares", "TQBR", "SBER");

    assertThat(book.secId()).isEqualTo("SBER");
    assertThat(book.boardId()).isEqualTo("TQBR");
    assertThat(book.updatedAt()).isNull();
    assertThat(book.last()).isNull();
    assertThat(book.bids()).hasSize(3);
    assertThat(book.asks()).hasSize(3);
    assertThat(book.bids().get(0).price()).isEqualByComparingTo("298.88");
    assertThat(book.bids().get(0).quantity()).isEqualTo(1510L);
    assertThat(book.asks().get(0).price()).isEqualByComparingTo("298.90");
  }

  @Test
  void parsesDailyCandlesWithMoscowTime() {
    server.route("/candles.json", ok(AlgoApiFixtures.read("iss/candles_daily.json")));

    List<IssCandleDto> candles =
        client()
            .candles(
                "stock",
                "shares",
                "TQBR",
                "SBER",
                24,
                Instant.parse("2026-09-01T00:00:00Z"),
                Instant.parse("2026-10-01T00:00:00Z"));

    assertThat(candles).isNotEmpty();
    assertThat(candles.get(0).begin()).isEqualTo(Instant.parse("2026-08-31T21:00:00Z"));
    assertThat(candles.get(0).end()).isEqualTo(Instant.parse("2026-09-01T20:59:59Z"));
    assertThat(candles.get(0).open()).isEqualByComparingTo("276.03");
    assertThat(candles.get(0).volume()).isEqualByComparingTo("26826095");
    String query = URLDecoder.decode(server.lastRequest().query(), StandardCharsets.UTF_8);
    assertThat(query)
        .contains("interval=24")
        .contains("iss.meta=off")
        // ISS принимает from/till только как дату: время в параметрах возвращает пустой набор.
        .contains("from=2026-09-01")
        .contains("till=2026-10-01");
  }

  @Test
  void parsesTradesAndFiltersByPeriod() {
    server.route("/trades.json", ok(AlgoApiFixtures.read("iss/trades.json")));

    List<IssTradeDto> trades = client().trades("stock", "shares", "TQBR", "SBER", null, null);
    List<IssTradeDto> empty =
        client()
            .trades("stock", "shares", "TQBR", "SBER", Instant.parse("2026-11-02T04:00:00Z"), null);

    assertThat(trades).hasSize(5);
    assertThat(trades.get(0).tradeNo()).isEqualTo(18016891275L);
    assertThat(trades.get(0).tradeTime()).isEqualTo(Instant.parse("2026-11-02T03:59:39Z"));
    assertThat(trades.get(0).price()).isEqualByComparingTo("296.7");
    assertThat(trades.get(0).quantity()).isEqualTo(1L);
    assertThat(trades.get(0).buySell()).isEqualTo("S");
    assertThat(trades.get(4).buySell()).isEqualTo("B");
    assertThat(empty).isEmpty();
  }

  @Test
  void failureIsWrappedIntoAlgoApiException() {
    server.route("/securities.json", new AlgoApiTestServer.Response(500, "{\"error\":\"boom\"}"));

    assertThatThrownBy(() -> client().securities("stock", "shares", "TQBR"))
        .isInstanceOf(AlgoApiException.class);
    assertThat(server.requestCount()).isEqualTo(3);
  }

  private IssClient client() {
    IssProperties properties =
        new IssProperties(server.baseUrl(), 2_000, 2_000, 2, 10, "stock", "shares", "TQBR");
    return new IssClient(RestClient.builder(), properties);
  }

  private static AlgoApiTestServer.Response ok(String body) {
    return new AlgoApiTestServer.Response(200, body);
  }
}
