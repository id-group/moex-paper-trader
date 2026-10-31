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
- LIVE: клиент AlgoPack (`algo-api.moex.com`, заголовок `X-AlgoPack-Token`): котировки,
  стакан, сделки, свечи; справочник/расписание/свечи при нехватке тарифа — из MOEX ISS
  (`iss.moex.ru`, без токена). Токен — только из переменных окружения (`.env` не коммитится).
- RECORD = LIVE + запись входящего потока в БД (таблицы снапшотов) — собственная история
  для REPLAY. Обоснование: сравнение с бэктестом корректно только по тем же данным.
- REPLAY: воспроизведение записи/истории по таймлайну на виртуальном `Clock`; все
  стохастические модели (проскальзывание) используют `Random` с фиксируемым seed —
  прогон детерминирован.

Порт `MarketDataProvider`: последняя цена/BBO, стакан, история (свечи/сделки), дневной объём
(ADV — для ликвидности), справочник инструментов. Реализации: `AlgoPackMarketDataProvider`,
`IssMarketDataProvider` (fallback/справочник), `ReplayMarketDataProvider`,
`RecordingMarketDataProvider` (декоратор LIVE).

## 7. Исполнение ордеров (core/execution)

**ADR-9. Точная модель по спецификации владельца (упрощения запрещены):**
- Маркет: кросс стакана (buy — ask, sell — bid) + проскальзывание:
  случайные 0.05–0.15% в неблагоприятную сторону + тир по размеру относительно ADV:
  <1% → +0.05%, 1–5% → +0.1%, >5% → +0.2%.
- Ликвидность: ордер > 5% ADV (порог настраивается) → отклонение (`RESOURCE_EXHAUSTED`-подобная
  ошибка контракта) либо разбивка на части — политика `LiquidityPolicy` настраивается на профиль.
- Лимит: ожидание в очереди; исполнение, когда рынок пересекает лимит, по цене «лимит или
  лучше» (buy: ask ≤ limit → цена min(limit, ask)); частичные исполнения ограничиваются
  прошедшим объёмом; упрощение очереди (без позиции в очереди) — зафиксировано как допущение.

Интерфейсы `SlippageModel`, `LiquidityPolicy`, `MatchingEngine` — в `core/execution`,
реализации по умолчанию там же; тесты покрывают тиры проскальзывания и сценарии лимиток.

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
Таблицы (черновик): `accounts`, `instruments`, `orders`, `order_events` (лог статусов),
`fills`, `positions_daily`, `nav_daily`, `market_data_snapshots` (записи RECORD),
`strategy_tags`, `backtest_results`. Миграции — кандидат Flyway (подтвердить на этапе 3).

## 10. Отчётность (core/reporting)

- Метрики из кривой NAV: CAGR, Sharpe, Sortino, Max Drawdown, Calmar; WinRate — по закрытым
  сделкам (сопоставление FIFO — соглашение).
- Журнал сделок: цена до/после проскальзывания, комиссия, strategyId.
- Сравнение с бэктестом: вход — метрики/ряд NAV внешнего бэктеста (JSON), выход — таблица
  отклонений; порог 30% проверяется интеграционным тестом (этап 7).
- Разбивка по стратегиям — по `strategyId` ордеров (роутер передаёт метку; поле прокидывается
  через контракт как расширение — согласовать формат передачи с владельцем: заголовок
  `X-Strategy-Id` — допущение по умолчанию).

## 11. Конфигурация

Единый `application.yml` + переменные окружения для секретов. Ключи (префикс `emulator.`):
`mode` (LIVE/RECORD/REPLAY), `replay.*` (период, скорость, seed), `algoapi.token` (env),
`brokers.bks.*` (тариф комиссий, пороги ликвидности, ГО, rate-limit), `db.*`.
Профили Spring: `live`, `replay`, `test` (Testcontainers PostgreSQL — решить на этапе 3).
Мониторинг — Spring Boot Actuator (набор эндпоинтов и путь монтирования — на этапе 2).

## 12. Этапы реализации

Порядок и границы этапов — в `docs/roadmap.md`; статусы — в `docs/progress.md`.
