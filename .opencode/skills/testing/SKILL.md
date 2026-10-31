---
name: Testing
description: Правила тестирования moex-paper-trader — TDD, JUnit 5 + AssertJ + Mockito, изоляция без Spring Context, покрытие JaCoCo ≥ 60% (временно), ArchUnit, детерминизм, Testcontainers с этапа 3. Загружать перед написанием или изменением тестов и перед задачами TDD.
---

# Тестирование — правила проекта

Источник — `AGENTS.md` §5, §6, §10. Цикл TDD — команда `/tdd`, покрытие — `/coverage`.

## Принципы

- **TDD.** Сначала падающий тест, потом минимальный код, потом рефакторинг. Каждое поведение —
  отдельный тест.
- **Тест падает по нужной причине.** Если падает из-за опечатки или ошибки компиляции — это не
  Red. Исправь тест, а не код.
- **Не удалять и не ослаблять тесты.** Можно добавлять и исправлять (`AGENTS.md` §6).
- **Тесты детерминированы.** Без `Thread.sleep`, без реального времени, без сети, без глобального
  состояния между тестами.

## Инструменты

- JUnit 5 (`org.junit.jupiter.api`), AssertJ (`assertThat`), Mockito — для границ, которые
  нельзя поднять в тесте напрямую.
- Версии приходят из BOM Spring Boot; новые библиотеки для тестов — только по согласованию
  (`AGENTS.md` §6).
- Testcontainers — **с этапа 3** и только после согласования зависимости. До этого не добавлять.

## Изоляция

- Unit-тест не поднимает Spring Context. Если класс можно создать через конструктор — создать.
- Зависимости подставлять через Mockito (`@ExtendWith(MockitoExtension.class)`, `@Mock`).
- Для `core` тесты пишутся без Spring вообще (это ещё и проверяется ArchUnit).
- Время — только через инжектируемый `Clock` (порт из `core/marketdata`, см. skill
  `marketdata-modes`). `Instant.now()` в продакшн-коде не использовать: тесты станут недетерминированными.
- Случайность — через фиксированный seed (см. skill `execution-model`, проскальзывание).

## Структура

- Тесты лежат в `src/test/java` в том же пакете, что и класс, который тестируют.
- Имя — `<Класс>Test`. Для интеграционных тестов с БД — `<Класс>IT` (с этапа 3).
- Один тестовый класс — одно поведение или один публичный API класса.
- Имена методов описывают поведение: `rejectsOrderWhenBalanceIsInsufficient()`, а не `test1()`.
- Arrange / Act / Assert — отделять пустыми строками или явными комментариями при сложной подготовке.

## Assert и Mockito

- Использовать AssertJ: `assertThat(actual).isEqualTo(expected)`, `assertThatThrownBy(...)`.
- Не использовать `assertEquals` из JUnit там, где есть AssertJ.
- Mockito: проверять взаимодействия только там, где они являются поведением (например, вызов
  репозитория при сохранении). Не проверять внутренние вызовы.
- `verifyNoMoreInteractions` — только если это важно для контракта.

## Покрытие

- JaCoCo: порог **60%** по bundle (временно, `docs/temporary-decisions.md` #9; норма — 80%), проверка входит в `check` и `build` (`build.gradle`).
- Точка входа `BrokerEmulatorApplication` исключена из подсчёта.
- Каждый новый Service — минимум 60% unit-тестами (временно; норма 80%, `AGENTS.md` §5).
- Проверка: `/coverage` или `./gradlew test jacocoTestCoverageVerification`. Отчёт: `build/reports/jacoco/test/html/index.html`.
- Порог не снижать без разрешения владельца.

## ArchUnit

- `ArchitectureTest` (пакет `ru.moex.papertrader.arch`) — fitness-тесты архитектуры.
- Новые правила добавлять туда, а не в отдельные ad hoc проверки.
- Правило с пустой выборкой должно быть явно помечено `allowEmptyShould(true)` (как сейчас).

## Команды

Перед запуском Gradle выставить JDK 25:
`$env:JAVA_HOME='C:\Users\id-gr\.jdks\liberica-full-25.0.4'`

- Один класс: `./gradlew test --tests "ru.moex.papertrader.<пакет>.<Класс>Test"`
- Один метод: `./gradlew test --tests "ru.moex.papertrader.<пакет>.<Класс>Test.<метод>"`
- Все тесты: `./gradlew test`
- Покрытие: `./gradlew test jacocoTestCoverageVerification`

## Чек-лист перед завершением задачи

- [ ] Для каждого нового поведения есть тест, который был красным до реализации.
- [ ] Тесты без Spring Context, если это возможно.
- [ ] Нет `Thread.sleep`, `Instant.now()`, реальной сети и файлов вне `build/`.
- [ ] `./gradlew test jacocoTestCoverageVerification` зелёный.
- [ ] ArchUnit-правила не нарушены.
