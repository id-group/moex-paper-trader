# Бриф этапа 2: Контракт БКС (HTTP MVP)

## Цель

Через API эмулятора можно авторизоваться, выставить/отменить ордер, получить статус, список
заявок, сделки, портфель, лимиты и рыночные данные — строго по контракту БКС. Исполнение пока
виртуальное («заглушка»: ордер принимается и сохраняется в память), настоящий портфель и
матчинг — на этапах 3 и 5.

## Прочитать до начала (обязательно)

1. `AGENTS.md` — правила, окружение (JAVA_HOME!), запреты.
2. `docs/ARCHITECTURE.md` — разделы 2, 3, 5 (ADR-4…ADR-7).
3. `docs/bcs-api/README.md` — состав снапшота.
4. Страницы снапшота по своим эндпоинтам + sidecar-схемы `*.RequestSchema.json`,
   `*.StatusCodes.json`, `*.ParamsDetails.json` — **единственный источник правды о полях DTO**.

## Скоуп (что делать)

Пакеты `ru.moex.papertrader.bks.*` + минимум в `core`:

- `core/domain`: Order, Instrument, статусы заказа, PortfolioSnapshot — минимальный набор
  типов, нужных контракту (чистая Java, без Spring!).
- Временное in-memory хранилище заявок/сделок — сервис в `infra/services` (этап 3 заменит
  на JPA); интерфейсов персистентности в `core` нет (пакет `core/spi` упразднён).
- `bks/contract`: DTO — **точное зеркало схем** (имена полей, типы, enum-строки `"1"/"2"`,
  вложенность, `ErrorResponse`). Jackson-аннотации допустимы; Spring — запрещён (ArchUnit).
- `bks/api`: контроллеры под префиксом брокера (пример: контрактный `/api/v1/x` монтируется
  как `/api/bks/v1/x`; точный префикс — конфигурация): token, portfolio, limits, orders
  (create/cancel/status/list, sync+async), trades/search, quotes, order-book, candles-chart,
  instruments/by-tickers, trading-schedule/status. Фильтр/interceptor проверки Bearer на
  API-путях брокера. Обработчик ошибок → `ErrorResponse` с корректными HTTP-кодами из StatusCodes.
- `bks/adapter`: маппинг DTO ↔ core (MapStruct, где целесообразно); токен-сервис
  (refresh-токены из конфига, access — JWT-подобный или opaque, TTL 24 ч — конфигурируемо).
- Рыночные данные в контроллерах: временно — порт-заглушка `MarketDataProvider` (хардкод/конфиг
  нескольких тикеров), настоящая реализация — этап 4.
- Конфиг: `emulator.brokers.bks.*` (список refresh-токенов с правами trade/read-only; значения — из переменных окружения, решение владельца).

## Явно НЕ делать

- Настоящее исполнение/проскальзывание (этап 5), PostgreSQL (этап 3), WebSocket, deprecated-,
  OTC-, discounts-, operation-history-эндпоинты, rate limiting (только заложить конфигурацию).

## Обязательные тесты

- `@WebMvcTest`/`@SpringBootTest` на каждый контроллер: happy path + 1–2 ошибки (401, 400, 404)
  — сверять JSON-поля с контрактом (jsonPath).
- Маппинг adapter ↔ contract (round-trip).
- Токен-обмен: валидный refresh → access; read-only токен не может создать ордер (403).
- ArchUnit продолжает проходить (contract без Spring/core).

## Definition of Done

- `./gradlew clean build` зелёный (включая spotlessCheck и ArchUnit).
- `./gradlew bootRun` + curl-сценарий: token → create order → status → portfolio → отмена —
  работает (приложить в PR/отчёте).
- DTO сверены со схемами снапшота покамерно (в отчёте — таблица «эндпоинт → файл схемы»).

## Проверка

```powershell
$env:JAVA_HOME=$env:MOEX_JAVA25_HOME
./gradlew clean build
```

## Подводные камни

- В схемах БКС многие enum — **строки** `"1".."5"`, числа — `number` (double) / `int64`; не
  «улучшать» типы. `side`: `"1"` покупка, `"2"` продажа.
- `clientOrderId` — UUID, генерирует клиент; биржевой id — формат `ГГММДД-КодКласса-Номер`.
- Sync-методы возвращают состояние заявки (как get-order-by-id), async — `ShortOrderResponse`.
- Кодировка: файлы писать UTF-8 **без BOM** (BOM ломает Spotless: `illegal character \ufeff`).
- Lombok/MapStruct — annotation processors: версии с поддержкой JDK 25 + связка
  `lombok-mapstruct-binding`; изменение `build.gradle` — по явному разрешению владельца.
- Не создавать `package-info.java` без нужды; если создан — проверить spotlessApply.
