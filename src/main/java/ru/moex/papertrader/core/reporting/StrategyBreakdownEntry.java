package ru.moex.papertrader.core.reporting;

import java.math.BigDecimal;

/**
 * Агрегат по стратегии.
 *
 * @param strategyId метка роутера; сделки без метки попадают в группу {@code (none)}
 * @param realizedPnl реализованный FIFO-результат закрытых сделок после комиссий
 * @param tradeCount количество сделок журнала с этой меткой
 * @param closedTradeCount количество закрытых FIFO-пар
 * @param winRate доля прибыльных закрытых пар; {@code null}, если закрытых пар нет
 */
public record StrategyBreakdownEntry(
    String strategyId,
    BigDecimal realizedPnl,
    int tradeCount,
    int closedTradeCount,
    BigDecimal winRate) {}
