package ru.moex.papertrader;

import tools.jackson.databind.cfg.DateTimeFeature;
import tools.jackson.databind.json.JsonMapper;

/** Общий JsonMapper для unit-тестов контракта (даты — ISO-8601, как в приложении). */
public final class TestJson {

  public static final JsonMapper MAPPER =
      JsonMapper.builder().disable(DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS).build();

  private TestJson() {}
}
