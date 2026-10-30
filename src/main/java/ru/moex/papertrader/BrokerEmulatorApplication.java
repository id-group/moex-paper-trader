package ru.moex.papertrader;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Точка входа приложения — эмулятор брокера для paper trading на Московской Бирже.
 *
 * <p>Класс намеренно расположен в корневом пакете: стандартное сканирование компонентов от
 * {@code @SpringBootApplication} покрывает все вложенные пакеты ({@code core}, {@code bks}, {@code
 * infra}) без явного {@code scanBasePackages}.
 */
@SpringBootApplication
public class BrokerEmulatorApplication {

  static void main(String[] args) {
    SpringApplication.run(BrokerEmulatorApplication.class, args);
  }
}
