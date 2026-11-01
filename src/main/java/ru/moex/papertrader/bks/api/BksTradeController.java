package ru.moex.papertrader.bks.api;

import java.util.List;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ru.moex.papertrader.bks.adapter.BksTradeMapper;
import ru.moex.papertrader.bks.contract.ListResponseTradesSearchResponse;
import ru.moex.papertrader.bks.contract.TradesSearchRequest;
import ru.moex.papertrader.bks.contract.TradesSearchResponse;
import ru.moex.papertrader.core.domain.PageResult;
import ru.moex.papertrader.core.domain.Trade;
import ru.moex.papertrader.core.domain.TradeSearchCriteria;
import ru.moex.papertrader.infra.services.TradeService;

/** Сделки БКС: поиск. */
@RestController
@RequestMapping("${emulator.brokers.bks.base-path:/api/bks}/v1")
public class BksTradeController {

  private final TradeService tradeService;
  private final BksTradeMapper tradeMapper;

  public BksTradeController(TradeService tradeService, BksTradeMapper tradeMapper) {
    this.tradeService = tradeService;
    this.tradeMapper = tradeMapper;
  }

  @PostMapping("/trades/search")
  public ListResponseTradesSearchResponse search(
      @RequestBody TradesSearchRequest request,
      @RequestParam(name = "page", defaultValue = "0") int page,
      @RequestParam(name = "size", defaultValue = "50") int size,
      @RequestParam(name = "sort", required = false) List<String> sort) {
    BksPagination.validate(page, size);
    TradeSearchCriteria criteria = tradeMapper.toCriteria(request);
    PageResult<Trade> result = tradeService.search(criteria, page, size, sort);
    List<TradesSearchResponse> records =
        result.items().stream().map(tradeMapper::toResponse).toList();
    return new ListResponseTradesSearchResponse(
        records, result.totalRecords(), result.totalPages());
  }
}
