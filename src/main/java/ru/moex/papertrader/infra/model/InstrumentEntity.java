package ru.moex.papertrader.infra.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;

/** Инструмент, торгуемый на Мосбирже; доменная идентичность — пара (ticker, class_code). */
@Entity
@Table(name = "instruments")
public class InstrumentEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "ticker", nullable = false, length = 32)
  private String ticker;

  @Column(name = "class_code", nullable = false, length = 32)
  private String classCode;

  @Column(name = "type", nullable = false, length = 32)
  private String type;

  @Column(name = "short_name", length = 255)
  private String shortName;

  @Column(name = "display_name", length = 255)
  private String displayName;

  @Column(name = "isin", length = 32)
  private String isin;

  @Column(name = "lot_size", nullable = false, precision = 19, scale = 6)
  private BigDecimal lotSize;

  @Column(name = "minimum_step", nullable = false, precision = 19, scale = 6)
  private BigDecimal minimumStep;

  @Column(name = "currency", nullable = false, length = 8)
  private String currency;

  /** Конструктор для JPA. */
  protected InstrumentEntity() {}

  public InstrumentEntity(
      String ticker,
      String classCode,
      String type,
      String shortName,
      String displayName,
      String isin,
      BigDecimal lotSize,
      BigDecimal minimumStep,
      String currency) {
    this.ticker = ticker;
    this.classCode = classCode;
    this.type = type;
    this.shortName = shortName;
    this.displayName = displayName;
    this.isin = isin;
    this.lotSize = lotSize;
    this.minimumStep = minimumStep;
    this.currency = currency;
  }

  public Long getId() {
    return id;
  }

  public String getTicker() {
    return ticker;
  }

  public String getClassCode() {
    return classCode;
  }

  public String getType() {
    return type;
  }

  public String getShortName() {
    return shortName;
  }

  public String getDisplayName() {
    return displayName;
  }

  public String getIsin() {
    return isin;
  }

  public BigDecimal getLotSize() {
    return lotSize;
  }

  public BigDecimal getMinimumStep() {
    return minimumStep;
  }

  public String getCurrency() {
    return currency;
  }
}
