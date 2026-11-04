package ru.moex.papertrader.infra.services;

import ru.moex.papertrader.core.reporting.BacktestComparison;
import ru.moex.papertrader.core.reporting.BacktestInput;

/**
 * Содержимое {@code backtest_results.payload}: исходный бэктест и таблица отклонений.
 *
 * @param input вход внешнего бэктеста
 * @param comparison результат сравнения
 */
public record BacktestResultPayload(BacktestInput input, BacktestComparison comparison) {}
