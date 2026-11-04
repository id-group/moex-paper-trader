# AGENTS.md — инструкции AI-агентам

Ты — Senior Java-разработчик. Сохраняй архитектурные инварианты, строго типизируй код и следуй локальному стилю.

## Стек и окружение

- Java 25, Gradle 9.8.2 (только wrapper `./gradlew`), Spring Boot 4.1.2; версии библиотек — в `gradle/libs.versions.toml`.
- Spring Data JPA, PostgreSQL 18; миграции Flyway (кандидат, см. бриф этапа 3). До этапа 3 сборка и тесты не требуют поднятой БД. Actuator health: `/actuator/health`.
- JUnit 5, AssertJ, Mockito; Testcontainers — с этапа 3 и по согласованию. Дополнительно: Jackson, Lombok, MapStruct, JaCoCo, Spotless + Google Java Format.
- Windows: перед Gradle установить JDK 25: `$env:JAVA_HOME=$env:MOEX_JAVA25_HOME`. Если переменная пуста — остановиться и сообщить владельцу. Системный `JAVA_HOME` указывает на JDK 17; GJF 1.36.1 требует JVM 21+.
- Исходники и конфиги — UTF-8 без BOM.

## Команды

Всегда через wrapper: `./gradlew clean build`, `./gradlew test`, `./gradlew spotlessCheck`, `./gradlew spotlessApply`, `./gradlew bootRun`.
Для одной тестовой задачи: `./gradlew test --tests "ru.moex.papertrader.core.portfolio.VirtualPortfolioTest"`.

## Архитектура и инварианты

Один Gradle-проект; пакеты: `core` (чистое доменное ядро), `bks` (адаптер брокера), `infra` (Spring/JPA/API-клиенты). Основные зоны `core`: `domain`, `portfolio`, `execution`, `marketdata`, `commission`, `reporting`; `infra`: `repository`, `model`, `mapper`, `services`, `algoapi`.

1. `core` не зависит от Spring и внешних слоёв; зависимости направлены `bks/infra → core`. Это проверяет ArchUnit.
2. Каждый брокер — sibling-пакет (`bks`, `tbank`, …) со слоями `api/contract/adapter`, поверх общего `core`.
3. API брокеров доступны одновременно под собственными путями (БКС: `/api/bks/v1/**`).
4. Специфика БКС остаётся в `bks`: авторизация refresh→access, лимиты T0/T1/T2/T365 и ГО фьючерсов; `core` хранит брокер-независимый снапшот портфеля. DTO зеркалят замороженный снапшот. См. skills `bcs-contract`, `bcs-portfolio`.
5. Режимы котировок LIVE/RECORD/REPLAY и детерминизм (фиксируемый seed) обязательны. См. `marketdata-modes`.
6. Исполнение ордеров реализуется по полной спецификации, не упрощать без согласия владельца. См. `execution-model`.
7. Комиссии по умолчанию: акции 0.1%, ГО фьючерсов ~10%, настраиваются per-instrument. Corporate actions — заглушка только если это явно разрешено брифом/временным решением.

Подробные ADR: `docs/ARCHITECTURE.md`.

## Код и тесты

- REST DTO — `record`; JPA `@Entity` — обычные классы. DTO в `bks/contract` — точное зеркало контракта.
- Null-safety: `@NonNull`/`@Nullable` (`org.springframework.lang` или Lombok), где уместно — `Optional<T>`.
- Логирование — SLF4J через Lombok `@Slf4j`, не `System.out.println()`.
- `@Transactional` — только сервисный слой `infra/services`; в `core` нет Spring-аннотаций. Бизнес-исключения обрабатывает глобальный `@RestControllerAdvice`.
- Именование: `*Controller`, `*Service`/`*ServiceImpl`, `*Request`/`*Response`, `*Test`. Комментарии по-русски, кроме Javadoc.
- Форматирование — Google Java Format через Spotless. Текущая совместимая пара: Spotless 8.10.4 + GJF 1.36.1; GJF 1.37.0 несовместима (`NoSuchMethodError: Style.valueOf`). Перед обновлением проверять `spotlessApply`.
- Unit-тесты — JUnit 5 + Mockito; не поднимать Spring Context без необходимости. Для новой бизнес-логики и каждого нового сервиса — покрытие минимум 60% (временно; обычная цель 80%, см. `docs/temporary-decisions.md` #9). БД-интеграции — Testcontainers с этапа 3 по согласованию.

## Запреты

- Не коммитить `.env`, `.env.local`, `*.key`, `*.pem`.
- Не менять `build.gradle`/`settings.gradle` и не добавлять библиотеки без явного разрешения.
- Не менять DTO в `ru.moex.papertrader.bks.contract` без согласования; не редактировать `docs/bcs-api/*.md` (разрешено добавлять недостающие sidecar `*.json`).
- Не импортировать Spring, `bks` или `infra` в `core`; не удалять тесты — только добавлять или исправлять.
- Не коммитить заглушки, кроме случаев, прямо разрешённых брифом или `docs/temporary-decisions.md`.
- Не менять `src/main/resources/application-prod.yml` и `SecurityConfig`, если об этом прямо не попросили. Не использовать устаревшие API (`java.util.Date` → `java.time.*`).
- Коммитить только после явного подтверждения пользователя.

## Эффективная работа без потери качества

- Соблюдай также все более локальные `AGENTS.md` в затронутых каталогах.
- Для ясной локальной задачи работай напрямую в текущей сессии; не запускай полный GSD-процесс, исследование или субагентов «на всякий случай».
- Подключай skills только по затронутой области. Читай точечные файлы и нужные разделы документации, а не целые папки/документы.
- Делегируй только независимую существенную работу, когда выигрыш от параллельности оправдывает отдельные контексты. Не запускай дублирующие ревью/исследования.
- Маршрутизация субагентов по модели: read-only ревью/исследование — `worker-free-*` (бесплатно) или `worker-*-flash`; изолированные правки Java/тестов — `worker-qwen-flash`/`worker-deepseek-flash`; архитектура, отладка и нетривиальная бизнес-логика — основная модель (`pro`), на ней не экономить.
- Для итераций запускай релевантный тест; перед завершением — `spotlessCheck` после правок Java и `./gradlew test`. Не пропускай тесты, обязательную проверку или требования к покрытию ради экономии токенов.
- Для небольших исправлений в GSD используй `/gsd-quick`; полные этапные workflow — для действительно этапных задач. У `/gsd-execute-phase` режим `--interactive` дешевле по токенам, когда задачи небольшие или последовательные.

## Навыки, команды и завершение

Загружай профильный skill перед соответствующей работой: `bcs-contract`, `bcs-portfolio`, `marketdata-modes`, `execution-model`, `java-conventions`, `testing`.

- `/precommit` — перед каждым коммитом; делегирует `spotlessCheck` и тесты субагенту `precommit-checker` на дешёвой модели, основная сессия сохраняет краткий отчёт и исправляет найденные проблемы. Покрытие и чистота git обязательны.
- `/tdd <задача>` — red → green → refactor; `/coverage [пакет]` — JaCoCo; `/finish-stage N` — закрытие этапа и обновление прогресса.
- Документация библиотек и платформ: Context7 (`resolve-library-id`, затем `query-docs`), когда задача зависит от их API/версий.
- При изменении архитектуры обновляй `docs/ARCHITECTURE.md` и README. Статус этапов — `docs/progress.md`; планы — `docs/plan.md`, `docs/roadmap.md`, брифы — `docs/agent-briefs/`.
- Перед коммитом выполнить `/precommit`; после завершения сообщить проверки и известные ограничения. Обновляй `docs/progress.md` по завершении этапа.

## Ветки

`main` — стабильная защищённая ветка; новые задачи — `feature/*` или `fix/*`.
