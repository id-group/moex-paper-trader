package ru.moex.papertrader.infra.mapper;

import java.util.List;
import ru.moex.papertrader.core.portfolio.NavSnapshot;
import tools.jackson.core.JacksonException;
import tools.jackson.core.StreamWriteFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * Кодек JSON-поля {@code nav_daily.holdings}: сериализует список {@link NavSnapshot.Holding}.
 *
 * <p>{@link StreamWriteFeature#WRITE_BIGDECIMAL_AS_PLAIN} запрещает научную нотацию, поэтому цены
 * остаются в десятичном виде и сохраняют исходный масштаб.
 */
public final class NavHoldingsCodec {

  private static final JsonMapper MAPPER =
      JsonMapper.builder().enable(StreamWriteFeature.WRITE_BIGDECIMAL_AS_PLAIN).build();

  public String encode(List<NavSnapshot.Holding> holdings) {
    if (holdings == null) {
      throw new IllegalArgumentException("holdings не может быть null");
    }
    try {
      return MAPPER.writeValueAsString(holdings);
    } catch (JacksonException exception) {
      throw new IllegalStateException("Не удалось сериализовать holdings NAV", exception);
    }
  }

  public List<NavSnapshot.Holding> decode(String json) {
    if (json == null || json.isBlank()) {
      throw new IllegalArgumentException("JSON holdings NAV не может быть пустым");
    }
    try {
      return List.of(MAPPER.readValue(json, NavSnapshot.Holding[].class));
    } catch (JacksonException exception) {
      throw new IllegalStateException(
          "Не удалось разобрать holdings NAV: " + exception.getMessage(), exception);
    }
  }
}
