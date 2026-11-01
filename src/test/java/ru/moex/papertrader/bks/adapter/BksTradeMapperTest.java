package ru.moex.papertrader.bks.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import ru.moex.papertrader.TestInstruments;
import ru.moex.papertrader.bks.contract.TradesSearchRequest;
import ru.moex.papertrader.bks.contract.TradesSearchResponse;
import ru.moex.papertrader.core.domain.Side;
import ru.moex.papertrader.core.domain.Trade;
import ru.moex.papertrader.core.domain.TradeSearchCriteria;
import ru.moex.papertrader.infra.services.exception.InvalidRequestException;

class BksTradeMapperTest {

  private static final Instant NOW = Instant.parse("2026-11-01T10:00:00Z");

  private final BksTradeMapper mapper = new BksTradeMapper();

  @Test
  void mapsSearchRequestToCriteria() {
    TradesSearchRequest request =
        new TradesSearchRequest(
            "2",
            List.of(1L),
            List.of(2L),
            List.of("SBER"),
            List.of("TQBR"),
            OffsetDateTime.parse("2026-11-01T00:00:00Z"),
            OffsetDateTime.parse("2026-11-02T00:00:00Z"));

    TradeSearchCriteria criteria = mapper.toCriteria(request);

    assertThat(criteria.side()).isEqualTo(Side.SELL);
    assertThat(criteria.tradeNums()).containsExactly(1L);
    assertThat(criteria.orderNums()).containsExactly(2L);
    assertThat(criteria.from()).isEqualTo(Instant.parse("2026-11-01T00:00:00Z"));
  }

  @Test
  void emptyRequestMapsToEmptyCriteria() {
    TradeSearchCriteria criteria = mapper.toCriteria(null);

    assertThat(criteria.side()).isNull();
    assertThat(criteria.tickers()).isEmpty();
  }

  @Test
  void rejectsUnknownSide() {
    assertThatThrownBy(
            () ->
                mapper.toCriteria(new TradesSearchRequest("9", null, null, null, null, null, null)))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("side")
        .hasMessageContaining("должен быть \"1\" или \"2\"");
  }

  @Test
  void mapsTradeToContractResponse() {
    Trade trade =
        new Trade(
            7,
            3,
            "PAPER-0001",
            TestInstruments.sber(),
            Side.BUY,
            new BigDecimal("285.50"),
            10,
            new BigDecimal("0.29"),
            NOW);

    TradesSearchResponse response = mapper.toResponse(trade);

    assertThat(response.tradeNum()).isEqualTo(7);
    assertThat(response.orderNum()).isEqualTo(3);
    assertThat(response.ticker()).isEqualTo("SBER");
    assertThat(response.classCode()).isEqualTo("TQBR");
    assertThat(response.side()).isEqualTo("1");
    assertThat(response.instrumentType()).isEqualTo("STOCK");
    assertThat(response.price()).isEqualByComparingTo("285.50");
    assertThat(response.volume()).isEqualByComparingTo("2855.00");
    assertThat(response.contractAmount()).isEqualByComparingTo("2855.00");
    assertThat(response.tradeQuantity()).isEqualByComparingTo("10");
    assertThat(response.tradeQuantityLots()).isEqualByComparingTo("1");
    assertThat(response.settlementCurrency()).isEqualTo("RUB");
    assertThat(response.settleDate()).hasToString("2026-11-01");
    assertThat(response.tradeDateTime()).isNotNull();
  }
}
