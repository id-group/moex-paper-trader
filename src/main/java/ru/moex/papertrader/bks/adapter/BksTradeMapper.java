package ru.moex.papertrader.bks.adapter;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashSet;
import java.util.Set;
import org.springframework.stereotype.Component;
import ru.moex.papertrader.bks.contract.TradesSearchRequest;
import ru.moex.papertrader.bks.contract.TradesSearchResponse;
import ru.moex.papertrader.core.domain.Side;
import ru.moex.papertrader.core.domain.Trade;
import ru.moex.papertrader.core.domain.TradeSearchCriteria;
import ru.moex.papertrader.infra.services.exception.InvalidRequestException;

/** РњР°РїРїРёРЅРі СЃРґРµР»РѕРє РјРµР¶РґСѓ РєРѕРЅС‚СЂР°РєС‚РѕРј Р‘РљРЎ Рё РґРѕРјРµРЅРѕРј core. */
@Component
public class BksTradeMapper {

  private static final int REGULAR_DEAL_TYPE = 1;
  private static final ZoneOffset UTC = ZoneOffset.UTC;

  public TradeSearchCriteria toCriteria(TradesSearchRequest request) {
    if (request == null) {
      return new TradeSearchCriteria(null, null, null, null, null, null, null);
    }
    return new TradeSearchCriteria(
        request.startDateTime() == null ? null : request.startDateTime().toInstant(),
        request.endDateTime() == null ? null : request.endDateTime().toInstant(),
        toSide(request.side()),
        request.tradeNums() == null ? Set.of() : new LinkedHashSet<>(request.tradeNums()),
        request.orderNums() == null ? Set.of() : new LinkedHashSet<>(request.orderNums()),
        request.tickers() == null ? Set.of() : new LinkedHashSet<>(request.tickers()),
        request.classCodes() == null ? Set.of() : new LinkedHashSet<>(request.classCodes()));
  }

  public TradesSearchResponse toResponse(Trade trade) {
    BigDecimal quantity = BigDecimal.valueOf(trade.quantity());
    BigDecimal volume = trade.price().multiply(quantity);
    String currency = trade.instrument().currency();
    return new TradesSearchResponse(
        trade.orderNum(),
        trade.instrument().ticker(),
        trade.tradeNum(),
        trade.clientCode(),
        trade.instrument().classCode(),
        currency,
        currency,
        currency,
        trade.side().code(),
        trade.instrument().type(),
        REGULAR_DEAL_TYPE,
        OffsetDateTime.ofInstant(trade.executedAt(), UTC),
        trade.price(),
        volume,
        null,
        volume,
        trade.executedAt().atOffset(UTC).toLocalDate(),
        quantity,
        quantity.divide(trade.instrument().lotSize(), 0, RoundingMode.DOWN));
  }

  private static Side toSide(String code) {
    if (code == null) {
      return null;
    }
    return Side.fromCode(code)
        .orElseThrow(
            () -> new InvalidRequestException("side РґРѕР»Р¶РµРЅ Р±С‹С‚СЊ \"1\" РёР»Рё \"2\""));
  }
}
