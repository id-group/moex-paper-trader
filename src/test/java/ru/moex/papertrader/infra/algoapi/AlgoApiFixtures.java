package ru.moex.papertrader.infra.algoapi;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

/** Загрузка JSON-фикстур клиентов AlgoPack/ISS из {@code src/test/resources/fixtures}. */
final class AlgoApiFixtures {

  private AlgoApiFixtures() {}

  static String read(String resource) {
    try (InputStream in =
        AlgoApiFixtures.class.getClassLoader().getResourceAsStream("fixtures/" + resource)) {
      if (in == null) {
        throw new IllegalArgumentException("Фикстура не найдена: fixtures/" + resource);
      }
      return new String(in.readAllBytes(), StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
