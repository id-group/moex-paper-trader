package ru.moex.papertrader.infra.services;

import java.math.BigDecimal;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Настройки виртуального портфеля (брокер-независимые).
 *
 * @param allowStockMarginBuy маржинальное плечо на сутки для акционных покупок: при нехватке
 *     свободных средств дебет допускается в пределах стоимости удерживаемых бумаг (модель брокера с
 *     внутридневным плечом, выручка продаж поступает по расчётам T+1); по умолчанию {@code false} —
 *     полный отказ при нехватке средств, как и раньше
 */
@ConfigurationProperties(prefix = "emulator.portfolio")
public record PortfolioProperties(
    String account, BigDecimal initialCash, String currency, boolean allowStockMarginBuy) {}
