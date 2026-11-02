# Отчёт по этапу 3: Домен + виртуальный портфель + персистентность

Дата: 2026-11-02. Ветка: `feature/stage-3-persistence`. Бриф:
`docs/agent-briefs/stage-3-portfolio.md`.

## Итог

Настоящий виртуальный портфель вместо заглушек этапа 2: кэш и блокировки, позиции акций и
фьючерсов, ГО и вариационная маржа, комиссии, ежедневный NAV. Состояние живёт в PostgreSQL 18
(миграция Flyway `V1__init.sql`, 7 таблиц) и переживает перезапуск приложения. Портфель и лимиты
БКС-эндпоинтов собираются из реального `PortfolioSnapshot` через `bks/adapter`; HTTP-контракт
этапа 2 не менялся.

Проверка: `./gradlew clean spotlessApply build` — BUILD SUCCESSFUL. Итог гейта — в разделе
«Тесты и покрытие». Коммит не выполнялся: он ожидает явного решения владельца по таблице
«Решения владельца, ожидающие подтверждения».

## Шаги этапа

| Шаг | Содержимое | Результат |
|---|---|---|
| S3-0 | Зависимости Testcontainers/PostgreSQL, DataSource, Flyway, smoke-IT подключения к БД | выполнено |
| S3-1a | `core/portfolio`: `VirtualPortfolio` (кэш RUB, позиции, средняя цена, блокировки), `Fill` | выполнено, 21 тест слайса |
| S3-1b | `core/portfolio`: `MarginCalculator`, `MarginRates`, `MarginCheck`, `VariationMarginCalculator`, `NavCalculator`, `NavSnapshot` | выполнено, 55 тестов слайса |
| S3-2 | `core/commission`: `CommissionCalculator`, `CommissionTariff`, `CommissionTariffRegistry` | выполнено, 20 тестов слайса |
| S3-3 | `infra/model` + `infra/repository`, миграция `V1__init.sql` (7 таблиц) | выполнено |
| S3-4 | `infra/mapper` (MapStruct): сущность ↔ домен core | выполнено |
| S3-5 | `infra/services`: `AccountService`, `JpaOrderServiceImpl`, `FillService`, `PortfolioService`, `MarginSettlementService`, `OrderLocksCalculator`, заглушка `CorporateActionHandler` | выполнено |
| S3-6 | `bks/adapter`: портфель и лимиты из реального `PortfolioSnapshot`, контракт этапа 2 заморожен | выполнено |
| S3-7 | Сквозной IT `TradingFlowIntegrationIT`, IT перезапуска `RestartIntegrationIT`, 9 правил ArchUnit | выполнено, 418 тестов в прогоне |
| S3-8 | DoD: README, `docker-compose.yml`, `docs/progress.md`, `docs/ARCHITECTURE.md` §9, отчёт, очистка тестовой обвязки | выполнено в этой задаче |

## Схема БД (миграция V1)

Ровно семь таблиц; деньги — `NUMERIC(19,2)`, цены и средние цены — `NUMERIC(19,6)`, время —
только `TIMESTAMP WITH TIME ZONE`.

| Таблица | Ключевые колонки и ограничения |
|---|---|
| `accounts` | `account_id VARCHAR(64) PK`, `created_at NOT NULL DEFAULT now()` |
| `account_balances` | `id BIGSERIAL PK`, `account_id → accounts`, `currency VARCHAR(8)`, `cash/locked NUMERIC(19,2) NOT NULL DEFAULT 0`, `updated_at`; `UNIQUE (account_id, currency)` |
| `instruments` | `id BIGSERIAL PK`, `ticker`, `class_code`, `type`, `lot_size/minimum_step NUMERIC(19,6)`, `currency`; `UNIQUE (ticker, class_code)` |
| `orders` | `order_id VARCHAR(64) PK`, `order_num BIGINT UNIQUE`, `client_order_id UUID UNIQUE`, `account_id → accounts`, `instrument_id → instruments`, `side/order_type/time_in_force/status`, `quantity BIGINT`, `price NUMERIC(19,6)`, `executed_quantity`, `average_price`, `created_at/updated_at` |
| `fills` | `id BIGSERIAL PK`, `order_id → orders`, `account_id → accounts`, `instrument_id → instruments`, `price NUMERIC(19,6)`, `quantity BIGINT`, `commission NUMERIC(19,2)`, `executed_at` |
| `positions` | `id BIGSERIAL PK`, `account_id → accounts`, `instrument_id → instruments`, `quantity BIGINT` (short — отрицательное), `average_price NUMERIC(19,6)`, `updated_at`; `UNIQUE (account_id, instrument_id)`, `CHECK (quantity <> 0)`, `CHECK (average_price >= 0)` |
| `nav_daily` | `id BIGSERIAL PK`, `account_id → accounts`, `nav_date DATE`, `as_of`, `cash/commission/variation_margin/market_value/nav NUMERIC(19,2)`, `holdings VARCHAR(4000) NOT NULL DEFAULT '[]'` (JSON-массив `NavSnapshot.Holding`); `UNIQUE (account_id, nav_date)` |

Отложено на следующие миграции: `order_events` (лог статусов), `market_data_snapshots` (запись
RECORD), `strategy_tags`, `backtest_results`. Черновик `positions_daily` заменён таблицей
`positions` (текущее состояние, а не дневной срез). Индексы на внешних ключах не созданы —
см. решение владельца №8.

## Модель

- **Блокировки активных заявок** (`OrderLocksCalculator`, единая формула для портфеля и
  исполнения): акции BUY с ценой — `цена × остаток`; акции SELL и рыночная BUY без цены —
  ничего; фьючерсы любой стороны — ГО `|остаток| × опорная цена × 10 %`, опорная цена — цена
  лимитной заявки, для рыночной — последняя цена `MarketDataProvider`. Исполняемая заявка
  исключает собственную блокировку из расчёта доступных средств.
- **ГО позиций** — `|количество| × средняя цена × 10 %` (`MarginCalculator`, ставка
  настраивается per-instrument). При сделке блокировка меняется на дельту ГО: рост
  блокируется, снижение освобождается; при клиринге ГО пересчитывается от расчётной цены.
- **Кэш и баланс**: акции — покупка списывает `цена × количество + комиссия`, продажа
  зачисляет `цена × количество − комиссия`, шорт акций запрещён. Фьючерсы — стоимость
  контракта не списывается, кэш меняется только на сбор `1.00 RUB × количество`; короткая
  позиция разрешена. `account_balances.locked` — ГО открытых позиций; блокировки отдельных
  заявок динамические и в БД не пишутся.
- **Клиринг ВМ** (`MarginSettlementServiceImpl`): ВМ = `количество × (расчётная цена − средняя
  цена)` для фьючерсной позиции начисляется в кэш; убыток фиксируется, даже если кэш становится
  отрицательным. После начисления средняя цена позиции становится расчётной, ГО пересчитывается
  от неё. ВМ и закрытие дня идут в одной транзакции под блокировкой счёта.
- **NAV** = баланс + рыночная стоимость акций; комиссия дня и ВМ входят в срез отдельными
  полями. Фьючерсы в рыночную стоимость не входят — их результат уже в кэше через ВМ.
  `nav_daily` уникален по паре (счёт, дата); повторное закрытие дня — `ConflictException`.

## Тесты и покрытие

- Итог гейта: **418 тестов, 0 падений, 0 ошибок, 0 пропусков** (сумма по
  `build/test-results/test/*.xml`); `spotlessCheck` и `jacocoTestCoverageVerification` зелёные.
- Покрытие строк по JaCoCo (пересчёт по `build/reports/jacoco/test/jacocoTestReport.xml`):
  `core/portfolio` — 92.9 %, `core/commission` — 94.1 %, `core/domain` — 94.5 %,
  `infra/services` — 92.9 %, `infra/mapper` — 94.6 %, `bks/adapter` — 95.0 %, `bks/api` — 94.9 %.
- Интеграция с БД (Testcontainers, PostgreSQL 18):
  `TradingFlowIntegrationIT` — заявка по HTTP → сделка через `FillService` → портфель и лимиты
  по HTTP отражают кэш, позицию и ГО; `RestartIntegrationIT` — контекст поднимается дважды в
  веб-окружении на случайном порту (`server.port=0`, как у BKS-контекстов), заявки, сделки,
  позиции, баланс, NAV и нумерация заявок восстанавливаются из PostgreSQL. Коммитящие IT удаляют созданный ими инструмент
  `SiU6/SPBFUT`, чтобы не влиять на `JpaOrderServiceImplIT`.
- ArchUnit (`ArchitectureTest`, 9 тестов): core без Spring и внешних слоёв; infra без `bks`;
  `bks/contract` — чистые DTO; `@Transactional` только в `infra/services`; `@Entity` только в
  `infra/model`; Spring Data-репозитории только в `infra/repository`; запрет `System.out`/
  `System.err`; запрет `java.util.Date`/`Calendar`; core не читает системные часы.

## DoD этапа 3

| Пункт брифа | Статус | Подтверждение |
|---|---|---|
| `./gradlew clean build` зелёный | выполнено | `BUILD SUCCESSFUL`; 418 тестов, 0 падений |
| Портфель корректно считается (тесты) | выполнено | `VirtualPortfolioTest`, `FillServiceIT`, `MarginSettlementIT`, `JpaPortfolioServiceImplIT` |
| Состояние восстанавливается после рестарта (IT с БД) | выполнено | `RestartIntegrationIT` |
| В README добавлен раздел конфигурации БД (env, docker-compose PG 18) | выполнено | `README.md`, раздел «Конфигурация базы данных»; `docker-compose.yml` |
| Обязательные тесты брифа: акции (кэш, позиция, средняя цена, комиссия) | выполнено | `FillServiceIT`, `VirtualPortfolioTest` |
| Обязательные тесты брифа: фьючерсы (ГО, ВМ, отказ при нехватке обеспечения) | отклонение | `FillServiceIT`, `MarginSettlementIT`; отказ реализован как проверка перед сделкой, автоматический margin call не моделируется (см. ограничения) |
| Обязательные тесты брифа: NAV и комиссионный сценарий | выполнено | `MarginSettlementIT`, `NavCalculatorTest` |
| Обязательные тесты брифа: репозитории на PostgreSQL 18 | выполнено | `*RepositoryIT`, `DatabaseSchemaIT` (синглтон Testcontainers, см. отклонения) |
| Обязательные тесты брифа: сквозной сценарий HTTP | выполнено | `TradingFlowIntegrationIT` |

## Отклонения от брифа и AGENTS

1. **Testcontainers — синглтон на JVM вместо `@Testcontainers`/`@Container`** (AGENTS §5).
   Причина: per-class жизненный цикл останавливает контейнер между классами, а Spring кэширует
   контекст и сохраняет DataSource на уже остановленный порт. Контейнер стартует в статическом
   блоке `PostgresContainerSupport` и живёт до конца JVM. Зафиксировано в
   `docs/temporary-decisions.md`.
2. **S3-5d: красный прогон не снимался.** Процессное отклонение от TDD; поведение покрыто
   тестами `FillServiceIT`, но лог red-фазы отсутствует. Зафиксировано как процессный долг.
3. **`BksApiTestSupport` и `ApiResponse` были открыты до `public` в S3-7**, чтобы сквозной
   сценарий из `infra/services` видел HTTP-поддержку. В S3-8 `TradingFlowIntegrationIT`
   перенесён в `bks/api`, видимость возвращена: класс package-private, `ApiResponse` —
   `protected record`.
4. **Fallback создания счёта в снапшоте**: если строки баланса ещё нет, `JpaPortfolioServiceImpl`
   вызывает `AccountService.ensureAccount()` и перечитывает баланс. Штатно строку создаёт
   `AccountSeedRunner`; fallback страхует тестовые и нестандартные контексты.

## Известные ограничения

- T+1 поставка не моделируется: расчёты — T0 (допущение брифа, зафиксировано в коде).
- Матчинг и проскальзывание отсутствуют — этап 5; сделка идёт по последней цене заглушки
  `MarketDataProvider`.
- `FillService` доступен только из тестов: HTTP-пути исполнения заявок нет, контракт БКС не
  расширялся.
- Мультивалютность: структура кэша по валютам есть, но реально поддерживается только RUB;
  конвертация — TODO.
- Вариационная маржа без margin call и авто-ликвидации: убыток может увести кэш в минус.
- Постановка заявки не проверяет достаточность средств (решение владельца); проверка — при
  исполнении в `FillServiceImpl`.
- БКС-адаптер: `cbplUsed` — сумма ГО заявок и позиций; `moneyLimits.locked` включает ГО;
  `cbpLimit` и `varMargin` остаются пустыми.
- Комиссия фьючерса — только фиксированный сбор за контракт, без отдельного НДС и биржевого
  сбора сверх него; минимальная комиссия акций — 0.01 RUB (допущение).

## Решения владельца, ожидающие подтверждения

Все 14 пунктов подтверждены владельцем 2026-11-02 («ок» по дефолтам); по №14 владелец
дополнительно попросил перевод на `Clock` — выполнено (см. `BksErrorFactory` и
`BksErrorFactoryTest`).

| № | Вопрос | Принятый по умолчанию вариант | Что меняется при другом ответе |
|---|---|---|---|
| 1 | ГО позиции до клиринга | от средней цены | пересчёт от последней/расчётной цены на каждой сделке |
| 2 | Тарифы комиссий | акции 0.1 % с минимумом 0.01 RUB; фьючерс 1.00 RUB за контракт | правки `CommissionTariff` и пересчёт тестовых ожиданий |
| 3 | Кэш в NAV | до вычета комиссии (комиссия отдельным полем) | изменение формулы `NavCalculator` и `nav_daily.cash` |
| 4 | День комиссии в NAV | календарный день UTC | переход на московское время/торговый день |
| 5 | Отрицательный кэш после ВМ | фиксируется, margin call не моделируется | флаг дефицита или авто-ликвидация |
| 6 | Проверка средств при постановке заявки | не проверяется, только при исполнении | отказ на create/cancel и новые тесты API |
| 7 | `nav_daily.holdings` | `VARCHAR(4000)` с JSON-массивом | JSONB и миграция `V2` |
| 8 | Индексы на внешних ключах и `fills.executed_at`/`orders.status` | не созданы | миграция с индексами и замер планов |
| 9 | Порог JaCoCo | 60 % (временное решение №9) | возврат к 80 % по брифу и учёт IT в покрытии |
| 10 | Testcontainers | синглтон на JVM | `@Testcontainers` с пересмотром кэша контекста |
| 11 | `moneyLimits.locked` | включает ГО (`lockedCash`) | показывать только денежные блокировки заявок |
| 12 | `cbplUsed` | сумма ГО заявок и позиций | только ГО позиций |
| 13 | `cbpLimit` и `varMargin` | оставлены пустыми | хранить лимит ГО и ВМ в снапшоте |
| 14 | `BksErrorFactory` | перевод на инъектируемый `Clock` (выполнено) | — |

## Следующие шаги

1. Владелец подтверждает или меняет решения из таблицы; только после этого — коммит этапа 3
   (явное «да»).
2. Очистка worktree `s3-portfolio-core`, `s3-margin-nav`, `s3-commission` (force — отдельное
   подтверждение).
3. Этап 4 (рыночные данные LIVE/RECORD/REPLAY) — в отдельном окне после коммита этапа 3.
4. Этап 5 (модель исполнения) — после этапа 4: ему нужны стакан, сделки, ADV и виртуальный
   `Clock`.
