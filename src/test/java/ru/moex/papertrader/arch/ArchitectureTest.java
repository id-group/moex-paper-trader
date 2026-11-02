package ru.moex.papertrader.arch;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noMembers;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaCall;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.library.GeneralCodingRules;
import jakarta.persistence.Entity;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.util.Calendar;
import java.util.Date;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.data.repository.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * Архитектурные fitness-тесты: формально контролируют принципы из AGENTS.md, которые в проекте без
 * Gradle-модулей нельзя выразить модульными границами.
 *
 * <p>Правила-фабрики вынесены в статические методы: временная проверка на классах-нарушителях
 * применяет к ним те же самые правила (см. отчёт S3-7), не дублируя их текст.
 */
class ArchitectureTest {

  private static final JavaClasses CLASSES =
      new ClassFileImporter()
          .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
          .importPackages("ru.moex.papertrader");

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
                "ru.moex.papertrader.infra..");
    rule.check(CLASSES);
  }

  /** Принцип №1 (обратное направление): infra не знает про брокерские адаптеры. */
  @Test
  void infraIsIndependentFromBrokerAdapters() {
    ArchRule rule =
        noClasses()
            .that()
            .resideInAPackage("ru.moex.papertrader.infra..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage("ru.moex.papertrader.bks..")
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

  /** AGENTS §4: @Transactional — строго на сервисном слое infra/services, не на прочих классах. */
  @Test
  void transactionalOnlyInServiceLayer() {
    transactionalMembersRule().check(CLASSES);
    transactionalClassesRule().check(CLASSES);
  }

  /** AGENTS §4: аннотированные методы (включая readOnly-варианты) не выходят за service-слой. */
  static ArchRule transactionalMembersRule() {
    return noMembers()
        .that()
        .areAnnotatedWith(Transactional.class)
        .should()
        .beDeclaredInClassesThat()
        .resideOutsideOfPackage("ru.moex.papertrader.infra.services..");
  }

  /** AGENTS §4: аннотация на уровне класса — тоже только в service-слое. */
  static ArchRule transactionalClassesRule() {
    return noClasses()
        .that()
        .areAnnotatedWith(Transactional.class)
        .should()
        .resideOutsideOfPackage("ru.moex.papertrader.infra.services..")
        // В проекте @Transactional ставится на методы; правило страхует будущий class-level
        // вариант.
        .allowEmptyShould(true);
  }

  /** AGENTS §3: JPA-сущности живут только в infra/model — core остаётся без JPA. */
  @Test
  void entitiesOnlyInInfraModel() {
    entitiesRule().check(CLASSES);
  }

  /** AGENTS §3: @Entity вне infra/model запрещена. */
  static ArchRule entitiesRule() {
    return noClasses()
        .that()
        .areAnnotatedWith(Entity.class)
        .should()
        .resideOutsideOfPackage("ru.moex.papertrader.infra.model..");
  }

  /** AGENTS §3: Spring Data-репозитории — только в infra/repository. */
  @Test
  void springDataRepositoriesOnlyInInfraRepository() {
    repositoriesRule().check(CLASSES);
  }

  /** AGENTS §3: интерфейс, наследующий Spring Data Repository, не выходит за infra/repository. */
  static ArchRule repositoriesRule() {
    return noClasses()
        .that()
        .areInterfaces()
        .and()
        .areAssignableTo(Repository.class)
        .should()
        .resideOutsideOfPackage("ru.moex.papertrader.infra.repository..");
  }

  /** AGENTS §4: логирование только через SLF4J — System.out/System.err запрещены. */
  @Test
  void noClassesAccessStandardStreams() {
    noStandardStreamsRule().check(CLASSES);
  }

  /** Правило библиотеки ArchUnit: обращения к System.out/System.err. */
  static ArchRule noStandardStreamsRule() {
    return GeneralCodingRules.NO_CLASSES_SHOULD_ACCESS_STANDARD_STREAMS;
  }

  /** AGENTS §6: устаревшие java.util.Date/Calendar и их наследники запрещены. */
  @Test
  void noLegacyDateAndCalendarApi() {
    noLegacyDateApiRule().check(CLASSES);
  }

  /** AGENTS §6: зависимость от java.util.Date/Calendar (включая java.sql-наследники). */
  static ArchRule noLegacyDateApiRule() {
    DescribedPredicate<JavaClass> legacyDateApi =
        DescribedPredicate.describe(
            "устаревший java.util.Date/Calendar API",
            type -> type.isAssignableTo(Date.class) || type.isAssignableTo(Calendar.class));
    return noClasses().should().dependOnClassesThat(legacyDateApi);
  }

  /**
   * Принцип №5 и skill testing: core не читает системные часы — время приходит только через порт
   * {@code Clock}, иначе расчёты и тесты становятся недетерминированными.
   */
  @Test
  void coreDoesNotReadSystemClock() {
    coreUsesNoSystemClockRule().check(CLASSES);
  }

  /**
   * ArchUnit проверяет вызовы методов: {@code now()} перечисленных типов и {@code
   * System.currentTimeMillis()}. Проверка статических вызовов в core — этого достаточно: порт
   * {@code Clock} вызывается как {@code clock.instant()}, а не через системные методы.
   */
  static ArchRule coreUsesNoSystemClockRule() {
    DescribedPredicate<JavaCall<?>> systemClockCall =
        DescribedPredicate.describe(
            "вызов системных часов",
            call ->
                (call.getName().equals("now")
                        && SYSTEM_CLOCK_TYPES.contains(call.getTargetOwner().getFullName()))
                    || (call.getTargetOwner().isEquivalentTo(System.class)
                        && call.getName().equals("currentTimeMillis")));
    return noClasses()
        .that()
        .resideInAPackage("ru.moex.papertrader.core..")
        .should()
        .callMethodWhere(systemClockCall);
  }

  /** Типы, чей статический {@code now()} считается чтением системного времени. */
  private static final Set<String> SYSTEM_CLOCK_TYPES =
      Set.of(
          Instant.class.getName(),
          LocalDate.class.getName(),
          LocalDateTime.class.getName(),
          ZonedDateTime.class.getName());
}
