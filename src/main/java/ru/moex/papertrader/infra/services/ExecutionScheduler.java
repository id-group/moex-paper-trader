package ru.moex.papertrader.infra.services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Планировщик исполнения: на каждом тике снимает истёкшие DAY/GTD и пытается исполнить активные
 * заявки. В REPLAY тик использует виртуальные часы, поэтому одинаковый таймлайн даёт одинаковые
 * сделки.
 *
 * <p>Отключается настройкой {@code emulator.execution.scheduler-enabled=false} (тесты и профили без
 * фонового исполнения).
 */
@Component
@ConditionalOnProperty(
    prefix = "emulator.execution",
    name = "scheduler-enabled",
    havingValue = "true",
    matchIfMissing = true)
public class ExecutionScheduler {

  private static final Logger log = LoggerFactory.getLogger(ExecutionScheduler.class);

  private final ExecutionService executionService;

  public ExecutionScheduler(ExecutionService executionService) {
    this.executionService = executionService;
  }

  @Scheduled(fixedDelayString = "${emulator.execution.tick-ms:1000}")
  public void tick() {
    try {
      int processed = executionService.tick();
      if (processed > 0) {
        log.debug("Проход исполнения обработал заявок: {}", processed);
      }
    } catch (RuntimeException exception) {
      // Планировщик не должен умирать из-за одной ошибки: следующий тик повторит попытку.
      log.warn("Проход исполнения завершился ошибкой: {}", exception.getMessage());
    }
  }
}
