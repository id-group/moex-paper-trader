package ru.moex.papertrader.arch;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;

/**
 * Архитектурные fitness-тесты: формально контролируют принципы из AGENTS.md, которые в проекте без
 * Gradle-модулей нельзя выразить модульными границами.
 */
class ArchitectureTest {

  private static final JavaClasses CLASSES =
      new ClassFileImporter().importPackages("ru.moex.papertrader");

  /** Принцип №1: core — чистая Java, без Spring и без зависимостей от внешних слоёв. */
  @Test
  void coreIsIndependentFromSpringAndOuterLayers() {
    ArchRule rule =
        noClasses()
            .that()
            .resideInAPackage("ru.moex.papertrader.core..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(
                "org.springframework..",
                "ru.moex.papertrader", // точное совпадение: запускающие классы корневого пакета
                "ru.moex.papertrader.bks..",
                "ru.moex.papertrader.infra..")
            // Пакеты core пока пусты — правило не должно падать на пустой выборке.
            .allowEmptyShould(true);
    rule.check(CLASSES);
  }

  /** Принцип №4: contract — чистые DTO, не знают ни про Spring, ни про домен core. */
  @Test
  void bksContractIsPureDtoLayer() {
    ArchRule rule =
        noClasses()
            .that()
            .resideInAPackage("ru.moex.papertrader.bks.contract..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(
                "org.springframework..",
                "ru.moex.papertrader.core..",
                "ru.moex.papertrader.bks.api..",
                "ru.moex.papertrader.bks.adapter..")
            .allowEmptyShould(true);
    rule.check(CLASSES);
  }
}
