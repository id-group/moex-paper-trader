---
name: BCS Portfolio
description: Спецификация виртуального портфеля и портфеля БКС — лимиты moneyLimit/depoLimit/futuresLimit/futuresHolding/otcLimit по срокам T0/T1/T2/T365, ГО, вариационная маржа, NAV, комиссии. Загружать перед работой с core/portfolio, core/commission, bks/adapter (portfolio/limits).
---

# Портфель — спецификация

## Специфика БКС (живёт только в `bks/`)

- Портфель — массив записей `type ∈ {moneyLimit, depoLimit, futuresLimit, futuresHolding,
  otcLimit}` в разрезе `term ∈ {T0, T1, T2, T365}`.
- Форма ответа — `docs/bcs-api/http/portfolio.md` + `limits.md` (схемы встроены в страницы;
  там же общий контракт ошибки `ErrorResponse`).
- В `core` — брокер-независимый снапшот портфеля (`PortfolioSnapshot`); БКС-форма
  собирается из него в `bks/adapter`.

## Виртуальный портфель (core/portfolio, чистая Java)

- Кэш по валютам, позиции (акции + фьючерсы), блокировки.
- ГО фьючерсов: дефолт ~10%, настраивается per-instrument.
- Вариационная маржа: ежедневная переоценка.
- Маржа-проверка перед сделкой: не хватает обеспечения → отказ.
- NAV-снапшот по закрытию основной сессии (торговый календарь — упрощённо конфигом,
  полная интеграция — этап 4).

## Комиссии (core/commission)

- `CommissionCalculator` + тариф профиля: процент, минимальная комиссия, биржевой сбор
  фьючерсов. Дефолт акций — 0.1% (настраивается; инвариант — принцип №7 AGENTS.md).
- Комиссия — отдельным полем в `Fill`.
- Корпоративные события (дивиденды/сплиты) — заглушка с TODO, интерфейс
  `CorporateActionHandler`.

## Связанное

- `docs/ARCHITECTURE.md` §4, §8, §9 (ADR-5, ADR-10)
- `docs/agent-briefs/stage-3-portfolio.md` — бриф этапа 3 (персистентность, JPA, миграции)
