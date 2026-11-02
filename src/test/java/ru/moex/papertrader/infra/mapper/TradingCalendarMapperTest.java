package ru.moex.papertrader.infra.mapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import ru.moex.papertrader.core.marketdata.TradingDay;
import ru.moex.papertrader.infra.model.TradingCalendarEntity;

/** Прямой и обратный маппинг торгового дня. */
class TradingCalendarMapperTest {

  private static final String ENGINE = "stock";
  private static final String MARKET = "shares";
  private static final String BOARD = "TQBR";
  private static final LocalDate DATE = LocalDate.of(2026, 11, 2);
  private static final Instant OPEN = Instant.parse("2026-11-02T07:00:00Z");
  private static final Instant CLOSE = Instant.parse("2026-11-02T15:50:00Z");

  private final TradingCalendarMapper mapper = new TradingCalendarMapper();

  @Test
  void mapsTradingDayToEntityWithCalendarIdentity() {
    TradingDay day = new TradingDay(DATE, true, OPEN, CLOSE);

    TradingCalendarEntity entity = mapper.toEntity(day, ENGINE, MARKET, BOARD);

    assertThat(entity.getId()).isNull();
    assertThat(entity.getEngine()).isEqualTo(ENGINE);
    assertThat(entity.getMarket()).isEqualTo(MARKET);
    assertThat(entity.getBoard()).isEqualTo(BOARD);
    assertThat(entity.getTradeDate()).isEqualTo(DATE);
    assertThat(entity.isTrading()).isTrue();
    assertThat(entity.getOpenUtc()).isEqualTo(OPEN);
    assertThat(entity.getCloseUtc()).isEqualTo(CLOSE);
  }

  @Test
  void roundTripsTradingDay() {
    TradingDay original = new TradingDay(DATE, true, OPEN, CLOSE);

    TradingDay restored = mapper.toDomain(mapper.toEntity(original, ENGINE, MARKET, BOARD));

    assertThat(restored).isEqualTo(original);
  }

  @Test
  void keepsNullSessionWindowForNonTradingDay() {
    TradingDay holiday = new TradingDay(DATE, false, null, null);

    TradingCalendarEntity entity = mapper.toEntity(holiday, ENGINE, MARKET, BOARD);

    assertThat(entity.isTrading()).isFalse();
    assertThat(entity.getOpenUtc()).isNull();
    assertThat(entity.getCloseUtc()).isNull();
    assertThat(mapper.toDomain(entity)).isEqualTo(holiday);
  }

  @Test
  void rejectsNullArguments() {
    assertThatThrownBy(() -> mapper.toEntity(null, ENGINE, MARKET, BOARD))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(
            () -> mapper.toEntity(new TradingDay(DATE, false, null, null), " ", MARKET, BOARD))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("engine");
    assertThatThrownBy(() -> mapper.toDomain(null)).isInstanceOf(NullPointerException.class);
  }
}
