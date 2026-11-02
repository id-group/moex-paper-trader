# Отчёт по этапу 2: Контракт БКС (HTTP MVP)

Дата: 2026-11-01. Ветка: `feature/Phase-2`. Бриф: `docs/agent-briefs/stage-2-bks-contract.md`.

## Итог

MVP контракта БКС работает поверх заглушек ядра: авторизация refresh→access, заявки
(create/cancel/status/list, sync и async), сделки, портфель, лимиты, котировки, стакан, свечи,
справочник и статус торгов. Исполнение виртуальное — заявка принимается и хранится в памяти;
сделки появятся на этапе 5.

Проверка: `./gradlew clean build` — зелёный (Spotless, 124 теста, ArchUnit, JaCoCo ≥ 60%).
Сквозной HTTP-сценарий на `bootRun` пройден (транскрипт ниже).

## Что реализовано

| Слой | Содержимое |
|---|---|
| `core/domain` | `Order`, `Instrument`, `Side`, `OrderType`, `TimeInForce`, `OrderStatus`, `OrderIdType`, `NewOrder`, `Position`, `PortfolioSnapshot`, `Trade`, критерии поиска, `PageResult` |
| `core/marketdata` | порт `MarketDataProvider`, `Quote`, `OrderBookSnapshot`, `Candle`, `CandleSeries`, `TimeFrame`, `TradingSession` |
| `bks/contract` | DTO — зеркало схем снапшота: ошибки, токены, заявки, сделки, котировки/стакан/свечи, справочник, портфель/лимиты |
| `bks/adapter` | `BksTokenService` (refresh→access, TTL 24 ч / 90 дней), `BksOrderMapper`, `BksTradeMapper`, `BksPortfolioMapper`, `BksMarketDataMapper`, `BksProperties` |
| `bks/api` | 6 контроллеров, Bearer-фильтр, `@RestControllerAdvice` → `ErrorResponse`, проверки пагинации |
| `infra/services` | `InMemoryOrderServiceImpl`, `InMemoryTradeServiceImpl`, `PortfolioServiceImpl`, `StubMarketDataProvider` (конфиг инструментов) |

Точка монтирования — `emulator.brokers.bks.base-path` (по умолчанию `/api/bks`). Контрактный
`/api/v1/x` доступен как `/api/bks/v1/x`, Keycloak-эндпоинт — как
`/api/bks/trade-api-keycloak/realms/tradeapi/protocol/openid-connect/token`.

## Эндпоинты и схемы снапшота

| Метод и путь эмулятора | Схема запроса | Схема ответа / коды |
|---|---|---|
| `POST /api/bks/trade-api-keycloak/realms/tradeapi/protocol/openid-connect/token` | встроена в `http/authorization.md` | встроена в `authorization.md` (200/400) |
| `GET /api/bks/v1/portfolio` | — | встроена в `http/portfolio.md` (200/429) |
| `GET /api/bks/v1/limits` | — | встроена в `http/limits.md` (200/429) |
| `POST /api/bks/v1/orders` | `operations/create.RequestSchema.json` | `operations/create.StatusCodes.json` |
| `POST /api/bks/v1/orders/sync` | `operations/sync/create-order-sync.RequestSchema.json` | `operations/sync/create-order-sync.StatusCodes.json` |
| `POST /api/bks/v1/orders/cancel` | `operations/cancel-order.RequestSchema.json` | `operations/cancel-order.StatusCodes.json` |
| `POST /api/bks/v1/orders/cancel/sync` | `operations/sync/cancel-order-sync.RequestSchema.json` | `operations/sync/cancel-order-sync.StatusCodes.json` |
| `GET /api/bks/v1/orders` | `operations/get-order-by-id.ParamsDetails.json` (body пустой) | `operations/get-order-by-id.StatusCodes.json` |
| `POST /api/bks/v1/orders/search` | `operations/all-order-list.RequestSchema.json` + `.ParamsDetails.json` | `operations/all-order-list.StatusCodes.json` |
| `POST /api/bks/v1/trades/search` | `trades/get-trades.RequestSchema.json` + `.ParamsDetails.json` | `trades/get-trades.StatusCodes.json` |
| `POST /api/bks/v1/quotes` | `market-data/get-quotes.RequestSchema.json` | `market-data/get-quotes.StatusCodes.json` |
| `GET /api/bks/v1/order-book` | `market-data/get-order-book.ParamsDetails.json` | `market-data/get-order-book.StatusCodes.json` |
| `GET /api/bks/v1/candles-chart` | `market-data/get-candles-chart.ParamsDetails.json` | `market-data/get-candles-chart.StatusCodes.json` |
| `POST /api/bks/v1/instruments/by-tickers` | встроена в `information/get-instruments-by-tickers.md` | встроена там же (200/429) |
| `GET /api/bks/v1/trading-schedule/status` | — | встроена в `information/get-trading-status.md` (200/429) |

Пути в таблице — фактические для эмулятора; в снапшоте те же схемы относятся к `/api/v1/...`.
DTO проверены по полям: имена, типы (`number` → `BigDecimal`, `int64` → `Long`,
`int32` → `Integer`, `date-time` → `OffsetDateTime`, `date` → `LocalDate`), enum-строки
`"1"/"2"`, вложенность и `required`.

## Авторизация и конфигурация

- Refresh-токены — только из переменных окружения: `BKS_REFRESH_TOKEN_TRADE`,
  `BKS_REFRESH_TOKEN_READ` (в YAML — плейсхолдеры с пустым значением).
- `trade-api-write` даёт права `TRADE`, `trade-api-read` — `READ_ONLY`; чужой client_id для
  refresh-токена отклоняется как `invalid_grant`.
- Access-токен — opaque (`bks-<uuid>`), TTL 24 ч (`emulator.brokers.bks.auth.access-token-ttl`),
  refresh TTL 90 дней. Bearer проверяется фильтром на `/api/bks/v1/**`.
- Read-only токен получает 403 на create/cancel (включая sync); остальные методы ему доступны.
- Rate limit заложен конфигом (`rate-limit.enabled=false`, `requests-per-second=10`), эмуляция
  429 — этап 5.
- Actuator: наружу выставлен только `/actuator/health` (закрывает временное решение №1).

## Тесты и покрытие

- 124 теста: unit (домен, сервисы, мапперы, токен-сервис, сериализация контракта) и
  интеграционные `@SpringBootTest(webEnvironment = RANDOM_PORT)` — реальный HTTP по каждому
  контроллеру: happy path + 400/401/403/404/422 и сверка JSON-полей.
- ArchUnit: core без Spring/внешних слоёв, contract без Spring/core/api/adapter, infra без bks;
  тестовые классы из анализа исключены.
- JaCoCo (bundle): **92.9% инструкций, 94.0% строк** (порог 60% пройден; после исправлений
  ревью — см. отчёт `/precommit`).

## Исправления после ревью (2026-11-02)

| Коммит | Что исправлено |
|---|---|
| `17e043c` | Пустой `refresh_token` теперь даёт 400 `invalid_grant`; восстановлена кодировка `BksTradeMapper` |
| `33c21b4` | `BksTokenService` очищает просроченные access-сессии при каждом обмене токена (устранена утечка памяти) |

Решения по спорным местам контракта, которые **не** потребовали изменения кода:

1. **Неизвестный путь под `/api/bks/v1/**`.** Без токена — 401, с валидным токеном — 404:
   авторизация проверяется до маршрутизации (auth-first, ADR-7). Оставлено как есть.
2. **Дубль `clientOrderId`.** Возвращается 422 `VALIDATION_ERROR`: в снапшоте БКС нет 409 для
   create, а 422 присутствует в `operations/create.StatusCodes.json`. Возможная смена `type`
   на `CONFLICT` — отдельное решение, отложено.
3. **`orders/search` с `orderTypes`.** Коды 10 и 11 валидны по enum `all-order-list`;
   неподдержанные типы дают пустую выборку. Ответ 400 в search не задокументирован, поэтому
   оставлено без изменений.

Подробности — `docs/temporary-decisions.md` (пункты 10–12).

## Сквозной сценарий (DoD)

Запуск:

```powershell
$env:JAVA_HOME=$env:MOEX_JAVA25_HOME
$env:BKS_REFRESH_TOKEN_TRADE='dev-trade-token'
$env:BKS_REFRESH_TOKEN_READ='dev-read-token'
.\gradlew.bat bootRun
```

Сценарий (PowerShell, порт из `server.port`):

```powershell
$base = 'http://localhost:8080/api/bks'
$token = Invoke-RestMethod -Method Post `
  -Uri "$base/trade-api-keycloak/realms/tradeapi/protocol/openid-connect/token" `
  -ContentType 'application/x-www-form-urlencoded' `
  -Body @{ client_id='trade-api-write'; refresh_token='dev-trade-token'; grant_type='refresh_token' }
$headers = @{ Authorization = "Bearer $($token.access_token)" }
$order = Invoke-RestMethod -Method Post -Uri "$base/v1/orders/sync" -Headers $headers `
  -ContentType 'application/json' -Body (@{ clientOrderId=[guid]::NewGuid(); side='1';
    orderType='2'; orderQuantity=10; ticker='SBER'; classCode='TQBR'; price=285.5 } | ConvertTo-Json)
$status = Invoke-RestMethod -Uri "$base/v1/orders?orderIdType=1&orderId=$($order.clientOrderId)" -Headers $headers
$portfolio = Invoke-RestMethod -Uri "$base/v1/portfolio" -Headers $headers
$cancel = Invoke-RestMethod -Method Post -Uri "$base/v1/orders/cancel/sync" -Headers $headers `
  -ContentType 'application/json' -Body (@{ orderIdType='1'; orderId=$order.clientOrderId;
    clientOrderId=[guid]::NewGuid() } | ConvertTo-Json)
```

Фактический результат (2026-11-01, `bootRun`, временно порт 18090 — см. примечание):

```json
{
  "token": { "token_type": "bearer", "expires_in": 86400, "scope": "trade-api-write" },
  "created": {
    "clientOrderId": "1d593045-d360-486e-b032-8376be102779",
    "orderStatus": "0",
    "orderId": "20261101-TQBR-796285400001",
    "ticker": "SBER",
    "price": 285.5
  },
  "status": { "orderStatus": "0", "remainedQuantity": 10 },
  "portfolio": { "entries": 1, "type": "moneyLimit", "quantity": 1000000.0, "currency": "RUB" },
  "cancelled": { "orderStatus": "4" },
  "statusAfterCancel": "4",
  "limits": { "moneyLimit": 1000000.0, "term": "T0" }
}
```

Дополнительно проверено: read-only create → 403, portfolio без токена → 401, неизвестная
заявка → 404.

Примечание: на машине агента порт 8080 сбрасывает внешние подключения к процессу Java
(локальная особенность окружения; `jwebserver` и Node на других портах доступны). Сценарий
выполнен с `--server.port=18090`; код и конфигурация по умолчанию остаются на 8080.

## Допущения и отклонения (зафиксировать в решениях)

1. **Исполнение — заглушка этапа 2**: заявки принимаются и хранятся, но не исполняются;
   `trades/search` возвращает пустую страницу. Матчинг и fills — этап 5.
2. Поддерживаются `orderType` `1` (рыночная) и `2` (лимитная), `timeInForce` `1`/`2`.
   `3`–`5` и GTD отклоняются с 422 — до этапа 5.
3. Заявка на неизвестный инструмент → 404. В `quotes` неизвестные инструменты пропускаются,
   в `order-book`/`candles-chart` — 404 (код 404 входит в общий `ErrorResponse`, хотя в
   StatusCodes рыночных страниц перечислены только 200/400/429).
4. Портфель и лимиты — RUB, срок T0 (ADR-5 допускает T0/T1 по согласованию); depoLimit пуст,
   пока нет исполненных сделок. Средства под активными лимитными покупками показываются в
   `locked`.
5. В DTO справочника перенесены все 75 полей схемы, включая deprecated; незаполненные —
   `null` и в JSON не выводятся (`@JsonInclude(NON_NULL)`). Для этого одного DTO используется
   `JsonMapper.convertValue` по карте известных полей — осознанное исключение из ручного
   маппинга из-за размера схемы.
6. Ошибочный ответ токен-эндпоинта — Keycloak-формат `{error, error_description}`, а не
   `ErrorResponse` (так описано в `authorization.md`).
7. `ErrorDetail.payload` заполняется как `{"message": {"text": "..."}}` — схема требует
   объектные значения `additionalProperties`.
8. Заголовок `X-Strategy-Id` (ADR §10) не реализован — этап 6; rate limit — этап 5.
9. Хранилище заявок/сделок in-memory (временное решение №2), этап 3 заменит на JPA.
10. Заглушка рыночных данных (`emulator.marketdata.stub.instruments`) детерминированно генерирует
    котировки/стакан/свечи от базовой цены; настоящие AlgoPack/ISS — этап 4.
