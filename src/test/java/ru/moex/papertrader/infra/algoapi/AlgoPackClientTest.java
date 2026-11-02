package ru.moex.papertrader.infra.algoapi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;
import ru.moex.papertrader.infra.algoapi.AlgoApiTestServer.Response;
import ru.moex.papertrader.infra.algoapi.dto.AlgoPackCandleDto;
import ru.moex.papertrader.infra.algoapi.dto.AlgoPackOrderBookDto;
import ru.moex.papertrader.infra.algoapi.dto.AlgoPackQuoteDto;
import ru.moex.papertrader.infra.algoapi.dto.AlgoPackTradeDto;

/** Тесты HTTP-клиента AlgoPack на локальном сервере и фикстурах схемы API. */
class AlgoPackClientTest {

  private static final String QUOTE_FIXTURE = "algoapi/quote.json";
  private static final String TRADE_TIME = "2026-11-02T16:34:55Z";

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
  void parsesQuoteFromSecuritiesAndMarketdataBlocks() {
    server.route("/securities/SBER.json", ok(AlgoApiFixtures.read(QUOTE_FIXTURE)));

    AlgoPackQuoteDto quote = client(properties("test-token", 2_000)).quote("SBER");

    assertThat(quote.secId()).isEqualTo("SBER");
    assertThat(quote.boardId()).isEqualTo("TQBR");
    assertThat(quote.bid()).isEqualByComparingTo("298.88");
    assertThat(quote.offer()).isEqualByComparingTo("298.90");
    assertThat(quote.last()).isEqualByComparingTo("298.89");
    assertThat(quote.prevPrice()).isEqualByComparingTo("298.00");
    assertThat(quote.change()).isEqualByComparingTo("0.89");
    assertThat(quote.changeRate()).isEqualByComparingTo("0.30");
    assertThat(quote.updatedAt()).isEqualTo(Instant.parse(TRADE_TIME));
    assertThat(server.lastRequest().path())
        .isEqualTo("/iss/engines/stock/markets/shares/boards/TQBR/securities/SBER.json");
  }

  @Test
  void parsesOrderBookAndLimitsDepth() {
    server.route(
        "/securities/SBER/orderbook.json", ok(AlgoApiFixtures.read("algoapi/orderbook.json")));

    AlgoPackOrderBookDto book = client(properties("test-token", 2_000)).orderBook("SBER", 2);

    assertThat(book.secId()).isEqualTo("SBER");
    assertThat(book.boardId()).isEqualTo("TQBR");
    assertThat(book.updatedAt()).isEqualTo(Instant.parse(TRADE_TIME));
    assertThat(book.bids())
        .extracting(AlgoPackOrderBookDto.Level::price)
        .containsExactly(new BigDecimal("298.88"), new BigDecimal("298.87"));
    assertThat(book.asks())
        .extracting(AlgoPackOrderBookDto.Level::quantity)
        .containsExactly(1200L, 800L);
  }

  @Test
  void parsesCandlesAndSendsIntervalAndPeriod() {
    server.route("/securities/SBER/candles.json", ok(AlgoApiFixtures.read("algoapi/candles.json")));

    List<AlgoPackCandleDto> candles =
        client(properties("test-token", 2_000))
            .candles(
                "SBER",
                "60",
                Instant.parse("2026-10-30T21:00:00Z"),
                Instant.parse("2026-11-03T00:00:00Z"));

    assertThat(candles).hasSize(3);
    assertThat(candles.get(0).begin()).isEqualTo(Instant.parse("2026-10-30T21:00:00Z"));
    assertThat(candles.get(0).end()).isEqualTo(Instant.parse("2026-10-31T20:59:59Z"));
    assertThat(candles.get(0).open()).isEqualByComparingTo("296.70");
    assertThat(candles.get(2).volume()).isEqualByComparingTo("33199285");
    String query = URLDecoder.decode(server.lastRequest().query(), StandardCharsets.UTF_8);
    assertThat(query).contains("interval=60").contains("from=").contains("till=");
  }

  @Test
  void parsesTradesAndFiltersByPeriod() {
    server.route("/securities/SBER/trades.json", ok(AlgoApiFixtures.read("algoapi/trades.json")));

    List<AlgoPackTradeDto> all =
        client(properties("test-token", 2_000)).allTrades("SBER", null, null);
    List<AlgoPackTradeDto> later =
        client(properties("test-token", 2_000))
            .allTrades("SBER", Instant.parse("2026-11-02T16:34:56Z"), null);

    assertThat(all).hasSize(3);
    assertThat(all.get(0).tradeNo()).isEqualTo(15195661254L);
    assertThat(all.get(0).tradeTime()).isEqualTo(Instant.parse(TRADE_TIME));
    assertThat(all.get(0).buySell()).isEqualTo("S");
    assertThat(all.get(2).buySell()).isNull();
    assertThat(later)
        .extracting(AlgoPackTradeDto::tradeNo)
        .containsExactly(15195661255L, 15195661256L);
  }

  @Test
  void retriesOnServerErrorAndSucceeds() {
    AtomicInteger attempts = new AtomicInteger();
    server.route(
        "/securities/SBER.json",
        () ->
            attempts.incrementAndGet() <= 2
                ? new Response(500, "{\"error\":\"boom\"}")
                : ok(AlgoApiFixtures.read(QUOTE_FIXTURE)));

    AlgoPackQuoteDto quote = client(properties("test-token", 2_000)).quote("SBER");

    assertThat(quote.last()).isEqualByComparingTo("298.89");
    assertThat(attempts).hasValue(3);
    assertThat(server.requestCount()).isEqualTo(3);
  }

  @Test
  void failsAfterExhaustingRetries() {
    AtomicInteger attempts = new AtomicInteger();
    server.route(
        "/securities/SBER.json",
        () -> {
          attempts.incrementAndGet();
          return new Response(500, "{\"error\":\"boom\"}");
        });

    assertThatThrownBy(() -> client(properties("test-token", 2_000)).quote("SBER"))
        .isInstanceOf(AlgoApiException.class)
        .hasMessageContaining("не выполнен после 3 попыток");
    assertThat(attempts).hasValue(3);
  }

  @Test
  void noClientErrorIsNotRetried() {
    AtomicInteger attempts = new AtomicInteger();
    server.route(
        "/securities/SBER.json",
        () -> {
          attempts.incrementAndGet();
          return new Response(401, "{\"error\":\"unauthorized\"}");
        });

    assertThatThrownBy(() -> client(properties("test-token", 2_000)).quote("SBER"))
        .isInstanceOf(AlgoApiException.class);
    assertThat(attempts).hasValue(1);
  }

  @Test
  void sendsTokenHeaderOnlyWhenTokenPresent() {
    server.route("/securities/SBER.json", ok(AlgoApiFixtures.read(QUOTE_FIXTURE)));

    // Решение владельца (2026-11-02): Authorization: Bearer <APIKEY> вместо X-AlgoPack-Token.
    client(properties("secret-token", 2_000)).quote("SBER");
    assertThat(server.lastRequest().headers())
        .containsEntry("authorization", "Bearer secret-token");

    client(properties("", 2_000)).quote("SBER");
    assertThat(server.lastRequest().headers()).doesNotContainKey("authorization");
  }

  @Test
  void readTimeoutBecomesAlgoApiException() {
    server.route(
        "/securities/SBER.json",
        () -> {
          sleep(1_500);
          return ok(AlgoApiFixtures.read(QUOTE_FIXTURE));
        });

    assertThatThrownBy(() -> client(properties("test-token", 200)).quote("SBER"))
        .isInstanceOf(AlgoApiException.class);
  }

  @Test
  void invalidJsonBecomesAlgoApiException() {
    server.route("/securities/SBER.json", new Response(200, "not a json"));

    assertThatThrownBy(() -> client(properties("test-token", 2_000)).quote("SBER"))
        .isInstanceOf(AlgoApiException.class)
        .hasMessageContaining("некорректный JSON");
  }

  private AlgoPackClient client(AlgoPackProperties properties) {
    return new AlgoPackClient(RestClient.builder(), properties);
  }

  private AlgoPackProperties properties(String token, int readTimeoutMs) {
    return new AlgoPackProperties(
        server.baseUrl(), token, 2_000, readTimeoutMs, 2, 10, "stock", "shares", "TQBR");
  }

  private static Response ok(String body) {
    return new Response(200, body);
  }

  private static void sleep(long millis) {
    try {
      Thread.sleep(millis);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }
}
