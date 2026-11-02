package ru.moex.papertrader.infra.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/** Ежедневный срез NAV: один снапшот на пару (счёт, торговый день). */
@Entity
@Table(name = "nav_daily")
public class NavDailyEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "account_id", nullable = false, length = 64)
  private String accountId;

  @Column(name = "nav_date", nullable = false)
  private LocalDate navDate;

  @Column(name = "as_of", nullable = false)
  private Instant asOf;

  @Column(name = "cash", nullable = false, precision = 19, scale = 2)
  private BigDecimal cash;

  @Column(name = "commission", nullable = false, precision = 19, scale = 2)
  private BigDecimal commission;

  @Column(name = "variation_margin", nullable = false, precision = 19, scale = 2)
  private BigDecimal variationMargin;

  @Column(name = "market_value", nullable = false, precision = 19, scale = 2)
  private BigDecimal marketValue;

  @Column(name = "nav", nullable = false, precision = 19, scale = 2)
  private BigDecimal nav;

  /**
   * Позиции среза в JSON (NavSnapshot.Holding: ticker, quantity, closingPrice). Отдельная таблица
   * holdings не вводится: решение владельца — ровно семь таблиц.
   */
  @Column(name = "holdings", nullable = false, length = 4000)
  private String holdingsJson;

  /** Конструктор для JPA. */
  protected NavDailyEntity() {}

  public NavDailyEntity(
      String accountId,
      LocalDate navDate,
      Instant asOf,
      BigDecimal cash,
      BigDecimal commission,
      BigDecimal variationMargin,
      BigDecimal marketValue,
      BigDecimal nav,
      String holdingsJson) {
    this.accountId = accountId;
    this.navDate = navDate;
    this.asOf = asOf;
    this.cash = cash;
    this.commission = commission;
    this.variationMargin = variationMargin;
    this.marketValue = marketValue;
    this.nav = nav;
    this.holdingsJson = holdingsJson;
  }

  public Long getId() {
    return id;
  }

  public String getAccountId() {
    return accountId;
  }

  public LocalDate getNavDate() {
    return navDate;
  }

  public Instant getAsOf() {
    return asOf;
  }

  public BigDecimal getCash() {
    return cash;
  }

  public BigDecimal getCommission() {
    return commission;
  }

  public BigDecimal getVariationMargin() {
    return variationMargin;
  }

  public BigDecimal getMarketValue() {
    return marketValue;
  }

  public BigDecimal getNav() {
    return nav;
  }

  public String getHoldingsJson() {
    return holdingsJson;
  }
}
