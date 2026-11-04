# Отчёт этапа 6: Отчётность и метрики

Дата: 2026-11-04. Бриф: `stage-6-reporting.md`. Ветка: `feature/stage-6-reporting`.
Коммит: ожидает подтверждения владельца.

## Итог

- [x] `core/reporting`: `MetricsCalculator` (CAGR, Sharpe, Sortino, Max Drawdown, Calmar),
      `TradeJournal` + `FifoMatcher` (win rate, разбивка по стратегиям), `BacktestComparator`.
- [x] Журнал сделок: опорная цена стакана и цена после проскальзывания раздельно, комиссия
      отдельным полем, `strategyId` из `X-Strategy-Id`, выгрузка JSON/CSV.
- [x] `strategyId`: заголовок `X-Strategy-Id` (расширение контракта БКС), `orders.strategy_id`.
- [x] `fills.reference_price`: цена до проскальзывания; старые сделки читаются как нулевое
      проскальзывание.
- [x] Персистентность: миграция `V4__reporting.sql` (`reports`, `backtest_results`, индексы).
- [x] Внутренний API `/api/internal/reports/**` (без токена БКС) и файловый экспорт.
- [x] Тесты: 673 теста, 89 сюит, 0 падений/ошибок/пропусков; `spotlessCheck` зелёный;
      JaCoCo — bundle 92.9% инструкций / 76.8% ветвей, `core.reporting` — 95.3% / 77.5%.

## Формулы и контракты

Полное описание — `docs/reporting.md`: формулы, неопределённые значения (`null`), знак
проскальзывания, FIFO, JSON-контракт бэктеста, формат CSV, внутренний API и схема хранения.

## Стратегия и проскальзывание

- `X-Strategy-Id` читается в `BksOrderController` для `POST /v1/orders` и `/orders/sync`;
  пустое значение — `null`, длина ограничена 64 символами. Контрактные DTO не менялись.
- `MatchingEngine` возвращает `MatchFill(price, referencePrice, quantity)`: `referencePrice` —
  цена уровня стакана до модели проскальзывания. Для лимитных сделок цены совпадают.
- `FillService` сохраняет `reference_price`; журнал считает
  `slippagePerUnit = price - reference` для BUY и `reference - price` для SELL.

## Журнал и FIFO

- `TradeJournal` агрегирует сделки периода, суммарное проскальзывание и комиссии.
- `FifoMatcher` сопоставляет входы/выходы по каждому инструменту, поддерживает частичные
  закрытия и короткие позиции; комиссии распределяются по количеству и вычитаются из PnL.
- Win rate — доля прибыльных закрытых пар (строго `pnl > 0`); сделки без метки попадают в
  группу `(none)`.

## Сравнение с бэктестом

- Вход — `metrics` или `nav` (JSON-контракт в `docs/reporting.md`).
- Отклонение — абсолютное и относительное; порог по умолчанию 30%.
- `PASS` требует хотя бы одной сопоставимой метрики и всех отклонений в пороге.
- Результат сохраняется в `backtest_results` вместе с исходным входом.

## Проверка

- `./gradlew clean build` — BUILD SUCCESSFUL; `./gradlew test --rerun-tasks` — 673 теста,
  89 сюит, 0 падений/ошибок/пропусков.
- JaCoCo: bundle 92.9% инструкций / 76.8% ветвей; `core.reporting` — 95.3% / 77.5%.
- Интеграционный REPLAY-сценарий: `ReplayReportIntegrationIT` — две сессии, market buy/sell
  SBER, закрытие NAV обоих дней, отчёт с проскальзыванием, комиссией и FIFO win rate.

### Пример JSON-отчёта REPLAY-прогона

```json
{
  "accountId": "IT-REPLAY-REPORT",
  "label": "REPLAY-IT",
  "generatedAt": "2026-11-03T10:00:00.100Z",
  "periodFrom": "2026-11-02",
  "periodTo": "2026-11-03",
  "navSeries": [
    {"date": "2026-11-02", "nav": 999993.54},
    {"date": "2026-11-03", "nav": 1000032.11}
  ],
  "metrics": {
    "cagr": 0.009767,
    "sharpe": null,
    "sortino": -15.874508,
    "maxDrawdown": 0.000000,
    "calmar": null,
    "observations": 2
  },
  "journal": [
    {
      "executedAt": "2026-11-02T10:00:00Z",
      "orderId": "20261102-TQBR-796285400011",
      "clientOrderId": "2ae4aff9-c7c0-4e73-aff5-9aa22863ad5a",
      "ticker": "SBER",
      "classCode": "TQBR",
      "side": "BUY",
      "quantity": 10,
      "referencePrice": 285.510000,
      "price": 285.860481,
      "slippagePerUnit": 0.350481,
      "slippageTotal": 3.50,
      "commission": 2.86,
      "strategyId": "replay-strategy"
    },
    {
      "executedAt": "2026-11-03T10:00:00Z",
      "orderId": "20261103-TQBR-796285400012",
      "clientOrderId": "e7e4f7ed-a47a-4070-ac6e-e1cdb46aba15",
      "ticker": "SBER",
      "classCode": "TQBR",
      "side": "SELL",
      "quantity": 10,
      "referencePrice": 289.990000,
      "price": 289.646878,
      "slippagePerUnit": 0.343122,
      "slippageTotal": 3.43,
      "commission": 2.90,
      "strategyId": "replay-strategy"
    }
  ],
  "slippageTotal": 6.93,
  "commissionTotal": 5.76,
  "closedTrades": [
    {
      "ticker": "SBER",
      "classCode": "TQBR",
      "strategyId": "replay-strategy",
      "entrySide": "BUY",
      "quantity": 10,
      "entryPrice": 285.860481,
      "exitPrice": 289.646878,
      "pnl": 32.10,
      "entryAt": "2026-11-02T10:00:00Z",
      "exitAt": "2026-11-03T10:00:00Z",
      "entryOrderId": "20261102-TQBR-796285400011",
      "exitOrderId": "20261103-TQBR-796285400012"
    }
  ],
  "winRate": 1.000000,
  "strategies": [
    {
      "strategyId": "replay-strategy",
      "realizedPnl": 32.10,
      "tradeCount": 2,
      "closedTradeCount": 1,
      "winRate": 1.000000
    }
  ],
  "backtest": null
}
```

## Отклонения и решения (внимание владельца)

1. `strategyId` передаётся заголовком `X-Strategy-Id` (допущение брифа); контракт БКС не
   расширялся. Если роутер передаёт метку иначе — согласовать до этапа 7.
2. Безрисковая ставка — константа `0.16` в конфиге, а не запрос к ЦБ (временное решение №25).
3. Неопределённые метрики — `null`, а не `0`/`NaN` (временное решение №26).
4. Внутренний API без аутентификации, доступ ограничивается сетевым контуром; формат ошибок
   внутренний, `InternalReportExceptionHandler` имеет высший приоритет, `BksExceptionHandler`
   остаётся глобальным (временное решение №28).
5. Период журнала — календарные дни UTC, как у комиссии дня NAV (временное решение №16/27).
6. `reports` и `backtest_results` хранят payload в JSONB; отдельные таблицы строк журнала не
   вводятся.
7. Файловый экспорт выполняется после сохранения отчёта; при ошибке записи транзакция
   откатывается, но осиротевшие файлы возможны.
8. Найден и исправлен дефект REPLAY, проявившийся в интеграционном тесте: загрузка таймлайна
   использовала `Instant.MAX`, который Hibernate не может преобразовать в `java.sql.Timestamp`
   (`long overflow`). Верхняя граница заменена на `9999-12-31T23:59:59Z`
   (`MarketDataConfiguration`).
9. Профиль `test` ограничивает Hikari pool двумя соединениями: Spring кэширует контексты
   между IT-классами, и десять пулов по 10 соединений исчерпывали `max_connections` PostgreSQL.
   `BksExceptionHandler` остаётся глобальным (формат ошибок неизвестных путей БКС сохранён), а
   `InternalReportExceptionHandler` ограничен пакетом отчётов и имеет высший приоритет.

## Известные ограничения и follow-up

- Нет графиков/визуализации — только JSON/CSV (по брифу).
- Нет сравнения нескольких прогонов между собой — только прогон ↔ эталонный бэктест.
- Нет real-time стриминга метрик.
- `orders.status`-индекс остаётся открытым вопросом (V4 добавляет только
  `orders(account_id, strategy_id)` и `fills(account_id, executed_at)`).
- Аутентификация внутреннего API — кандидат на этап 7/8.
