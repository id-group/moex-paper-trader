package ru.moex.papertrader.infra.algoapi;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.OptionalInt;
import org.junit.jupiter.api.Test;
import ru.moex.papertrader.core.marketdata.TimeFrame;

/** Тесты маппинга таймфреймов core на интервалы ISS/AlgoPack. */
class CandleIntervalsTest {

  @Test
  void mapsOnlyIntervalsSupportedByBothApis() {
    assertThat(CandleIntervals.toIntervalCode(TimeFrame.M1)).hasValue(1);
    assertThat(CandleIntervals.toIntervalCode(TimeFrame.H1)).hasValue(60);
    assertThat(CandleIntervals.toIntervalCode(TimeFrame.D)).hasValue(24);
    assertThat(CandleIntervals.toIntervalCode(TimeFrame.W)).hasValue(7);
    assertThat(CandleIntervals.toIntervalCode(TimeFrame.MN)).hasValue(31);
  }

  @Test
  void unsupportedTimeFramesHaveNoInterval() {
    assertThat(CandleIntervals.toIntervalCode(TimeFrame.M5)).isEmpty();
    assertThat(CandleIntervals.toIntervalCode(TimeFrame.M15)).isEmpty();
    assertThat(CandleIntervals.toIntervalCode(TimeFrame.M30)).isEmpty();
    assertThat(CandleIntervals.toIntervalCode(TimeFrame.H4)).isEmpty();
    assertThat(CandleIntervals.toIntervalCode(null)).isEmpty();
  }

  @Test
  void optionalIntContract() {
    OptionalInt interval = CandleIntervals.toIntervalCode(TimeFrame.D);

    assertThat(interval.getAsInt()).isEqualTo(24);
  }
}
