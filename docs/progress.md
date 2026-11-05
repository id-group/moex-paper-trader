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

- **Этап 7 завершён — сквозной прогон и сверка с бэктестом** (2026-11-05; ветка
  `feature/stage-7-integration`; бриф `docs/agent-briefs/stage-7-integration.md`, отчёт
  `docs/agent-briefs/stage-7-report.md`).
  - `it/FullCycleReplayIntegrationTest`: детерминированный REPLAY-набор (24 торговых дня,
    SBER/GAZP/SiU6) → HTTP-контракт БКС (token → orders/sync → portfolio → limits →
    orders/search → trades/search) → матчинг → NAV по дням → `/api/internal/reports` → сверка
    с эталоном `BacktestComparator` (порог 30%).
  - `it/replay`: `ReplayDataset` (детерминированный генератор, эквивалент записи сессии),
    `SmaCrossoverStrategy` (SMA 16/64, long-only + один фьючерсный round trip),
    `IdealBacktest` (эталон по ценам стакана без проскальзывания и комиссий).
  - `ReplaySeedSupport`: идемпотентный сидинг инструментов, календаря и снапшотов через
    `BeanPostProcessor` до создания `MarketDataProvider` (таймлайн REPLAY читается на старте).
  - Контрольный прогон: максимум расхождения 18.53% (Sortino), `passed = true`; издержки
    эмулятора ≈0.78% NAV (проскальзывание 5 694.96 RUB, комиссия 2 111.36 RUB).
  - Исправлен разрыв контракта: `POST /v1/trades/search` теперь читает `fills` через
    `JpaTradeServiceImpl` (раньше in-memory хранилище никто не наполнял).
  - `it/LiveSmokeIntegrationTest` (тег `live`): ручной smoke против AlgoPack/ISS, по умолчанию
    пропускается без `ALGO_PACK_TOKEN`.
  - Проверка: `./gradlew clean build` зелёный; 687 тестов, 93 сюиты, 0 падений, 1 пропуск;
    JaCoCo bundle 93.3% инструкций / 77.5% ветвей; `spotlessCheck` зелёный.
  - Решения: эталон считается из того же набора без издержек (внешнего эталона нет); набор
    синтетический и детерминированный; отдельная Gradle-задача `integrationTest` не вводилась
    (`build.gradle` не менялся).

- Закрыт хвост окружения: `MOEX_JAVA25_HOME` прописан в конфиг фонового сервиса
  OpenCode (`opencode service set env ...`), сервис перезапущен; `.\gradlew.bat --version`
  показывает `Launcher JVM: 25.0.4` без ручного выставления `JAVA_HOME`.

- **Этап 6 завершён — отчётность и метрики** (2026-11-04; коммит `64a2697`, ветка
  `feature/stage-6-reporting`). Отчёт:
  `docs/agent-briefs/stage-6-report.md`, формулы и контракты — `docs/reporting.md`.
  - `core/reporting`: `MetricsCalculator` (CAGR, Sharpe, Sortino, Max Drawdown, Calmar по
    дневному NAV, 252 торговых дня), `TradeJournal` + `FifoMatcher` (win rate, частичные
    закрытия, короткие позиции, комиссии в PnL), `BacktestComparator` (порог 30%).
  - `strategyId`: заголовок `X-Strategy-Id` (расширение контракта БКС), `orders.strategy_id`;
    `fills.reference_price` — цена стакана до проскальзывания.
  - Миграция `V4__reporting.sql`: `reports` и `backtest_results` (payload JSONB), индексы
    `orders(account_id, strategy_id)` и `fills(account_id, executed_at)`.
  - `infra/services`: `ReportServiceImpl` — сборка отчёта из NAV/сделок/заявок, файловый
    экспорт JSON/CSV, необязательная сверка с бэктестом; `ReportProperties`.
  - `reporting/api`: внутренний брокер-нейтральный API `/api/internal/reports/**` без токена
    БКС; ошибки внутреннего API обрабатывает `InternalReportExceptionHandler` с высшим
    приоритетом, `BksExceptionHandler` остаётся глобальным.
  - Проверка: `./gradlew clean build` зелёный; `./gradlew test --rerun-tasks` — 673 теста,
    89 сюит, 0 падений/ошибок/пропусков; JaCoCo bundle 92.9% инструкций / 76.8% ветвей,
    `core.reporting` — 95.3% / 77.5%; REPLAY-сценарий — `ReplayReportIntegrationIT`.
  - Решения: `X-Strategy-Id` как расширение контракта; безрисковая ставка 16% конфигом;
    неопределённые метрики — `null`; период журнала — дни UTC; внутренний API без
    аутентификации (временные решения №24–28).

- **Этап 5 завершён — модель исполнения ордеров** (2026-11-04; коммит `544adfe`,
  ветка `feature/stage-5-execution`). Отчёт: `docs/agent-briefs/stage-5-report.md`.
  - `core/execution`: `MatchingEngine`, `SpecSlippageModel` (база 0.05–0.15 % + тиры ADV),
    `AdvLiquidityPolicy` (REJECT/SPLIT), `MatchFill`/`MatchResult`, события жизненного цикла.
  - Маркет — немедленный кросс стакана с проскальзыванием в неблагоприятную сторону; лимит —
    ожидание пересечения и исполнение по «лимит или лучше»; FOK — all-or-nothing; маркет-остаток
    снимается, лимитный остаётся активным.
  - Жизненный цикл `NEW → PARTIALLY_FILLED → FILLED / CANCELED / REJECTED`, журнал
    `order_events` (миграция V3: `orders.expiry_date`, `order_events`), GTD по `expiryDate`,
    DAY — при закрытии сессии.
  - `infra/services`: `ExecutionServiceImpl`, `OrderPlacementService` (предпроверка ADV и
    best-effort исполнение при постановке), `ExecutionScheduler` (`scheduler-enabled`),
    `ExecutionProperties`; ошибка ликвидности → HTTP 429 `RESOURCE_EXHAUSTED`.
  - BKS: create через `OrderPlacementService`, `expiryDate` в запросе/ответе.
  - Проверка: `./gradlew spotlessCheck test` — 619 тестов, 81 сюита, 0 падений; JaCoCo bundle
    92 % инструкций / 77 % ветвей, `core.execution` — 94 % / 84 %.
  - Решения: REJECT + 429 по умолчанию (SPLIT настройкой); планировщик + попытка при
    постановке; GTD/FOK полностью; orderType 3–5 — заглушка `NOT_IMPLEMENTED`.

- **Этап 4 завершён — рыночные данные LIVE/RECORD/REPLAY** (2026-11-02; коммит
  `7c0b7fd`, решения владельца — `d27d953`). Отчёт: `docs/agent-briefs/stage-4-report.md`.
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
  - Решения владельца (коммит `d27d953`): авторизация AlgoPack — `Authorization: Bearer
    <APIKEY>` (ключ только из env `ALGO_PACK_TOKEN`); M5/M15/M30/H4 не агрегируются (пустая
    серия до тарифа); границы сессии дня — из минутных свечей MOEX ISS (открытие — первый бар
    дня, закрытие — последний бар вечернего фрагмента с 18:00 МСК), настройки
    `session-open`/`session-close` остались fallback.
  - Проверка: `./gradlew test` — 549 тестов (74 сюиты), 0 падений; spotlessCheck зелёный.
  - LIVE-демонстрация: справочник/свечи/календарь — реальные данные ISS (curl-лог в отчёте);
    стакан требует подписки ISS или токена AlgoPack (зафиксировано как ограничение).
  - Отклонения и решения — 12 пунктов в отчёте этапа 4 (endpoint календаря ISS, домен
    iss.moex.com, баг формата from/till свечей и др.); три открытых вопроса закрыты
    решениями владельца (см. выше).
  - Закрыто временное решение №4 (календарь — из данных биржи вместо конфига; отдельного
    trading-schedule у ISS нет — источник: история торгов + минутные свечи); №16 сужено
    до NAV-части (день комиссии NAV остаётся календарным днём UTC).

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
