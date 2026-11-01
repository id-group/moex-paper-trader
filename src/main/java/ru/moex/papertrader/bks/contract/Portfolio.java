package ru.moex.papertrader.bks.contract;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.math.BigDecimal;

/** Запись портфеля (схема {@code Portfolio}). */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record Portfolio(
    String type,
    String subAccountId,
    String agreementId,
    String account,
    String exchange,
    String ticker,
    String displayName,
    String baseAssetTicker,
    String currency,
    String upperType,
    String instrumentType,
    String term,
    BigDecimal quantity,
    BigDecimal locked,
    BigDecimal balancePrice,
    BigDecimal currentPrice,
    BigDecimal balanceValue,
    BigDecimal balanceValueRub,
    BigDecimal balanceValueUsd,
    BigDecimal balanceValueEur,
    BigDecimal currentValue,
    BigDecimal currentValueRub,
    BigDecimal currentValueUsd,
    BigDecimal currentValueEur,
    BigDecimal unrealizedPL,
    BigDecimal unrealizedPercentPL,
    BigDecimal dailyPL,
    BigDecimal dailyPercentPL,
    BigDecimal portfolioShare,
    Integer scale,
    BigDecimal minimumStep,
    String board,
    String priceUnit,
    BigDecimal faceValue,
    BigDecimal accruedIncome,
    String logoLink,
    Boolean isBlocked,
    Boolean isBlockedTradeAccount,
    BigDecimal lockedForFutures,
    BigDecimal ratioQuantity,
    String expireDate) {}
