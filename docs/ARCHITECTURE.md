# ARCHITECTURE.md — Целевая архитектура moex-paper-trader

Документ фиксирует архитектурные решения (ADR) эмулятора брокера. Нормативные правила для
агентов и стек (включая Actuator, Lombok, MapStruct, JaCoCo) — в `AGENTS.md`, здесь не
дублируются; инварианты-принципы 1–7 — тоже там (AGENTS.md, §3), здесь — только их
обоснования (ADR-2, ADR-3, ADR-5, ADR-8, ADR-9, §8).

Заметки по инструментам стека: Lombok и MapStruct подключаются как annotation processors
(нужны версии с поддержкой JDK 25 и связка `lombok-mapstruct-binding`); JaCoCo контролирует
покрытие бизнес-логики (норма 80% — AGENTS.md); Actuator — мониторинг.

## 1. Контекст и цели

Сервис-«песочница»: принимает запросы в формате API реального брокера (первый — БКС Trade API),
исполняет ордера виртуально по реальным котировкам ММВБ, ведёт портфель с комиссиями и
проскальзыванием, формирует отчётность. Целевой сценарий использования: внешний роутер
алготрейдинг-системы работает с эмулятором так же, как с боевым API брокера, а прогон в
режиме REPLAY сверяется с бэктестом (целевое расхождение метрик < 30%).

Ключевое требование: добавление нового брокера (Т-Банк, Алор, Финам) — дешёвая операция.

**Котировки и информация о торгах:**
AlgoPack (https://moexalgo.github.io/) и MOEX ISS (https://iss.moex.ru).
Контракт брокера: снапшот документации в `docs/bcs-api/` — источник истины для DTO
(правила работы с ним — skill `bcs-contract`).

## 2. Общая схема: порты и адаптеры (гексагональная архитектура)

```
              ┌─────────────────────────────────────────────────┐
  роутер ───► │ bks/api (контроллеры контракта БКС)             │
  (HTTP)      │   └─ bks/contract (DTO) + bks/adapter (маппинг) │
              ├─────────────────────────────────────────────────┤
              │ core (чистая Java):                             │
              │   domain · portfolio · execution · marketdata   │
              │   commission · reporting                        │
              ├─────────────────────────────────────────────────┤
              │ infra: repository · model · dto · mapper ·      │
              │        services (PostgreSQL 18, JPA, MapStruct) │
              │        algoapi (AlgoPack / MOEX ISS)            │
              └─────────────────────────────────────────────────┘
      корневой пакет ru.moex.papertrader — точка сборки (Application, конфигурация)
```

**ADR-1. Один Gradle-проект, слои — пакеты.** Модули не используются (решение владельца);
границы слоёв контролирует ArchUnit-тест `ArchitectureTest`. Обоснование: проще сборка и
навигация, а дисциплину зависимостей обеспечивает тест.

**ADR-2. core не зависит от Spring и внешних слоёв.** Направление зависимостей:
`корневой пакет (запуск), bks, infra → core`. Обоснование: ядро переносимо между брокерами
и тестируется без Spring-контекста.

**ADR-3. Новый брокер = sibling-пакет** `ru.moex.papertrader.<broker>` со слоями
`api/contract/adapter` поверх того же core. API всех брокеров смонтированы одновременно
и доступны всегда, каждый под своим префиксом (БКС — `/api/bks/**`: контрактный путь
`/api/v1/x` монтируется как `/api/bks/v1/x`); рантайм-переключателя брокеров нет (решение
владельца). Профиль брокера (тариф комиссий, параметры проскальзывания, базовый путь) —
конфигурация.

## 3. Структура пакетов

```text
ru.moex.papertrader   # корень: BrokerEmulatorApplication, Spring-конфигурация
├── core/
│   ├── domain/      # Instrument, Order, Fill, Position, PortfolioSnapshot и VO
│   ├── portfolio/   # учёт кэша/позиций, ГО и вариационная маржа, NAV
│   ├── execution/   # матчинг-движок, SlippageModel, LiquidityPolicy
│   ├── marketdata/  # порт MarketDataProvider, порт Clock, режимы LIVE/RECORD/REPLAY
│   ├── commission/  # CommissionCalculator + тарифы
│   └── reporting/   # кривая NAV, метрики, журнал сделок, сравнение с бэктестом
├── bks/        # адаптер БКС: api/ + contract/ + adapter/
└── infra/
    ├── repository/  # Spring Data JPA (PostgreSQL 18)
    ├── model/       # сущности БД
    ├── dto/         # DTO инфраструктурного слоя
    ├── mapper/      # MapStruct: сущности БД ↔ домен core
    ├── services/    # сервисы-оркестраторы: персистентность + вызов чистой логики core
    └── algoapi/     # клиенты AlgoPack / MOEX ISS (реализации core/marketdata)
```

## 4. Доменная модель (core/domain)

| Сущность | Содержание |
|---|---|
| Instrument | тикер, classCode/board (TQBR, FORTS-режимы), тип (STOCK/FUTURES/...), лот, шаг цены, валюта, ISIN |
| Order | clientOrderId (UUID от клиента), биржевой orderId, side, orderType (маркет/лимит/...), timeInForce, qty, price, статус жизненного цикла, `strategyId` (метка роутера для отчётов) |
| Fill | сделка: цена фактическая (после проскальзывания), объём, комиссия (отдельно), время (Clock) |
| Position | инструмент, qty, средняя цена; для фьючерсов — ГО, вариационная маржа |
| PortfolioSnapshot | брокер-независимый срез: кэш по валютам, позиции, блокировки, ГО, NAV |

**ADR-4. Идентификаторы заявок — как у БКС:** клиентский `clientOrderId` (UUID) и биржевой
`ГГММДД-КодКласса-НомерЗаявки`; статус-запрос поддерживает оба (`orderIdType` 1/2).
Обоснование: контракт `docs/bcs-api/http/operations/get-order-by-id.*`.

## 5. Контракт БКС (bks) — состав MVP

Точные пути, параметры и схемы — только из `docs/bcs-api/` (не изобретать!). В таблице —
оригинальные пути контракта; эмулятор монтирует их под префикс брокера (пример для БКС:
`/api/v1/x` → `/api/bks/v1/x`; точный префикс — конфигурация). Обзор:

| Группа | Эндпоинты | Скоуп |
|---|---|---|
| Авторизация | `POST /trade-api-keycloak/realms/tradeapi/protocol/openid-connect/token` (refresh→access 24 ч) | MVP |
| Портфель/лимиты | `GET /api/v1/portfolio`, `GET /api/v1/limits` | MVP |
| Заявки | `POST /api/v1/orders` (+ `/sync`), `/orders/cancel` (+ `/sync`), `/orders/edit` (+ `/sync`), `GET /api/v1/orders` (статус), `POST /api/v1/orders/search` (список) | MVP: create/cancel/status/list (sync+async); edit — позже |
| Сделки | `POST /api/v1/trades/search` | MVP |
| Рыночные данные | `POST /api/v1/quotes`, `GET /api/v1/order-book`, `GET /api/v1/candles-chart`, `POST /api/v1/last-trades` | MVP: quotes/order-book/candles-chart; last-trades — позже |
| Справочник | `POST /api/v1/instruments/by-tickers`, `/by-isins`, `GET /api/v1/instruments/by-type`, `GET /api/v1/trading-schedule/status`, `/daily-schedule` | MVP: by-tickers + trading-status; остальное — позже |
| Прочее | discounts, operations/search (неторговые), deprecated-методы, OTC, MCP | не реализуем (deprecated — по запросу роутера) |
| WebSocket | потоки портфеля/заявок/стакана/сделок | отдельный этап после HTTP MVP |

**ADR-5. Портфель БКС — массив лимитов**, а не «кэш+позиции»: записи типов
`moneyLimit/depoLimit/futuresLimit/futuresHolding/otcLimit` в разрезе сроков `T0/T1/T2/T365`.
`core` хранит брокер-независимый `PortfolioSnapshot`; `bks/adapter` раскладывает его в
контрактные записи (для песочницы: RUB, term=T0/T1 — по согласованию). Обоснование: зеркалим
реальный контракт (`docs/bcs-api/http/portfolio.md`), сохраняя ядро переносимым.

**ADR-6. Ошибки — единый контракт `ErrorResponse`** (timestamp, traceId, type ∈
{VALIDATION_ERROR, RESOURCE_EXHAUSTED, NOT_FOUND, UNAUTHORIZED, FORBIDDEN, CONFLICT, ...},
errors[]) + HTTP-коды как в схемах StatusCodes. Rate-limit (429) эмулируем по
`docs/bcs-api/restrictions.md` — отключаемо конфигом.

**ADR-7. Авторизация:** эмулятор выдаёт «refresh-токены» из переменных окружения (решение владельца, модуль 2; в YAML — только плейсхолдеры) (два вида прав:
trade / read-only — как у БКС), обмен на access-токен (JWT-подобный, 24 ч) на эндпоинте
авторизации под префиксом брокера; фильтр проверяет Bearer на API-путях брокера.
Обоснование: роутер проходит тот же сценарий, что и с боевым API.

## 6. Рыночные данные (core/marketdata + infra/algoapi)

**ADR-8. Три режима: LIVE, RECORD, REPLAY** (+ виртуальный `Clock`).
- LIVE: клиент AlgoPack (`algo-api.moex.com`, заголовок `Authorization: Bearer <APIKEY>`,
  решение владельца): котировки,
  стакан, сделки, свечи; справочник/расписание/свечи при нехватке тарифа — из MOEX ISS
  (`iss.moex.com`, без токена; домен `iss.moex.ru` выведен из эксплуатации). Токен — только из
  переменных окружения (`.env` не коммитится).
- RECORD = LIVE + запись входящего потока в БД (таблицы снапшотов) — собственная история
  для REPLAY. Обоснование: сравнение с бэктестом корректно только по тем же данным.
- REPLAY: воспроизведение записи/истории по таймлайну на виртуальном `Clock`; все
  стохастические модели (проскальзывание) используют `Random` с фиксируемым seed —
  прогон детерминирован.

Порт `MarketDataProvider`: последняя цена/BBO, стакан, история (свечи/сделки), дневной объём
(ADV — для ликвидности), справочник инструментов. Реализации: `AlgoPackMarketDataProvider`,
`IssMarketDataProvider` (fallback/справочник), `ReplayMarketDataProvider`,
`RecordingMarketDataProvider` (декоратор LIVE).

**ADR-11. Единицы количества: `core` — штуки, MOEX ISS/AlgoPack — лоты, пересчёт на границе.**
- Модель `core` хранит объёмы в штуках: `Order.quantity`, `executedQuantity`, `Fill.quantity`,
  `Position.quantity`, `OrderBookSnapshot.Level.quantity`, `Trade.quantity`. Это требование
  контракта БКС (`orderQuantity`, `fills.quantity`, `order-book` — «шт.»); `*Lots` в ответах БКС
  выводятся делением на `lotSize` в `bks/adapter`.
- Биржа отдаёт `QUANTITY` стакана и сделок **в лотах** (проверено на живых данных GAZP/HYDR/SNGS/
  FEES: `VALUE = QUANTITY × PRICE × LOTSIZE` выполняется для 100 % строк, `QUANTITY × PRICE` — ни
  для одной), а `volume` свечей и `VOLTODAY` — **в штуках** (`value / volume` = средневзвешенная
  цена сделки и лежит внутри диапазона свечи только без пересчёта на лотность).
- Значит пересчёт `лоты → штуки` (`QuantityUnits.toShares`) обязателен в `IssMarketDataProvider`
  и `AlgoPackMarketDataProvider` (стакан и сделки), а `AdvCalculator` суммирует объём свечей **без**
  умножения на `lotSize`. `*Client` и DTO в `infra/algoapi/dto` остаются зеркалом ответа биржи —
  пересчёт не должен протекать в транспортный слой.
- Обоснование границы: иначе `MatchingEngine` видит ликвидность, заниженную в `lotSize` раз
  (FEES — в 10 000), исполняет заявки по крупице за тик и фишлами, некратными лоту, а ADV завышен
  в `lotSize` раз, из-за чего `AdvLiquidityPolicy` и тиры `SpecSlippageModel` теряют смысл.
- Сопутствующее правило: `Order.accept` отклоняет `quantity` нецелое число лотов
  (`InvalidOrderException` → HTTP 400), как реальный брокер; `lotSize <= 1` (фьючерсы, лот из одной
  бумаги) проходит проверку автоматически.

**ADR-12. Торговое окно — полный день TQBR из данных биржи, а не окно основной секции.**
- Границы дня берутся из минутных свечей эталонного инструмента ISS: открытие — первый бар дня;
  закрытие — последний бар вечернего фрагмента (запрос с 18:00 МСК), но только когда день закрылся.
  Для идущего дня закрытие плановое из `session-close`: до 23:50 МСК вечерний фрагмент неполный, и
  по его последнему бару день сузился бы (например до 19:30), остановив исполнение вечером.
- Fallback-окно настроек (`session-open`/`session-close`) расширено до полного торгового дня
  07:00–23:50 МСК (было 10:00–18:50 — только основная секция). Обоснование: `ExecutionServiceImpl`
  гейтит матчинг по `TradingSession.open()`, и пока день не попал в кэш календаря, заявки между
  07:00–10:00 и после 18:50 МСК оставались бы `NEW` — проверено экспериментом 2026-11-19: лимитная
  заявка AFLT исполнилась ровно в 10:01, как только наступила граница fallback-окна.
- Границы уже известных дней периодически перезаписываются из данных биржи
  (`TradingCalendarService.refreshLiveBounds`, вызывается `TradingCalendarInitializer` раз в час):
  `tradingDays()` пропущенные кэшем дни не обновляет, поэтому записанная вслепую граница застыла бы
  навсегда — так в БД остались 10:00–18:50 у дней 11–18.11.
- Корень «вслепую записанных» границ (2026-11-19): `RestClient.uri(String)` трактует строку как
  URI-шаблон и кодирует её повторно, поэтому вечерний запрос свечей уходил как
  `from=2026-11-19%252018:00:00`. ISS такую дату не распознаёт и вместо вечера отвечает барами
  чужого дня (замерено: 500 строк за 2011-12-15). `boundsWithinDay` отбраковывает их, `fetchCandleBounds`
  возвращает пусто — и всё это без единого WARN: границы молча брались из fallback-окна настроек, а
  `synchronize()` больше не возвращался к этим дням. Исправление: URI собирается `build().encode().toUri()`
  и передаётся объектом `uri(URI)`, а не строкой (проверено регресс-тестом
  `candlesRequestEncodesFromParameterExactlyOnce`).
- Досрочное (сокращённое) закрытие дня приходит из тех же свечей: фактическая граница приоритетнее
  плановой, но только после наступления планового закрытия (`closeOf` принимает вечерний хвост при
  `!at.isBefore(planned)`). До планового закрытия идущий день живёт по плановой границе, иначе
  неполный вечерний фрагмент сузил бы день задолго до реального конца сессии. При сбое источника
  плановое закрытие не переписывает закэшированное, если оно позже, — день не сужается.
- Схема `trading_calendar` не менялась (решение владельца): источник границы (биржа/настройки) не
  фиксируется, различие устраняется перезаписью при следующей синхронизации. Следствия, принятые
  осознанно: (а) если день уже закрыт и закэширован фактической границей, а на часовом refresh
  вечерний фрагмент временно не пришёл, закрытие откатится к плановому (расширение `widen` не
  различает «кэш = факт» и «кэш = fallback»); (б) для последнего дня исторического диапазона
  `tradingDays()` плановое закрытие может быть записано вместо доступного фактического, пока его не
  поправит `refreshLiveBounds`.
- Следствие для контракта: `TradingStatusResponse.sessionName` («Основная сессия»,
  `TradingCalendarService.SESSION_NAME`) в упрощённой модели одной сессии означает весь торговый
  день, а не секцию 09:10–18:55. Поле — строка, контракт БКС (`bks/contract`) не затронут.

## 7. Исполнение ордеров (core/execution)

**ADR-9. Точная модель по спецификации владельца (упрощения запрещены):**
- Маркет: кросс стакана (buy — ask, sell — bid) + проскальзывание:
  случайные 0.05–0.15% в неблагоприятную сторону + тир по размеру ордера относительно ADV:
  <1% → +0.05%, 1–5% → +0.1%, >5% → +0.2%. Тир считается по объёму прохода, а не по куску
  стакана.
- Ликвидность: ордер > 5% ADV (порог настраивается) → отклонение (`LiquidityExceededException`
  → HTTP 429 `RESOURCE_EXHAUSTED`) либо разбивка на части — политика `LiquidityPolicy`
  настраивается на профиль (`REJECT` по умолчанию / `SPLIT`).
- Лимит: ожидание в очереди; исполнение, когда рынок пересекает лимит, по цене «лимит или
  лучше» (buy: ask ≤ limit → цена ask); частичные исполнения ограничиваются доступным объёмом
  стакана; упрощение очереди (без позиции в очереди) — зафиксировано как допущение.
- FOK — all-or-nothing: при нехватке объёма заявка отклоняется без частичных сделок. GTD
  снимается после `expiryDate` (или на закрытии этого дня). DAY живёт до конца торгового дня,
  под который создана: созданная до закрытия истекает на границе закрытия, созданная после
  закрытия переносится на следующий торговый день. Граница — `closeAt` того же источника
  сессии (`MarketDataProvider.tradingSession`), который разрешает исполнение; причина
  `EXPIRED` содержит таймстемп границы (см. `docs/order-events.md`).
- Маркет-остаток при нехватке объёма снимается (`CANCELED`), лимитный остаток остаётся активным.
- Жизненный цикл `NEW → PARTIALLY_FILLED → FILLED / CANCELED / REJECTED`; переходы пишутся в
  `order_events` (`CREATED`, `PARTIALLY_FILLED`, `FILLED`, `CANCELED`, `REJECTED`, `EXPIRED`).
  Семантика `order_events.quantity`: `PARTIALLY_FILLED` — размер фишла, терминальные события —
  накопленный `executed_quantity` (`docs/order-events.md`).
- Исполнение батча (`FillService.fillAll`) — одна транзакция с одним восстановлением портфеля
  и одной записью: нативные `upsert`/`updateBalances` не обновляют persistence context, поэтому
  повторное восстановление внутри транзакции вернуло бы устаревший снимок.
- `orderType` 3–5 контракта (алгоритмическая GTD, iceberg, условная) — заглушка
  `NOT_IMPLEMENTED` с TODO; стоп-лоссы и WebSocket-нотификации вне скоупа.

Интерфейсы `SlippageModel`, `LiquidityPolicy`, `MatchingEngine` — в `core/execution`, реализации
по умолчанию там же (`SpecSlippageModel`, `AdvLiquidityPolicy`). `infra/services`:
`ExecutionServiceImpl` (матчинг и жизненный цикл), `OrderPlacementService` (предпроверка ADV и
немедленная попытка при постановке), `ExecutionScheduler` (фоновый проход, отключается
`scheduler-enabled=false`). Seed REPLAY берётся из `marketdata.replay.seed`; STUB — из
`execution.slippage.seed`; LIVE/RECORD — системный `SecureRandom`.

## 8. Портфель, комиссии, ГО (core/portfolio + core/commission)

- Акции: кэш, позиция, T+1 фиксится в домене (для песочницы расчёты T0 — допущение).
- Фьючерсы: ГО по умолчанию ~10% (настраивается per-instrument), ежедневная вариационная
  маржа; при нехватке обеспечения новые ордера отклоняются (авто-ликвидация — будущее).
- Комиссии: тариф профиля брокера (процент + минимальная; для фьючерсов — биржевой сбор);
  по умолчанию 0.1% для акций. Комиссия пишется в Fill отдельным полем (для журнала).
- NAV: ежедневный снапшот по закрытию основной сессии (торговый календарь МБ) в БД.
- Корпоративные события (дивиденды/сплиты): интерфейс-заглушка + TODO.

## 9. Персистентность (infra)

**ADR-10. Персистентность — слой infra, без портов в core** (решение владельца: пакет
`core/spi` упразднён). PostgreSQL 18, Spring Data JPA: `infra/repository` (репозитории),
`infra/model` (сущности БД), `infra/dto`, `infra/mapper` (MapStruct: сущность ↔ домен core),
`infra/services` (оркестрация: загрузка/сохранение состояния + вызов чистой доменной логики
core). Зависимость односторонняя: `infra → core`, контролируется ArchUnit.

Схему создаёт и изменяет только Flyway (`src/main/resources/db/migration`; V1 — этап 3,
V2 — таблицы рыночных данных этапа 4, V3 — `expiry_date` и журнал `order_events` этапа 5,
V4 — `strategy_id`, `reference_price`, `reports` и `backtest_results` этапа 6);
`spring.jpa.hibernate.ddl-auto=validate` — Hibernate лишь сверяет сущности со схемой. Деньги —
`NUMERIC(19,2)`, цены и средние цены — `NUMERIC(19,6)`, время — только
`TIMESTAMP WITH TIME ZONE`.

| Таблица | Ключевые колонки и ограничения |
|---|---|
| `accounts` | `account_id VARCHAR(64) PK`, `created_at NOT NULL DEFAULT now()` |
| `account_balances` | `id BIGSERIAL PK`, `account_id → accounts`, `currency VARCHAR(8)`, `cash/locked NUMERIC(19,2) NOT NULL DEFAULT 0`, `updated_at`; `UNIQUE (account_id, currency)` |
| `instruments` | `id BIGSERIAL PK`, `ticker`, `class_code`, `type`, `short_name/display_name/isin`, `lot_size/minimum_step NUMERIC(19,6)`, `currency`; `UNIQUE (ticker, class_code)` |
| `orders` | `order_id VARCHAR(64) PK`, `order_num BIGINT UNIQUE`, `client_order_id UUID UNIQUE`, `account_id → accounts`, `instrument_id → instruments`, `side/order_type/time_in_force/status`, `quantity BIGINT`, `price NUMERIC(19,6)`, `expiry_date DATE NULL` (только GTD), `executed_quantity BIGINT`, `average_price NUMERIC(19,6)`, `created_at/updated_at` |
| `order_events` | `id BIGSERIAL PK`, `order_id → orders`, `event_type VARCHAR(32)`, `previous_status/status VARCHAR(32)`, `quantity BIGINT`, `price NUMERIC(19,6)`, `reason VARCHAR(512)`, `occurred_at TIMESTAMPTZ`; индекс `(order_id, occurred_at, id)` |
| `fills` | `id BIGSERIAL PK`, `order_id → orders`, `account_id → accounts`, `instrument_id → instruments`, `price NUMERIC(19,6)`, `quantity BIGINT`, `commission NUMERIC(19,2)`, `executed_at` |
| `positions` | `id BIGSERIAL PK`, `account_id → accounts`, `instrument_id → instruments`, `quantity BIGINT` (short — отрицательное), `average_price NUMERIC(19,6)`, `updated_at`; `UNIQUE (account_id, instrument_id)`, `CHECK (quantity <> 0)`, `CHECK (average_price >= 0)` |
| `nav_daily` | `id BIGSERIAL PK`, `account_id → accounts`, `nav_date DATE`, `as_of`, `cash/commission/variation_margin/market_value/nav NUMERIC(19,2)`, `holdings VARCHAR(4000) NOT NULL DEFAULT '[]'`; `UNIQUE (account_id, nav_date)` |
| `market_data_snapshots` | `id BIGSERIAL PK`, `ts TIMESTAMPTZ NOT NULL`, `kind VARCHAR(16)` (QUOTE/ORDER_BOOK/TRADE), `ticker VARCHAR(32)`, `class_code VARCHAR(8)`, `payload JSONB NOT NULL`; индекс `(ticker, class_code, ts)` — таймлайн REPLAY |
| `trading_calendar` | `id BIGSERIAL PK`, `engine/market/board VARCHAR(16)`, `trade_date DATE`, `is_trading BOOLEAN`, `open_utc/close_utc TIMESTAMPTZ NULL`; `UNIQUE (engine, market, board, trade_date)` |
| `reports` | `id BIGSERIAL PK`, `account_id → accounts`, `label VARCHAR(128)`, `period_from/period_to DATE`, `generated_at TIMESTAMPTZ`, `payload JSONB` — канонический `ReportPayload`; `CHECK (period_to >= period_from)` |
| `backtest_results` | `id BIGSERIAL PK`, `report_id → reports NULL`, `account_id → accounts`, `label`, `threshold NUMERIC(9,6)`, `passed BOOLEAN`, `compared_at TIMESTAMPTZ`, `payload JSONB` — вход и отклонения |

Стратегия записи:

- `accounts`, `account_balances`, `instruments` — идемпотентный `INSERT ... ON CONFLICT DO NOTHING`
  по естественному ключу: гонка двух контекстов не прерывает транзакцию, накопленный баланс не
  перезаписывается.
- `positions` — `INSERT ... ON CONFLICT (account_id, instrument_id) DO UPDATE` (количество,
  средняя цена, `updated_at`); закрытая позиция удаляется, потому что схема запрещает нулевое
  количество.
- `orders`, `fills`, клиринг ВМ и закрытие дня (`nav_daily`) выполняются в одной транзакции под
  пессимистичной блокировкой строки счёта (`AccountRepository.lockByAccountId`,
  `SELECT ... FOR UPDATE`): под ней же нумеруются заявки.
- `nav_daily.holdings` — JSON-массив `NavSnapshot.Holding` (`VARCHAR(4000)`, по умолчанию
  `[]`); JSONB — открытый вопрос владельца.

`backtest_results` создана миграцией V4 этапа 6; `strategy_tags` остаётся отложенной (метка
`orders.strategy_id` покрывает потребность отчётности). Индексы `orders (account_id, strategy_id)`
и `fills (account_id, executed_at)` добавлены той же миграцией; индекс на `orders.status` остаётся
открытым вопросом владельца. Черновик `positions_daily` заменён таблицей `positions` (текущее
состояние).

`POST /v1/trades/search` читает сделку из `fills` (номер — PK `fills`), заявка даёт номер, сторону
и клиентский код, справочник — инструмент (`JpaTradeServiceImpl`, этап 7); in-memory
`InMemoryTradeServiceImpl` этапа 2 оставлен только как база unit-тестов сортировки/пагинации.


## 10. Отчётность (core/reporting)

- Метрики из кривой NAV: CAGR, Sharpe, Sortino, Max Drawdown, Calmar (252 торговых дня,
  безрисковая ставка — конфиг, по умолчанию 16%); WinRate — по закрытым FIFO-парам, комиссии
  вычитаются из результата. Неопределённые значения — `null`, а не `0`.
- Журнал сделок: опорная цена стакана и фактическая цена после проскальзывания раздельно,
  комиссия отдельным полем, `strategyId` из заголовка `X-Strategy-Id` (расширение контракта БКС;
  поле `orders.strategy_id`), выгрузка JSON/CSV.
- Сравнение с бэктестом: вход — метрики или ряд NAV внешнего бэктеста (JSON, контракт —
  `docs/reporting.md`), выход — таблица отклонений (абс. и %), флаг PASS/FAIL по порогу 30%.
- Разбивка по стратегиям — реализованный PnL, число сделок, число закрытых пар и win rate.
- Хранение: `reports`, `backtest_results`; экспорт в `emulator.reporting.output-dir`.
- Внутренний API `/api/internal/reports/**` — брокер-нейтральный пакет `reporting/api`, без токена
  БКС; ошибки отчётов обрабатывает `InternalReportExceptionHandler` (пакет `reporting/api`,
  высший приоритет), `BksExceptionHandler` остаётся глобальным — формат ошибок неизвестных путей
  БКС сохранён.

## 11. Конфигурация

Единый `application.yml` + переменные окружения для секретов. Ключи (префикс `emulator.`):
`marketdata.mode` (STUB/LIVE/RECORD/REPLAY), `replay.*` (начало таймлайна, скорость, seed),
`marketdata.retention-days` (хранение записи RECORD), `execution.*` (планировщик, глубина
стакана, политика ликвидности и порог ADV, seed и базовая доля проскальзывания),
`reporting.*` (безрисковая ставка, порог сверки с бэктестом, каталог экспорта),
`algoapi.*` (base-url, таймауты, ретраи; токен — только env), `iss.*` (base-url, таймауты,
ретраи, режим/рынок/борда), `brokers.bks.*` (тариф комиссий, пороги ликвидности, ГО, rate-limit),
`db.*`.
Профили Spring: `live`, `replay`, `test` (Testcontainers PostgreSQL — решить на этапе 3).
Мониторинг — Spring Boot Actuator (набор эндпоинтов и путь монтирования — на этапе 2).

## 12. Этапы реализации

Порядок и границы этапов — в `docs/roadmap.md`; статусы — в `docs/progress.md`.
