package ru.moex.papertrader.infra.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import org.springframework.lang.Nullable;

/**
 * Торговый день календаря MOEX: одна строка на (движок, рынок, режим, дата).
 *
 * <p>Для неторговых дней {@code openUtc} и {@code closeUtc} — {@code null}. Время хранится в UTC,
 * дата — торговая дата в часовом поясе биржи (Europe/Moscow).
 */
@Entity
@Table(name = "trading_calendar")
public class TradingCalendarEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "engine", nullable = false, length = 16)
  private String engine;

  @Column(name = "market", nullable = false, length = 16)
  private String market;

  @Column(name = "board", nullable = false, length = 16)
  private String board;

  @Column(name = "trade_date", nullable = false)
  private LocalDate tradeDate;

  @Column(name = "is_trading", nullable = false)
  private boolean trading;

  @Nullable
  @Column(name = "open_utc")
  private Instant openUtc;

  @Nullable
  @Column(name = "close_utc")
  private Instant closeUtc;

  /** Конструктор для JPA. */
  protected TradingCalendarEntity() {}

  public TradingCalendarEntity(
      String engine,
      String market,
      String board,
      LocalDate tradeDate,
      boolean trading,
      @Nullable Instant openUtc,
      @Nullable Instant closeUtc) {
    this.engine = engine;
    this.market = market;
    this.board = board;
    this.tradeDate = tradeDate;
    this.trading = trading;
    this.openUtc = openUtc;
    this.closeUtc = closeUtc;
  }

  public Long getId() {
    return id;
  }

  public String getEngine() {
    return engine;
  }

  public String getMarket() {
    return market;
  }

  public String getBoard() {
    return board;
  }

  public LocalDate getTradeDate() {
    return tradeDate;
  }

  public boolean isTrading() {
    return trading;
  }

  @Nullable
  public Instant getOpenUtc() {
    return openUtc;
  }

  @Nullable
  public Instant getCloseUtc() {
    return closeUtc;
  }
}
