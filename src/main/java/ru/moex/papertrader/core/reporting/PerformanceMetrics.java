package ru.moex.papertrader.core.reporting;

import java.math.BigDecimal;

/**
 * Метрики доходности и риска, рассчитанные по дневному ряду NAV.
 *
 * <p>Все значения — доли, а не проценты: {@code 0.12} означает 12%. {@code null} у коэффициента
 * означает «не определён» (пустой ряд, неположительный NAV, нулевое стандартное отклонение,
 * отсутствие просадки или убыточных дней). Такой результат нельзя путать с нулевой доходностью,
 * поэтому нули не подставляются.
 *
 * @param cagr среднегодовая доходность по 252 торговым дням
 * @param sharpe коэффициент Шарпа на дневных доходностях
 * @param sortino коэффициент Сортино (downside deviation)
 * @param maxDrawdown максимальная просадка от пика, неотрицательная доля
 * @param calmar отношение CAGR к максимальной просадке
 * @param observations количество точек NAV в исходном ряду
 */
public record PerformanceMetrics(
    BigDecimal cagr,
    BigDecimal sharpe,
    BigDecimal sortino,
    BigDecimal maxDrawdown,
    BigDecimal calmar,
    int observations) {}
