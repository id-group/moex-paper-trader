# Прогресс — статус реализации

Обновлять по завершении каждого этапа и промежуточных результатов. Порядок этапов — в
`docs/roadmap.md`, текущий план — в `docs/plan.md`, временные решения — в
`docs/temporary-decisions.md`.

## Октябрь 2026

- **Этап 1 завершён**: скелет single-project — Spring Boot приложение, ArchUnit-тесты
  границ слоёв, Spotless; `./gradlew clean build` зелёный.
- Контракт БКС: снапшот `docs/bcs-api/` полный — 45 страниц + 43 sidecar-схемы.
- Рефакторинг структуры: классы переведены из пакета `app/` в корневой
  (`ru.moex.papertrader.BrokerEmulatorApplication`); `HealthController` удалён —
  health через Spring Actuator, зависимость `spring-boot-starter-actuator` добавлена
  в `build.gradle` (`/actuator/health`).
- Инструкции для агентов: доменные спецификации вынесены из `AGENTS.md` в skills
  (`bcs-contract`, `bcs-portfolio`, `marketdata-modes`, `execution-model`); добавлена
  команда `/precommit`; документация проекта консолидирована в `docs/`.

- **Модуль 2 (ДЗ OTUS), окружение агента**: GSD Core 1.19.0 (профиль `standard`); команды `/checkpoint`, `/resume`, `/status`; хук JDK 25 для Gradle; сжатие контекста; чек-лист безопасности; discovery в `.planning/`. Память (claude-mem 15.7.0, OpenCode Go, регион Global) работает: наблюдатель пишет, поиск отвечает (проверено 2026-10-31, 16:54); телеметрия claude-mem отключена; см. README.

## Ноябрь 2026

- Закрыт хвост окружения: `MOEX_JAVA25_HOME` прописан в конфиг фонового сервиса
  OpenCode (`opencode service set env ...`), сервис перезапущен; `.\gradlew.bat --version`
  показывает `Launcher JVM: 25.0.4` без ручного выставления `JAVA_HOME`.

- **Этап 4 завершён — рыночные данные LIVE/RECORD/REPLAY** (2026-11-02; коммит
  `TODO`). Отчёт: `docs/agent-briefs/stage-4-report.md`.
  - Контрактное ядро: порты `Clock` (`SystemClock`/`VirtualClock`), `TradingCalendar`,
    снапшоты `MarketDataSnapshot` (QUOTE/ORDER_BOOK/TRADE), `Trade`/`TradingDay`, методы
    `trades`/`adv`/`tradingDays` в `MarketDataProvider`.
  - `core/marketdata`: `VirtualClock`, `RecordingMarketDataProvider` (RECORD),
    `ReplayMarketDataProvider` (REPLAY, детерминизм, свечи из сделок, ADV).
  - `infra/algoapi`: клиенты AlgoPack/MOEX ISS (ретраи/таймауты/кэш справочника), провайдеры
    LIVE с деградацией AlgoPack→ISS.
  - `infra`: миграция V2 (`market_data_snapshots` JSONB, `trading_calendar`), сущности/
    репозитории/мапперы, `MarketDataSnapshotStore`, `TradingCalendarService` (кэш в БД,
    источник — история торгов ISS).
  - Режимы `emulator.marketdata.mode` (STUB/LIVE/RECORD/REPLAY) в `MarketDataConfiguration`;
    `ReplayClockAdvancer` продвигает виртуальный таймлайн; миграция всех потребителей времени
    на порт `Clock`; ArchUnit — 10 правил (системные часы читает только infra).
  - Проверка: `./gradlew test` — 548 тестов (74 сюиты), 0 падений; spotlessCheck зелёный.
  - LIVE-демонстрация: справочник/свечи/календарь — реальные данные ISS (curl-лог в отчёте);
    стакан требует подписки ISS или токена AlgoPack (зафиксировано как ограничение).
  - Отклонения и решения — 12 пунктов в отчёте этапа 4 (endpoint календаря ISS, домен
    iss.moex.com, окно сессии из конфига, баг формата from/till свечей и др.).

- **Этап 2 завершён — Контракт БКС (HTTP MVP)** (2026-11-01; закрыт после ревью —
  2026-11-02). Отчёт: `docs/agent-briefs/stage-2-report.md`.
  - `core/domain` + `core/marketdata`: `Order`/`Instrument`/`PortfolioSnapshot`, статусы,
    порт `MarketDataProvider` — чистая Java без Spring.
  - `bks/contract`: DTO — покамерное зеркало схем снапшота (`ErrorResponse`, токены,
    заявки, сделки, котировки/стакан/свечи, справочник, портфель/лимиты).
  - `bks/api`: контроллеры под `/api/bks` (token, portfolio, limits, orders create/cancel/
    status/search sync+async, trades/search, quotes, order-book, candles-chart,
    instruments/by-tickers, trading-schedule/status), Bearer-фильтр с правами trade/read-only,
    `@RestControllerAdvice` → `ErrorResponse`.
  - `bks/adapter`: обмен refresh→access (TTL 24 ч, refresh 90 дней), маппинг DTO ↔ core.
  - `infra/services`: in-memory заявки/сделки, портфель (кэш + блокировки под заявки),
    конфигурируемая заглушка рыночных данных.
  - Проверка: `./gradlew clean build` зелёный; 124 теста; JaCoCo bundle 92.9% инструкций /
    94.0% строк; сквозной сценарий `bootRun` token → create → status → portfolio → cancel
    пройден (транскрипт в отчёте).
  - Исправления после ревью (2026-11-02): `17e043c` — 400 `invalid_grant` при пустом
    `refresh_token`, восстановлена кодировка `BksTradeMapper`; `33c21b4` — очистка
    просроченных access-токенов в `BksTokenService`. Спорные места контракта зафиксированы
    временными решениями №10–12 (auth-first 401/404, дубль `clientOrderId` → 422
    `VALIDATION_ERROR`, неподдержанные `orderTypes` в search → пустая выборка).
  - Закрыты временные решения №1 (Actuator: только `/actuator/health`) и №7 (префикс
    `emulator.brokers.bks.base-path`, по умолчанию `/api/bks`). Решение №2 (in-memory)
    остаётся в силе до этапа 3.

- **Этап 3 завершён — домен + виртуальный портфель + PostgreSQL** (2026-11-02;
  коммит `68c4c9c`). Отчёт: `docs/agent-briefs/stage-3-report.md`.
  - S3-0: зависимости Testcontainers/PostgreSQL 18, DataSource, Flyway, smoke-IT подключения.
  - S3-1a: `core/portfolio` — `VirtualPortfolio` (кэш RUB, позиции, средняя цена, блокировки),
    `Fill`.
  - S3-1b: `core/portfolio` — ГО, `MarginCheck`, вариационная маржа, `NavCalculator`/`NavSnapshot`.
  - S3-2: `core/commission` — `CommissionCalculator` и тарифы (акции 0.1 %/мин. 0.01 RUB,
    фьючерс 1.00 RUB за контракт).
  - S3-3: `infra/model` + `infra/repository`, миграция `V1__init.sql` (7 таблиц).
  - S3-4: `infra/mapper` — MapStruct «сущность ↔ домен core».
  - S3-5: `infra/services` — счёт, JPA-заявки, `FillService`, портфельный снапшот, клиринг
    ВМ/NAV, блокировки заявок, заглушка корпоративных событий.
  - S3-6: `bks/adapter` — портфель и лимиты из реального `PortfolioSnapshot`; контракт этапа 2
    не менялся.
  - S3-7: `TradingFlowIntegrationIT` (HTTP → сделка → портфель), `RestartIntegrationIT`
    (восстановление из PostgreSQL), ArchUnit — 9 правил.
  - S3-8: DoD — README (раздел БД), `docker-compose.yml` (PostgreSQL 18), `docs/ARCHITECTURE.md`
    §9, этот статус, отчёт этапа, очистка тестовой обвязки.
  - Проверка: `./gradlew clean spotlessApply build` зелёный; 418 тестов, 0 падений; JaCoCo и
    Spotless зелёные (покрытие по пакетам — в отчёте этапа 3).
  - Закрыты временные решения №2 (JPA вместо in-memory) и №3 (Flyway выбран); новые
    ограничения и вопросы владельца — №13–20 и таблица решений в отчёте.
  - Коммит `68c4c9c` выполнен после явного «да» владельца; все 14 решений таблицы
    подтверждены, №14 (BksErrorFactory) дополнительно переведён на `Clock`.

## Открытые хвосты

- Индекс git: устаревшие записи `app/` сняты с индекса (модуль 2). Закрыто.
- Фоновый сервис OpenCode: `MOEX_JAVA25_HOME` в env сервиса. Закрыто 2026-11-01.
