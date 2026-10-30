# Бриф этапа 4: Рыночные данные — LIVE / RECORD / REPLAY

## Цель

Реальный источник котировок вместо заглушки: AlgoPack (+ MOEX ISS fallback) в LIVE, запись
потока в RECORD, детерминированное воспроизведение в REPLAY на виртуальном `Clock`.

## Прочитать до начала

1. `AGENTS.md`, `docs/ARCHITECTURE.md` — раздел 6 (ADR-8).
2. Документация AlgoPack: https://moexalgo.github.io/ (эндпоинты, авторизация `X-AlgoPack-Token`,
   форматы: все сделки, стакан, свечи, история) и MOEX ISS: https://iss.moex.ru/iss/reference/
   (свечи, справочник, расписание).
3. Код `core/marketdata` (порт из этапа 2) и `bks/api` контроллеры market-data.

## Скоуп

- `core/marketdata`: порт `MarketDataProvider` (BBO/last, стакан, свечи, история сделок, ADV,
  справочник, торговый календарь/расписание), порт `Clock` (+ `SystemClock`, `VirtualClock`),
  модель данных котировок (Quote, OrderBook, Candle, Trade — чистая Java).
- `infra/algoapi`: HTTP-клиент AlgoPack (токен — только env-переменная), клиент ISS как
  fallback/справочник; ретраи, таймауты, кэш справочника.
- Режимы: `LIVE` (опрос/подписка по конфигу), `RECORD` (декоратор: пишет входящие данные в
  `market_data_snapshots`), `REPLAY` (`ReplayMarketDataProvider` читает записи/историю и
  отдаёт по виртуальному таймлайну; VirtualClock синхронизирован с проигрывателем; seed
  генераторов — из конфига).
- Переключение режимов: `emulator.mode` + профиль Spring.
- ADV: расчёт среднего дневного объёма из истории (нужен этапу 5 для ликвидности).
- Торговый календарь/расписание сессий (для NAV этапа 3 и таймлайна REPLAY): источник —
  ISS trading-schedule; кэш в БД.

## Явно НЕ делать

- Матчинг/проскальзывание (этап 5) — только данные; WebSocket-стриминг наружу (контракт БКС WS —
  отдельный этап); платные тарифы AlgoPack не предполагать — деградация до ISS/записей.

## Обязательные тесты

- Клиенты AlgoPack/ISS: на моках HTTP (записи реальных ответов — fixtures в test-ресурсах).
- `VirtualClock` + `ReplayMarketDataProvider`: детерминизм (два прогона идентичны), порядок
  событий, поведение вне торговой сессии.
- RECORD: записал → REPLAY по записи = те же котировки (round-trip тест).
- ADV-расчёт на fixture-истории.

## Definition of Done

- LIVE: `GET /api/bks/v1/order-book?ticker=...` и quotes отдают реальные данные (или ISS-fallback)
  — показать curl-лог в отчёте.
- REPLAY: прогон записанного/исторического окна воспроизводится детерминированно (тест).
- Конфигурация режимов описана в `README.md`.

## Подводные камни

- Токен AlgoPack — секрет: только env, никогда в git/конфиг (правило AGENTS.md).
- Тайм-зоны: МСК (Europe/Moscow) — хранить UTC, отображать/группировать по МСК; `Clock` —
  единственный источник времени в core (никаких `System.currentTimeMillis()`/`LocalDateTime.now()`
  вне infra — добавить ArchUnit-правило).
- Лимиты/ретраи HTTP: не долбить AlgoPack чаще разрешённого (см. их docs), кэшировать справочник.
- Файлы — UTF-8 без BOM.
