# Requirements — moex-paper-trader

Источники: `docs/ARCHITECTURE.md` §5 (состав MVP), `docs/agent-briefs/stage-2-bks-contract.md`, `docs/bcs-api/` (схемы полей). Идентификаторы нужны для трассировки в ROADMAP и в планах фаз.

## v1 (фазы 2–7)

### Контракт БКС (фаза 2)

- [ ] **BKS-AUTH-01**: обмен refresh-токена на access (TTL 24 ч, настраивается) — `POST …/token`, ADR-7
- [ ] **BKS-AUTH-02**: read-only токен не может создавать и отменять заявки (403)
- [ ] **BKS-AUTH-03**: Bearer на всех путях `/api/bks/v1/**`; неверный или просроченный токен → 401
- [ ] **BKS-PORT-01**: `GET /api/bks/v1/portfolio` — массив лимитов по срокам T0/T1 (ADR-5)
- [ ] **BKS-PORT-02**: `GET /api/bks/v1/limits`
- [ ] **BKS-ORD-01**: создание заявки, синхронно и асинхронно (`orders`, `orders/sync`)
- [ ] **BKS-ORD-02**: отмена заявки, синхронно и асинхронно (`orders/cancel`, `orders/cancel/sync`)
- [ ] **BKS-ORD-03**: статус по `orderIdType` 1/2 — clientOrderId или биржевой номер (ADR-4)
- [ ] **BKS-ORD-04**: список заявок — `orders/search`
- [ ] **BKS-TRD-01**: сделки — `trades/search`
- [ ] **BKS-MD-01**: котировки, стакан, свечи — `quotes`, `order-book`, `candles-chart` (через заглушку `MarketDataProvider`)
- [ ] **BKS-REF-01**: `instruments/by-tickers`, `trading-schedule/status`
- [ ] **BKS-ERR-01**: ошибки в формате `ErrorResponse` (timestamp, traceId, type, errors[]) с HTTP-кодами из StatusCodes (ADR-6)
- [ ] **BKS-DTO-01**: DTO `bks/contract` — точное зеркало схем снапшота; сверка покамерно в отчёте фазы
- [ ] **BKS-CFG-01**: префикс `/api/bks/v1` и список refresh-токенов через `emulator.brokers.bks.*`; значения refresh-токенов — из переменных окружения (решение владельца, S3 в `docs/security-checklist.md`)

### Домен и портфель (фаза 3)

- [ ] **CORE-PF-01**: виртуальный портфель: кэш, позиции; расчёты T+1 — допущение песочницы (`docs/ARCHITECTURE.md` §8)
- [ ] **CORE-PF-02**: ГО фьючерсов (~10%, настраивается per-instrument) и вариационная маржа
- [ ] **INF-DB-01**: PostgreSQL 18, Spring Data JPA; миграции Flyway (кандидат, подтвердить на этапе 3)

### Рыночные данные (фаза 4)

- [ ] **MD-01**: режимы LIVE / RECORD / REPLAY с виртуальным `Clock` (ADR-8)
- [ ] **MD-02**: детерминизм REPLAY: фиксированный seed проскальзывания

### Исполнение (фаза 5)

- [ ] **EX-01**: маркет-ордер по стакану, проскальзывание 0.05–0.15% плюс тиры по ADV (ADR-9)
- [ ] **EX-02**: лимитный ордер в очереди, исполнение по цене «лимит или лучше»
- [ ] **EX-03**: объём свыше 5% ADV — отклонение или разбивка (`LiquidityPolicy`)

### Отчётность (фаза 6)

- [ ] **RPT-01**: NAV, CAGR, Sharpe, Sortino, Max Drawdown, Calmar, WinRate (FIFO)
- [ ] **RPT-02**: журнал сделок: цена до и после проскальзывания, комиссия, strategyId
- [ ] **RPT-03**: сравнение с бэктестом, порог расхождения 30%

### Интеграция (фаза 7)

- [ ] **INT-01**: сквозной прогон REPLAY и сверка с бэктестом

## Out of Scope

| Requirement | Reason |
|-------------|--------|
| WebSocket-потоки БКС | Отдельный этап после HTTP MVP (`docs/ARCHITECTURE.md` §5) |
| `orders/edit` (update) | Позже; не входит в MVP (`docs/ARCHITECTURE.md` §5) |
| `last-trades`, `instruments/by-type`, `daily-schedule` | Позже; не входит в MVP |
| Discounts, operation-history, OTC, MCP, deprecated-методы | Не реализуем; deprecated — по запросу роутера |
| Реальные заявки брокеру | Эмулятор исполняет виртуально |
| Rate limiting как работающий механизм | Только конфигурация, включается при необходимости (ADR-6) |

---
*Создано 2026-10-31 при discovery (модуль 2). Допущения и открытые вопросы — в `DISCOVERY.md`.*
