package ru.moex.papertrader.infra.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** Счёт владельца портфеля; естественный ключ — {@code account_id}. */
@Entity
@Table(name = "accounts")
public class AccountEntity {

  @Id
  @Column(name = "account_id", nullable = false, length = 64)
  private String accountId;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  /** Конструктор для JPA. */
  protected AccountEntity() {}

  public AccountEntity(String accountId, Instant createdAt) {
    this.accountId = accountId;
    this.createdAt = createdAt;
  }

  public String getAccountId() {
    return accountId;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
