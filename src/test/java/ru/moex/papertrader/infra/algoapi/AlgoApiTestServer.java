package ru.moex.papertrader.infra.algoapi;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Supplier;

/**
 * Локальный HTTP-сервер для тестов клиентов: подменяет AlgoPack/ISS без внешних библиотек
 * (встроенный JDK {@link HttpServer}). Позволяет проверить путь, query-параметры, заголовки и число
 * запросов (ретраи).
 */
final class AlgoApiTestServer implements AutoCloseable {

  record Request(String method, String path, String query, Map<String, String> headers) {}

  record Response(int status, String body) {}

  private final HttpServer server;
  private final ExecutorService executor;
  private final List<Request> requests = new CopyOnWriteArrayList<>();
  private final Map<String, Supplier<Response>> routes = new ConcurrentHashMap<>();

  private AlgoApiTestServer(HttpServer server, ExecutorService executor) {
    this.server = server;
    this.executor = executor;
  }

  static AlgoApiTestServer start() throws IOException {
    HttpServer server =
        HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
    ExecutorService executor = Executors.newCachedThreadPool();
    AlgoApiTestServer testServer = new AlgoApiTestServer(server, executor);
    server.createContext("/", testServer::handle);
    server.setExecutor(executor);
    server.start();
    return testServer;
  }

  /** Маршрут по фрагменту пути; выигрывает самый длинный совпавший фрагмент. */
  void route(String pathFragment, Response response) {
    routes.put(pathFragment, () -> response);
  }

  /** Маршрут с динамическим ответом (например, для проверки ретраев). */
  void route(String pathFragment, Supplier<Response> response) {
    routes.put(pathFragment, response);
  }

  String baseUrl() {
    return "http://127.0.0.1:" + server.getAddress().getPort();
  }

  List<Request> requests() {
    return List.copyOf(requests);
  }

  int requestCount() {
    return requests.size();
  }

  Request lastRequest() {
    return requests.get(requests.size() - 1);
  }

  private void handle(HttpExchange exchange) throws IOException {
    String path = exchange.getRequestURI().getPath();
    Map<String, String> headers = new LinkedHashMap<>();
    exchange
        .getRequestHeaders()
        .forEach((name, values) -> headers.put(name.toLowerCase(Locale.ROOT), values.get(0)));
    requests.add(
        new Request(
            exchange.getRequestMethod(), path, exchange.getRequestURI().getRawQuery(), headers));
    Response response = resolve(path);
    byte[] body = response.body().getBytes(StandardCharsets.UTF_8);
    exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
    exchange.sendResponseHeaders(response.status(), body.length);
    try (OutputStream out = exchange.getResponseBody()) {
      out.write(body);
    }
  }

  private Response resolve(String path) {
    return routes.entrySet().stream()
        .filter(entry -> path.contains(entry.getKey()))
        .max(Map.Entry.comparingByKey((left, right) -> left.length() - right.length()))
        .map(entry -> entry.getValue().get())
        .orElse(new Response(404, "{\"error\":\"not found\"}"));
  }

  @Override
  public void close() {
    server.stop(0);
    executor.shutdownNow();
  }
}
