# Отчёт этапа 5: Модель исполнения ордеров

Дата: 2026-11-04. Бриф: `stage-5-execution.md`. Ветка: `feature/stage-5-execution`.
Коммит — после явного подтверждения владельца.

## Итог

- [x] `core/execution`: `MatchingEngine`, `SlippageModel`/`SpecSlippageModel`,
      `LiquidityPolicy`/`AdvLiquidityPolicy`, `MatchFill`/`MatchResult`, события жизненного цикла.
- [x] Маркет: немедленный кросс стакана (buy — ask, sell — bid) с проскальзыванием только в
      неблагоприятную сторону.
- [x] Проскальзывание: база 0.05–0.15 % + тир по размеру ордера относительно ADV
      (`<1 % → +0.05 %`, `1–5 % → +0.1 %`, `>5 % → +0.2 %`); seed фиксирует генератор.
- [x] Лимит: ожидание пересечения, исполнение по цене «лимит или лучше», частичные исполнения;
      остаток лимитки остаётся активным.
- [x] FOK: all-or-nothing; при нехватке объёма — `REJECTED` без частичных сделок.
- [x] Ликвидность: по умолчанию `REJECT` + HTTP 429 `RESOURCE_EXHAUSTED`; `SPLIT` — настройкой.
- [x] Жизненный цикл: `NEW → PARTIALLY_FILLED → FILLED / CANCELED / REJECTED`, журнал
      `order_events`; GTD по `expiryDate`, DAY — при закрытии сессии.
- [x] Проводка в портфель: `FillService.fillAll` применяет сделки матчинга одной транзакцией,
      комиссия и позиция обновляются существующим портфельным кодом.
- [x] BKS: create через `OrderPlacementService`, `expiryDate` в запросе/ответе, 429 при ADV.
- [x] Тесты: 619 тестов, 81 сюита, 0 падений/ошибок/пропусков; `spotlessCheck` зелёный.

## Реализованная модель (принцип № 6 AGENTS.md)

| Требование брифа | Реализация | Проверка |
|---|---|---|
| Маркет buy по ask + slippage | `MatchingEngine.match`, `slippedPrice` | `MatchingEngineTest.marketBuyCrossesAskWithAdverseSlippage` |
| Маркет sell по bid − slippage | то же, множитель `1 − rate` | `marketSellCrossesBidWithAdverseSlippage` |
| База 0.05–0.15 % | `SpecSlippageModel` + `Random(seed)` | `SpecSlippageModelTest.baseComponentStaysWithinSpecifiedRange` |
| Тиры `<1 / 1–5 / >5 %` ADV | `SpecSlippageModel.tier` | `appliesLowTier…`, `appliesMidTier…`, `appliesHighTier…` |
| Тир по размеру ордера, не по куску стакана | в `MatchingEngine` в модель передаётся `target` (объём прохода) | `slippageTierUsesWholeOrderSizeNotBookChunk` |
| Детерминизм REPLAY | seed из `emulator.marketdata.replay.seed` в `ExecutionConfiguration` | `replayWithSameSeedProducesIdenticalFills`, `sameSeedProducesSameSequence` |
| Лимит ждёт пересечения | `crosses` + `RemainderAction.KEEP` | `limitBuyDoesNotCrossWhenAskIsAboveLimit` |
| Лимит по «лимит или лучше» | исполнение по цене уровня стакана | `limitBuyExecutesAtAskWhenCrossed`, `crossedLimitBuyExecutesAtLimitOrBetterWithoutSlippage` (IT) |
| Частичное исполнение лимитки | остаток активен | `limitPartialFillKeepsRemainderActive` |
| Маркет-остаток снимается | `RemainderAction.CANCEL` → `OrderService.cancel` | `marketPartialFillCancelsRemainder`, `executeCancelsUnfilledMarketRemainder` |
| FOK all-or-nothing | `fok && left > 0 → reject` | `fokRejectsWhenVolumeIsInsufficient`, `fokIsRejectedWhenBookCannotFillWholeVolume` (IT) |
| Ликвидность REJECT | `AdvLiquidityPolicy` + `LiquidityExceededException` | `rejectsSixPercentOfAdvByDefault`, `rejectsOrderAboveAdvBeforeSaving` |
| Ликвидность SPLIT | части не больше порога | `splitsSixPercentOfAdvIntoParts`, `splitPolicyProducesSeriesOfParts` |
| HTTP 429 `RESOURCE_EXHAUSTED` | `BksExceptionHandler.liquidityExceeded` | `BksExceptionHandlerTest.liquidityExceededMapsTo429ResourceExhausted` |
| GTD по `expiryDate` | `Order.expiryDate`, `ExecutionServiceImpl.isExpired` | `gtdOrderIsExpiredAfterExpiryDate` (IT), `tickExpiresGtdOrderAfterExpiryDate` |
| DAY при закрытии сессии | `isExpired` + `TradingSession.open` | `tickExpiresDayOrderWhenSessionCloses` |
| orderType 3–5 — заглушка | `OrderType.isSupported` + сообщение `NOT_IMPLEMENTED` и TODO | `OrderTest.rejectsUnsupportedOrderType` |

## Жизненный цикл и журнал

Миграция `V3__execution.sql`:

- `orders.expiry_date DATE` — только для GTD, для DAY/FOK остаётся `NULL`;
- `order_events(id, order_id → orders, event_type, previous_status, status, quantity, price,
  reason, occurred_at)` + индекс `(order_id, occurred_at, id)`.

События пишутся в той же транзакции, что и изменение заявки (`OrderEventRecorder` без
собственного `@Transactional`):

- `CREATED` — при сохранении заявки;
- `PARTIALLY_FILLED` / `FILLED` — на каждую сделку;
- `CANCELED` — отмена клиентом/сервисом, с причиной и остатком;
- `REJECTED` — FOK, ликвидность, недостаток средств, ошибка fill;
- `EXPIRED` — снятие DAY/GTD, статус заявки `CANCELED`.

`FillService.fillAll` выполняет все сделки одного прохода в одной транзакции: ошибка откатывает
весь набор и не оставляет частичных записей. `ExecutionServiceImpl` намеренно не оборачивает
проход целиком в транзакцию: reject/cancel — отдельные транзакции, чтобы сбой одной заявки не
откатывал уже исполненные соседние.

## BKS и портфель

- `POST /v1/orders` и `/orders/sync` идут через `OrderPlacementService`:
  1) предпроверка ADV до сохранения; при `REJECT` — `LiquidityExceededException` → 429;
  2) сохранение заявки;
  3) немедленная попытка исполнения; ошибка исполнения не возвращает 4xx/5xx — заявка
     сохраняется и переводится в `REJECTED` (best-effort).
- `BksOrderMapper` разбирает `expiryDate` ISO `yyyy-MM-dd`, отдаёт его в `OrderData`.
- Исполнение использует существующий `FillService`: цена после проскальзывания становится ценой
  сделки, комиссия считается тарифом портфеля и хранится отдельным полем `fills.commission`.

## Конфигурация

`emulator.execution.*` (значения по умолчанию — `application.yml`):

| Ключ | Назначение |
|---|---|
| `scheduler-enabled` | фоновый проход; `false` в профиле `test` |
| `tick-ms` | период `ExecutionScheduler` |
| `order-book-depth` | глубина стакана для матчинга |
| `liquidity-policy` | `REJECT` (по умолчанию) или `SPLIT` |
| `liquidity-threshold-percent` | порог ADV, по умолчанию 5 |
| `slippage.seed` | seed STUB; REPLAY берёт `marketdata.replay.seed` |
| `slippage.base-min` / `base-max` | базовая доля 0.0005–0.0015 |

`ExecutionScheduler` включается только при `scheduler-enabled=true`
(`@ConditionalOnProperty`, `matchIfMissing=true`).

## Проверка

- `./gradlew spotlessCheck test` — **619 тестов, 81 сюита, 0 падений/ошибок/пропусков**.
- JaCoCo: bundle 92 % инструкций / 77 % ветвей; `core.execution` — 94 % / 84 %.
- Новые интеграционные сценарии:
  - `ExecutionFlowIntegrationIT`: HTTP create → матчинг → портфель/журнал; лимит по лучшей цене;
    FOK reject; GTD expiry; market sell без позиции → `REJECTED` с сохранением заявки.
  - `JpaOrderServiceImplIT`: `CREATED/CANCELED/REJECTED/EXPIRED`, round-trip `expiryDate`.
  - `RestartIntegrationIT`, `TradingFlowIntegrationIT`: очистка `order_events` перед `orders`
    (новый внешний ключ V3).

## Отклонения и решения (внимание владельца)

1. **Политика ликвидности по умолчанию — `REJECT` + 429 `RESOURCE_EXHAUSTED`** (решение
   владельца); `SPLIT` доступен настройкой.
2. **Триггер лимиток** — планировщик + попытка при постановке; в REPLAY проход идёт по
   виртуальным тикам.
3. **Маркет при нехватке объёма** исполняет доступное, остаток снимается (`CANCELED`), а не
   висит активным.
4. **Ошибка исполнения при постановке** — best-effort: заявка сохраняется и переводится в
   `REJECTED`; create не возвращает 4xx/5xx. Исключение — ADV при `REJECT` (429 до сохранения).
5. **GTD и FOK реализованы полностью**; DAY снимается при закрытии сессии (`TradingSession`).
6. **Тир проскальзывания считается по объёму прохода**, а не по куску стакана: иначе крупный
   маркет-ордер получал бы тир `<1 %` на каждом уровне. Исправлено в ходе ревью.
7. **Заглушка orderType 3–5**: явное сообщение `NOT_IMPLEMENTED` + TODO; контракт БКС не имеет
   одноимённого `ErrorType`, поэтому ответ — 422 `VALIDATION_ERROR`.
8. **STUB не отдаёт ADV** (`adv() = 0`), поэтому ликвидность/429 проверяются unit-тестами с
   управляемым ADV, а не HTTP-сценарием. В LIVE/REPLAY ADV приходит из рыночных данных.
9. **`ExecutionServiceImpl` не `@Transactional`** осознанно: `fillAll` атомарен, reject/cancel —
   отдельные транзакции.
10. **Очистка тестовых данных** теперь удаляет `order_events` до `orders` из-за внешнего ключа V3.

## Известные ограничения и follow-up

- Нет симуляции позиции в очереди стакана — допущение брифа «исполнение при пересечении цены».
- Нет стоп-лоссов/условных ордеров и WebSocket-нотификаций (явно вне скоупа).
- DAY-снятие зависит от `TradingSession.open`; заглушка STUB всегда отдаёт открытую сессию,
  поэтому DAY проверяется unit-тестом, а GTD — интеграционным.
- Параллельные `tick` и постановка заявки сериализуются блокировкой счёта в `FillService`, но
  матчинг читает состояние заявки до блокировки. При гонке возможен повторный матчинг остатка;
  `fill` отклонит превышение, после чего остаток будет снят. Отдельная блокировка заявки на
  время матчинга — кандидат на следующий этап.
- Метрики исполнения (fill rate, slippage vs benchmark) — этап 6 (отчётность).
