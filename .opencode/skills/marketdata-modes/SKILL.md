---
name: Market Data Modes
description: Режимы котировок LIVE/RECORD/REPLAY, виртуальный Clock, фиксируемый seed проскальзывания, клиенты AlgoPack и MOEX ISS, торговый календарь. Загружать перед работой с core/marketdata, infra/algoapi, таймлайном REPLAY.
---

# Рыночные данные — LIVE / RECORD / REPLAY

## Режимы

- `LIVE` — реальное время: AlgoPack (основной источник) + MOEX ISS (fallback/справочник).
- `RECORD` — LIVE + запись входящего потока в хранилище (`market_data_snapshots`).
  Нужен, чтобы сравнение эмулятора с бэктестом шло по тем же данным.
- `REPLAY` — воспроизведение записи по виртуальному таймлайну; `VirtualClock`
  синхронизирован с проигрывателем; seed генераторов (в т.ч. проскальзывания) —
  из конфига, **детерминизм обязателен**.

Переключение режимов: `emulator.mode` + профиль Spring.

## Порты (core/marketdata, чистая Java)

- `MarketDataProvider`: BBO/last, стакан, свечи, история сделок, ADV (нужен этапу 5
  для ликвидности), справочник инструментов, торговый календарь/расписание.
- `Clock`: `SystemClock`, `VirtualClock`.
- Модель данных: Quote, OrderBook, Candle, Trade.

## Инфраструктура (infra/algoapi)

- HTTP-клиент AlgoPack: токен (`X-AlgoPack-Token`) — **только env-переменная**, никогда
  в git/конфиг; ретраи, таймауты, кэш справочника.
- MOEX ISS: свечи, справочник, расписание; trading-schedule — источник торгового
  календаря (для NAV этапа 3 и таймлайна REPLAY), кэш в БД.

## Связанное

- `docs/ARCHITECTURE.md` §6 (ADR-8)
- `docs/agent-briefs/stage-4-marketdata.md` — бриф этапа 4
- Документация: https://moexalgo.github.io/ и https://iss.moex.ru/iss/reference/
