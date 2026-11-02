package ru.moex.papertrader.infra.algoapi;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.moex.papertrader.core.domain.Instrument;
import ru.moex.papertrader.infra.algoapi.dto.IssSecurityDto;

/**
 * Ленивый кэш справочника инструментов из MOEX ISS, общий для провайдеров AlgoPack и ISS.
 *
 * <p>Справочник загружается один раз (double-checked locking) и дальше используется без сетевых
 * вызовов. Ключ — пара {@code ticker|classCode}; {@code classCode} — режим торгов ISS. Специфика
 * брокера/площадки сюда не проникает: на выходе — брокер-независимый {@link Instrument}.
 */
final class IssInstrumentCatalog {

  private static final Logger log = LoggerFactory.getLogger(IssInstrumentCatalog.class);

  private static final String INSTRUMENT_TYPE = "STOCK";
  private static final String DEFAULT_CURRENCY = "RUB";
  private static final BigDecimal DEFAULT_LOT_SIZE = BigDecimal.ONE;
  private static final BigDecimal DEFAULT_MIN_STEP = new BigDecimal("0.01");

  private final IssClient iss;
  private final String engine;
  private final String market;
  private final String board;
  private volatile Map<String, Instrument> cache;

  IssInstrumentCatalog(IssClient iss, String engine, String market, String board) {
    this.iss = iss;
    this.engine = engine;
    this.market = market;
    this.board = board;
  }

  /** Инструменты по списку тикеров: порядок запроса сохраняется, неизвестные пропускаются. */
  List<Instrument> instruments(Collection<String> tickers) {
    Map<String, Instrument> instruments = cache();
    List<Instrument> result = new ArrayList<>();
    for (String ticker : new LinkedHashSet<>(tickers)) {
      for (Instrument instrument : instruments.values()) {
        if (instrument.ticker().equals(ticker)) {
          result.add(instrument);
          break;
        }
      }
    }
    return List.copyOf(result);
  }

  /** Инструмент по паре тикер + режим торгов. */
  Optional<Instrument> instrument(String ticker, String classCode) {
    return Optional.ofNullable(cache().get(key(ticker, classCode)));
  }

  private Map<String, Instrument> cache() {
    Map<String, Instrument> local = cache;
    if (local == null) {
      synchronized (this) {
        local = cache;
        if (local == null) {
          local = load();
          cache = local;
        }
      }
    }
    return local;
  }

  private Map<String, Instrument> load() {
    Map<String, Instrument> result = new LinkedHashMap<>();
    for (IssSecurityDto dto : iss.securities(engine, market, board)) {
      try {
        Instrument instrument = toInstrument(dto);
        result.put(key(instrument.ticker(), instrument.classCode()), instrument);
      } catch (RuntimeException e) {
        log.warn("ISS: пропущен некорректный инструмент {}: {}", dto.secId(), e.getMessage());
      }
    }
    return Map.copyOf(result);
  }

  private Instrument toInstrument(IssSecurityDto dto) {
    String boardId = dto.boardId() == null || dto.boardId().isBlank() ? board : dto.boardId();
    String shortName = firstNonBlank(dto.shortName(), dto.secName(), dto.secId());
    String displayName = firstNonBlank(dto.secName(), shortName);
    return new Instrument(
        dto.secId(),
        boardId,
        INSTRUMENT_TYPE,
        shortName,
        displayName,
        dto.isin(),
        positiveOr(dto.lotSize(), DEFAULT_LOT_SIZE),
        positiveOr(dto.minStep(), DEFAULT_MIN_STEP),
        currency(dto.currencyId()));
  }

  private static String currency(String currencyId) {
    if (currencyId == null || currencyId.isBlank()) {
      return DEFAULT_CURRENCY;
    }
    // ISS обозначает рубль как SUR; в домене используется международный код RUB.
    return "SUR".equalsIgnoreCase(currencyId) ? DEFAULT_CURRENCY : currencyId;
  }

  private static BigDecimal positiveOr(BigDecimal value, BigDecimal fallback) {
    return value == null || value.signum() <= 0 ? fallback : value;
  }

  private static String firstNonBlank(String... values) {
    for (String value : values) {
      if (value != null && !value.isBlank()) {
        return value;
      }
    }
    return null;
  }

  private static String key(String ticker, String classCode) {
    return ticker + "|" + classCode;
  }
}
