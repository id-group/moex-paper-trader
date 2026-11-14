package ru.moex.papertrader.infra.services;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import ru.moex.papertrader.PostgresContainerSupport;
import ru.moex.papertrader.core.marketdata.TradingDay;
import ru.moex.papertrader.infra.repository.TradingCalendarRepository;

/**
 * Кэш календаря в PostgreSQL: дозагрузка внутри {@code tradingDays()} обязана выполняться в
 * транзакции сервисного слоя. Регрессия — синхронизация из {@link TradingCalendarInitializer}
 * (старт приложения в LIVE) падала с «No EntityManager with actual transaction available», потому
 * что вызов приходит не из транзакционного теста/контроллера.
 *
 * <p>Тест намеренно не транзакционный: транзакция должна открываться самим сервисом.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
class TradingCalendarServiceIT extends PostgresContainerSupport {

  /** Уникальный режим торгов: строки календаря этого теста не пересекаются с другими. */
  private static final String BOARD = "ITCAL";

  private static final LocalDate TRADE_DATE = LocalDate.of(2026, 10, 28);

  private static final HttpServer ISS = startIssStub();

  @Autowired private TradingCalendarService service;
  @Autowired private TradingCalendarRepository repository;

  @DynamicPropertySource
  static void marketDataProperties(DynamicPropertyRegistry registry) {
    registry.add(
        "emulator.marketdata.iss-base-url", () -> "http://127.0.0.1:" + ISS.getAddress().getPort());
    registry.add("emulator.marketdata.calendar-board", () -> BOARD);
  }

  @Test
  void tradingDaysSynchronizationPersistsDaysInServiceTransaction() {
    List<TradingDay> days =
        service.tradingDays(
            Instant.parse("2026-10-28T00:00:00Z"), Instant.parse("2026-10-28T20:00:00Z"));

    assertThat(days).extracting(TradingDay::date).containsExactly(TRADE_DATE);
    // upsert репозитория (@Modifying) выполнился — значит, была транзакция сервиса
    assertThat(
            repository.findByEngineAndMarketAndBoardAndTradeDate(
                "stock", "shares", BOARD, TRADE_DATE))
        .isPresent();
  }

  /** Локальный стенд ISS: история сделок есть, минутных свечей нет (fallback-окно настроек). */
  private static HttpServer startIssStub() {
    try {
      HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
      byte[] history = fixture("/fixtures/iss/trading-schedule.json");
      server.createContext("/iss/history/", exchange -> respond(exchange, history));
      byte[] emptyCandles =
          "{\"candles\":{\"columns\":[\"begin\",\"end\"],\"data\":[]}}"
              .getBytes(StandardCharsets.UTF_8);
      server.createContext("/iss/engines/", exchange -> respond(exchange, emptyCandles));
      server.start();
      return server;
    } catch (IOException exception) {
      throw new IllegalStateException("Стенд ISS не поднялся", exception);
    }
  }

  private static byte[] fixture(String name) throws IOException {
    try (InputStream stream = TradingCalendarServiceIT.class.getResourceAsStream(name)) {
      assertThat(stream).as("fixture: %s", name).isNotNull();
      return stream.readAllBytes();
    }
  }

  private static void respond(com.sun.net.httpserver.HttpExchange exchange, byte[] body)
      throws IOException {
    exchange.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");
    exchange.sendResponseHeaders(200, body.length);
    try (OutputStream out = exchange.getResponseBody()) {
      out.write(body);
    }
  }
}
