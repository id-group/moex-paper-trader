# AGENTS.md — Инструкции для AI-агентов

Ты — Senior Java-разработчик. Работай аккуратно, придерживайся строгой типизации и
архитектуры этого проекта. Используй лучшие практики и паттерны разработки.

## 1. Стек и окружение

- **Java:** 25 (toolchain задан в `build.gradle`)
- **Build Tool:** Gradle 9.8.2 — только wrapper `./gradlew` (Maven не используется)
- **Framework:** Spring Boot 4.1.2 (версии зависимостей — в `gradle/libs.versions.toml`)
- **ORM / DB:** Spring Data JPA, PostgreSQL 18, миграции — Flyway (кандидат, подтвердить
  на этапе 3). Подключение БД — этап 3, бриф `stage-3-portfolio`; сейчас сборка и тесты
  поднятой БД не требуют
- **Тесты:** JUnit 5, AssertJ, Mockito (Testcontainers — с этапа 3, добавление по согласованию)
- Дополнительно: Spring Actuator (мониторинг, health — `/actuator/health`), Jackson, Lombok,
  MapStruct, JaCoCo, Spotless + Google Java Format

### Окружение (Windows) — ВАЖНО

- Перед любым запуском Gradle выставлять JDK 25:
  `$env:JAVA_HOME='C:\Users\id-gr\.jdks\liberica-full-25.0.4'`
  (системный JAVA_HOME указывает на JDK 17, а GJF 1.36.1 требует JVM 21+ — без этого
  `spotlessCheck` падает)
- Все исходники и конфиги — UTF-8 **без BOM**

---

## 2. Основные команды

Всегда используй обертку Gradle:

```
./gradlew clean build    # сборка + тесты + проверка стиля
./gradlew bootRun        # запуск сервиса: http://localhost:8080; health: GET /actuator/health
./gradlew test           # все тесты
./gradlew test --tests "ru.moex.papertrader.core.portfolio.VirtualPortfolioTest"  # один тест
./gradlew spotlessApply  # автоформатирование (Google Java Format)
./gradlew spotlessCheck  # проверка стиля
```

---

## 3. Архитектура и структура папок

Один Gradle-проект, разделение — пакетами (модулей нет). Порты и адаптеры (детали и
обоснования — `docs/ARCHITECTURE.md` §2–§3): `core` — чистое доменное ядро, `bks` —
адаптер брокера, `infra` — инфраструктура.

```text
src/
├── main/
│   ├── java/ru/moex/papertrader/   # корень пакета: запуск (Application), Spring-конфигурация
│   │   ├── core/            # доменное ядро — ЧИСТАЯ JAVA, без Spring
│   │   │   ├── domain/      # Instrument, Order, Fill, Position, Portfolio
│   │   │   ├── portfolio/   # кэш, позиции, ГО и вариационная маржа, NAV
│   │   │   ├── execution/   # матчинг ордеров, проскальзывание, ликвидность
│   │   │   ├── marketdata/  # порт MarketDataProvider + порт Clock; LIVE/RECORD/REPLAY
│   │   │   ├── commission/  # модель комиссий
│   │   │   └── reporting/   # кривая NAV, метрики, журнал сделок
│   │   ├── bks/             # адаптер БКС (sibling core; новый брокер = sibling-пакет)
│   │   │   ├── api/         # REST-контроллеры под контракт trade-api.bcs.ru → /api/bks/v1/**
│   │   │   ├── contract/    # DTO — точное зеркало docs/bcs-api
│   │   │   └── adapter/     # маппинг DTO ↔ домен core
│   │   └── infra/
│   │       ├── repository/  # PostgreSQL (Spring Data JPA)
│   │       ├── model/       # JPA-сущности (@Entity)
│   │       ├── mapper/      # MapStruct: сущность ↔ домен core
│   │       ├── services/    # Spring-сервисы оркестрации (интерфейс + *ServiceImpl)
│   │       └── algoapi/     # клиенты AlgoPack / MOEX ISS — реализации core/marketdata
│   └── resources/
│       ├── db/migration/    # SQL-скрипты Flyway (появятся на этапе 3, V1__init.sql)
│       └── application.yml
└── test/
    └── java/ru/moex/papertrader/  # unit и интеграционные тесты
```

### Принципы (инварианты; нумерация фиксирована — на неё ссылаются ArchitectureTest и бриф stage-5; обоснования — ADR в docs/ARCHITECTURE.md)

1. **core не зависит от Spring и ни от чего внешнего.** Зависимости:
   `bks/infra/корневые классы → core`, никогда обратно. Контроль — ArchUnit-тест.
2. **Новый брокер = sibling-пакет** (`tbank/`, `alor/`, ...) со слоями `api/contract/adapter`
   поверх того же core. Ядро при этом не меняется.
3. **API всех подключённых брокеров доступны всегда одновременно** (каждый под своими путями,
   например, БКС — `/api/bks/v1/**`).
4. **Специфика БКС живёт только в `bks/`**: Keycloak-подобная авторизация (refresh→access),
   портфель-массив лимитов в разрезе сроков, ГО фьючерсов. В core — брокер-независимый
   снапшот портфеля. Спецификации — skills `bcs-contract`, `bcs-portfolio`.
5. **Режимы котировок:** LIVE / RECORD / REPLAY, детерминизм обязателен (фиксируемый seed).
   Спецификация — skill `marketdata-modes`.
6. **Модель исполнения — по спецификации, не упрощать без согласия владельца.**
   Полная спецификация — skill `execution-model`.
7. **Комиссии:** по умолчанию 0.1% для акций, ГО ~10% для фьючерсов (настраивается
   per-instrument). Корпоративные события — заглушка с TODO (разрешена брифом этапа 3).

---

## 4. Соглашения по коду (Code Style)

- **DTO:** для передачи данных через REST API используй Java `record` (JPA-сущности
  `@Entity` — обычные классы). DTO БКС в `bks/contract` — точное зеркало схем снапшота.
- **Null-safety:** аннотации `@NonNull` / `@Nullable` (`org.springframework.lang` или
  Lombok); где уместно — `Optional<T>`.
- **Логирование:** SLF4J через Lombok `@Slf4j`. Никогда `System.out.println()`.
- **Транзакции:** `@Transactional` — строго на сервисном слое (`infra/services`), не на
  контроллерах и репозиториях. В `core` Spring-аннотаций нет вовсе (принцип №1).
- **Исключения:** кастомные бизнес-исключения (например, `ResourceNotFoundException`),
  перехватываются глобальным `@RestControllerAdvice`.
- **Именование:** контроллеры `*Controller`, сервисы `*Service` / `*ServiceImpl`,
  DTO `*Request` / `*Response`, тесты `*Test`.
- **Формат:** Google Java Format через Spotless (`./gradlew spotlessApply` перед коммитом).
  Версия GJF жёстко связана с версией Spotless (текущая пара: 8.10.4 + GJF 1.36.1;
  GJF 1.37.0 несовместима — `NoSuchMethodError: Style.valueOf`): перед обновлением любой
  из версий проверять `spotlessApply`
- **Мапперы:** MapStruct (если целесообразно)
- Комментарии на русском языке (кроме Javadoc)

---

## 5. Правила написания тестов

- Unit-тесты — JUnit 5 + Mockito. Не поднимай Spring Context там, где класс можно
  протестировать в изоляции.
- Интеграционные тесты с базой данных — `@Testcontainers` (с этапа 3; подключение
  зависимости — по согласованию).
- Каждый новый Service — минимум 80% покрытия unit-тестами.

---

## 6. Запреты (CRITICAL)

- ❌ Никогда не коммитить `.env`, `.env.local`, `*.key`, `*.pem`
- ❌ Не изменять `build.gradle`, `settings.gradle` без явного разрешения (в т.ч. новые
  библиотеки — только по согласованию)
- ❌ Не менять DTO в `ru.moex.papertrader.bks.contract` без согласования (зеркалят контракт БКС)
- ❌ Не редактировать `docs/bcs-api/*.md` — снапшот заморожен (разрешено только добавлять
  недостающие sidecar-`*.json` рядом со страницами)
- ❌ Не импортировать Spring / `bks` / `infra` в пакетах `core` (принцип №1, ArchUnit)
- ❌ Не удалять тесты — только добавлять или исправлять
- ❌ Не коммитить заглушки вместо реальной логики, КРОМЕ случаев, явно разрешённых брифом
  (пример: `CorporateActionHandler` — этап 3) или `docs/temporary-decisions.md`
- ❌ Не изменять `src/main/resources/application-prod.yml` и `SecurityConfig`, если тебя
  об этом прямо не попросили
- ❌ Устаревшие API не использовать (`java.util.Date` → только `java.time.*`)

---

## 7. Что агент ДОЛЖЕН делать

- Писать тесты для всей новой бизнес-логики (минимум 80% покрытия)
- Перед коммитом проходить гейт `/precommit` (spotlessCheck + `./gradlew test` + чистота git)
- Загружать профильный skill перед задачей в соответствующей области (см. §9)
- Обновлять документацию (этот файл, README.md, `docs/`) при изменении архитектуры
- Использовать существующие интерфейсы и не создавать дубликаты функционала

## 8. Ветки

- `main` — стабильная версия, защищена от прямых коммитов
- `feature/*` — новые функции (например, `feature/market-data-feed`)
- `fix/*` — исправления ошибок (например, `fix/commission-calculation`)

## 9. Инструменты агента

### Skills (`.opencode/skills/`)

Перед работой в соответствующей области загрузить профильный skill (tool `skill`):

- `bcs-contract` — DTO/контроллеры БКС, снапшот `docs/bcs-api`, авторизация, монтаж путей
- `bcs-portfolio` — виртуальный портфель, лимиты БКС, ГО, вариационная маржа, NAV, комиссии
- `marketdata-modes` — LIVE/RECORD/REPLAY, `Clock`, seed, AlgoPack/MOEX ISS
- `execution-model` — матчинг, проскальзывание, ADV-тиры, жизненный цикл ордеров
- `java-conventions` — соглашения по коду Java: record-DTO, null-safety, SLF4J, слои транзакций и исключений
- `testing` — TDD, JUnit 5 / AssertJ / Mockito, изоляция, покрытие ≥ 80%, ArchUnit

### Команды (`.opencode/commands/`)

- `/precommit` — гейт перед коммитом: spotlessCheck + тесты + покрытие ≥ 80% + чистота git + отчёт;
  запускать перед каждым коммитом
- `/tdd <задача>` — цикл red → green → refactor (разработка по TDD: тест раньше кода)
- `/coverage [пакет]` — прогон JaCoCo и список классов ниже порога 80%
- `/finish-stage N` — закрытие этапа: DoD, покрытие, обновление `docs/progress.md`

### Context7 (MCP)

Документация библиотек (Spring Boot 4.x, MapStruct, Spotless и т.п.): сначала
`resolve-library-id`, затем `query-docs`. Предпочтительнее веб-поиска — данные в обучающих
материалах модели могут отставать от текущих версий.

## 10. Чек-лист завершения задачи (Definition of Done)

- [ ] `./gradlew spotlessCheck` зелёный (после правок Java)
- [ ] `./gradlew test` зелёный, покрытие новой логики ≥ 80%
- [ ] Гейт `/precommit` пройден; коммит — только с явного подтверждения пользователя
- [ ] Обновлены `docs/progress.md` (статус), при изменении архитектуры —
      `docs/ARCHITECTURE.md` и README.md
- [ ] Загружен профильный skill, если задача в его области

## 11. Документация

- ADR — [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md); этапы — [`docs/roadmap.md`](docs/roadmap.md)
- Статус и прогресс (обновлять по завершении каждого этапа) — [`docs/progress.md`](docs/progress.md);
  текущий план — [`docs/plan.md`](docs/plan.md)
- Временные решения и условия их пересмотра — [`docs/temporary-decisions.md`](docs/temporary-decisions.md)
- Брифы этапов 2–7 — [`docs/agent-briefs/`](docs/agent-briefs/); снапшот контракта БКС —
  [`docs/bcs-api/`](docs/bcs-api/)
