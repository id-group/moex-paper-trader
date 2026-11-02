package ru.moex.papertrader.infra.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;

/** Денежные средства и блокировки счёта в разрезе валюты. */
@Entity
@Table(name = "account_balances")
public class AccountBalanceEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "account_id", nullable = false, length = 64)
  private String accountId;

  @Column(name = "currency", nullable = false, length = 8)
  private String currency;

  @Column(name = "cash", nullable = false, precision = 19, scale = 2)
  private BigDecimal cash;

  @Column(name = "locked", nullable = false, precision = 19, scale = 2)
  private BigDecimal locked;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  /** Конструктор для JPA. */
  protected AccountBalanceEntity() {}

  public AccountBalanceEntity(
      String accountId, String currency, BigDecimal cash, BigDecimal locked, Instant updatedAt) {
    this.accountId = accountId;
    this.currency = currency;
    this.cash = cash;
    this.locked = locked;
    this.updatedAt = updatedAt;
  }

  public Long getId() {
    return id;
  }

  public String getAccountId() {
    return accountId;
  }

  public String getCurrency() {
    return currency;
  }

  public BigDecimal getCash() {
    return cash;
  }

  public BigDecimal getLocked() {
    return locked;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }
}
