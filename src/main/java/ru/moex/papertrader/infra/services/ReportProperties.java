package ru.moex.papertrader.infra.services;

import java.math.BigDecimal;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Настройки отчётности.
 *
 * <p>Безрисковая ставка — годовая доля ({@code 0.16} — 16%); по умолчанию константа 16%, пока
 * ключевая ставка ЦБ не вынесена в отдельный источник. Порог сравнения с бэктестом — доля ({@code
 * 0.30} — 30%). Каталог экспорта создаётся при генерации отчёта.
 *
 * @param riskFreeRate годовая безрисковая ставка долей
 * @param backtestThreshold допустимое относительное отклонение от бэктеста
 * @param outputDir каталог файлового экспорта JSON/CSV
 */
@ConfigurationProperties(prefix = "emulator.reporting")
public record ReportProperties(
    BigDecimal riskFreeRate, BigDecimal backtestThreshold, String outputDir) {

  private static final BigDecimal DEFAULT_RISK_FREE_RATE = new BigDecimal("0.16");
  private static final BigDecimal DEFAULT_BACKTEST_THRESHOLD = new BigDecimal("0.30");
  private static final String DEFAULT_OUTPUT_DIR = "build/reports/emulator";

  public ReportProperties {
    riskFreeRate = riskFreeRate == null ? DEFAULT_RISK_FREE_RATE : riskFreeRate;
    backtestThreshold = backtestThreshold == null ? DEFAULT_BACKTEST_THRESHOLD : backtestThreshold;
    outputDir = outputDir == null || outputDir.isBlank() ? DEFAULT_OUTPUT_DIR : outputDir.trim();
  }
}
