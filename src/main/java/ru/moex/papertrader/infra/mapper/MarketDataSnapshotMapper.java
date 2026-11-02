package ru.moex.papertrader.infra.mapper;

import java.util.Objects;
import org.springframework.stereotype.Component;
import ru.moex.papertrader.core.marketdata.MarketDataSnapshot;
import ru.moex.papertrader.core.marketdata.OrderBookEvent;
import ru.moex.papertrader.core.marketdata.QuoteSnapshot;
import ru.moex.papertrader.core.marketdata.SnapshotKind;
import ru.moex.papertrader.core.marketdata.TradeEvent;
import ru.moex.papertrader.infra.model.MarketDataSnapshotEntity;
import tools.jackson.core.JacksonException;
import tools.jackson.core.StreamWriteFeature;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.ObjectWriter;
import tools.jackson.databind.cfg.DateTimeFeature;

/**
 * Маппинг снапшота рыночных данных: сущность {@code market_data_snapshots} ↔ доменный {@link
 * MarketDataSnapshot}.
 *
 * <p>Колонки {@code ts}, {@code kind}, {@code ticker} и {@code class_code} дублируют поля домена
 * для индекса таймлайна; полная форма события лежит в JSONB-поле {@code payload}. Конкретный record
 * выбирается по {@code kind}: {@code QUOTE} → {@link QuoteSnapshot}, {@code ORDER_BOOK} → {@link
 * OrderBookEvent}, {@code TRADE} → {@link TradeEvent}.
 *
 * <p>Маппер работает и без Spring: достаточно передать в конструктор настроенный {@link
 * ObjectMapper}.
 */
@Component
public class MarketDataSnapshotMapper {

  private final ObjectMapper objectMapper;
  private final ObjectWriter payloadWriter;

  public MarketDataSnapshotMapper(ObjectMapper objectMapper) {
    this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
    // Десятичные числа — без научной нотации, моменты времени — строкой ISO-8601 (UTC).
    this.payloadWriter =
        objectMapper
            .writer()
            .with(StreamWriteFeature.WRITE_BIGDECIMAL_AS_PLAIN)
            .without(DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS);
  }

  /** Создаёт сущность без {@code id}: первичный ключ назначает БД. */
  public MarketDataSnapshotEntity toEntity(MarketDataSnapshot snapshot) {
    Objects.requireNonNull(snapshot, "snapshot");
    Objects.requireNonNull(snapshot.timestamp(), "snapshot.timestamp");
    String ticker = requireText(snapshot.ticker(), "snapshot.ticker");
    String classCode = requireText(classCodeOf(snapshot), "snapshot.classCode");
    return new MarketDataSnapshotEntity(
        snapshot.timestamp(),
        snapshot.kind().name(),
        ticker,
        classCode,
        payloadWriter.writeValueAsString(snapshot));
  }

  /** Восстанавливает доменный снапшот из строки БД. */
  public MarketDataSnapshot toDomain(MarketDataSnapshotEntity entity) {
    Objects.requireNonNull(entity, "entity");
    String kindName = requireText(entity.getKind(), "entity.kind");
    String payload = requireText(entity.getPayload(), "entity.payload");
    SnapshotKind kind;
    try {
      kind = SnapshotKind.valueOf(kindName);
    } catch (IllegalArgumentException exception) {
      throw new IllegalStateException("Неизвестный тип снапшота: " + kindName, exception);
    }
    try {
      return switch (kind) {
        case QUOTE -> reader(QuoteSnapshot.class).readValue(payload);
        case ORDER_BOOK -> reader(OrderBookEvent.class).readValue(payload);
        case TRADE -> reader(TradeEvent.class).readValue(payload);
      };
    } catch (JacksonException exception) {
      throw new IllegalStateException(
          "Не удалось разобрать снапшот " + kindName + ": " + exception.getMessage(), exception);
    }
  }

  private <T> tools.jackson.databind.ObjectReader reader(Class<T> type) {
    // Доменные записи сериализуют и производные аксессоры (ticker(), kind()), которых нет в
    // каноническом конструкторе record, — лишние свойства при чтении игнорируются.
    return objectMapper.readerFor(type).without(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
  }

  private static String classCodeOf(MarketDataSnapshot snapshot) {
    return switch (snapshot) {
      case QuoteSnapshot quote -> quote.quote().classCode();
      case OrderBookEvent orderBook -> orderBook.orderBook().classCode();
      case TradeEvent trade -> trade.trade().classCode();
    };
  }

  private static String requireText(String value, String name) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(name + " не может быть пустым");
    }
    return value;
  }
}
