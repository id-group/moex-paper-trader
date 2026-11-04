# Отчётность и метрики (этап 6)

Документ фиксирует формулы, форматы и контракты отчётности. Реализация — `core/reporting`
(расчёты), `infra/services` (сборка и хранение), `reporting/api` (внутренний HTTP API).

## 1. Метрики

Вход — дневной ряд NAV (`nav_daily`) за период. Годовая база — **252 торговых дня**. Дневная
доходность считается по простой формуле:

```text
r_t = NAV_t / NAV_(t-1) - 1
```

| Метрика | Формула | Примечание |
|---|---|---|
| CAGR | `(NAV_last / NAV_first)^(252 / (N - 1)) - 1` | `N` — число точек ряда |
| Sharpe | `(mean(r) - rf/252) / stdev(r) × sqrt(252)` | выборочное `stdev` (`N - 2` в знаменателе) |
| Sortino | `(mean(r) - rf/252) / downside × sqrt(252)` | `downside = sqrt(mean(min(r - rf/252, 0)^2))` |
| Max Drawdown | `max((peak - NAV_t) / peak)` | просадка от исторического пика, доля ≥ 0 |
| Calmar | `CAGR / Max Drawdown` | от неокруглённого CAGR |
| Win Rate | `прибыльные закрытые FIFO-пары / все закрытые пары` | результат после комиссий |

Безрисковая ставка `rf` — годовая доля, по умолчанию `0.16` (16%, константа конфига
`emulator.reporting.risk-free-rate`). Порог сверки с бэктестом — `0.30` (30%,
`emulator.reporting.backtest-threshold`).

### Неопределённые значения

Если метрика математически не определена, возвращается `null` (в JSON — `null`), а не `0`:

- пустой или одноточечный ряд — все метрики `null`;
- любой NAV ≤ 0 — CAGR, Sharpe, Sortino, Max Drawdown, Calmar `null`;
- нулевое стандартное отклонение дневных доходностей — Sharpe `null`;
- нет убыточных дней относительно `rf` — Sortino `null`;
- нулевая максимальная просадка — Calmar `null`;
- нет закрытых FIFO-пар — Win Rate `null`.

Значения округляются до 6 знаков по `HALF_UP`; денежные суммы журнала — до 2 знаков.

## 2. Журнал сделок

Каждая сделка содержит:

- `executedAt`, `orderId`, `clientOrderId`, `ticker`, `classCode`, `side`, `quantity`;
- `referencePrice` — опорная цена стакана до проскальзывания;
- `price` — фактическая цена после проскальзывания;
- `slippagePerUnit` — `price - referencePrice` для BUY и `referencePrice - price` для SELL
  (положительное значение — издержка);
- `slippageTotal` — `slippagePerUnit × quantity`;
- `commission` — комиссия отдельным полем;
- `strategyId` — метка стратегии из заголовка `X-Strategy-Id` (может быть `null`).

Проскальзывание и комиссия не смешиваются: журнал хранит их раздельно. Сделки до этапа 6 не имеют
`reference_price`; для них проскальзывание считается нулевым (`referencePrice = price`).

### FIFO и win rate

Входы и выходы сопоставляются по каждому инструменту в хронологическом порядке. Входящая сделка
сначала закрывает противоположные открытые лоты, остаток открывает новый лот. Частичное закрытие
создаёт отдельную закрытую пару. Комиссия каждой сделки распределяется по количеству
(`commission / quantity`) и вычитается из результата пары. Метка стратегии пары — метка сделки
входа. Незакрытые лоты в win rate не входят (нереализованный результат).

`Win Rate` = число пар с `pnl > 0` / общее число закрытых пар. Нулевой результат считается
неприбыльным. Разбивка по стратегиям (`strategies`) содержит реализованный PnL, число сделок
журнала, число закрытых пар и win rate по каждой метке; сделки без метки попадают в группу
`(none)`.

## 3. Сравнение с бэктестом

Вход внешнего бэктеста (поле `backtest` запроса или JSON-файл):

```json
{
  "label": "external-backtest-2026-11-02",
  "nav": [
    {"date": "2026-10-30", "nav": 1000000.00},
    {"date": "2026-11-02", "nav": 1015000.00}
  ],
  "metrics": {
    "cagr": 0.105,
    "sharpe": 1.20,
    "sortino": 1.70,
    "maxDrawdown": 0.04,
    "calmar": 2.60,
    "winRate": 0.55
  }
}
```

Правила:

- нужен хотя бы один из блоков `nav` или `metrics`;
- если передан `metrics`, он имеет приоритет, а `nav` сохраняется в БД как исходные данные;
- если передан только `nav`, метрики рассчитываются тем же `MetricsCalculator`;
- сравниваются только метрики, присутствующие в обоих наборах и не равные `null`;
- относительное отклонение: `|actual - benchmark| / |benchmark|`;
- при `benchmark = 0` относительное отклонение не определено, метрика проходит только при
  `actual = 0`;
- `PASS`, если есть хотя бы одна сопоставимая метрика и все они уложились в порог 30%; пустое
  сравнение — `FAIL`.

Результат (`backtest` в отчёте):

```json
{
  "threshold": 0.30,
  "passed": true,
  "deviations": [
    {
      "metric": "cagr",
      "actual": 0.125000,
      "benchmark": 0.100000,
      "absoluteDeviation": 0.025000,
      "percentDeviation": 0.250000,
      "withinThreshold": true
    }
  ]
}
```

## 4. JSON-отчёт

Канонический вид `ReportPayload` (он же ответ `POST /api/internal/reports` и содержимое
файла `report-<id>.json`):

```json
{
  "accountId": "PAPER-0001",
  "label": "REPLAY-2026-11-02",
  "generatedAt": "2026-11-04T18:00:00Z",
  "periodFrom": "2026-10-30",
  "periodTo": "2026-11-02",
  "navSeries": [
    {"date": "2026-10-30", "nav": 1000000.00},
    {"date": "2026-11-02", "nav": 1002450.00}
  ],
  "metrics": {
    "cagr": 0.320000,
    "sharpe": 1.100000,
    "sortino": 1.600000,
    "maxDrawdown": 0.030000,
    "calmar": 10.600000,
    "observations": 2
  },
  "journal": [
    {
      "executedAt": "2026-11-02T10:15:30Z",
      "orderId": "20261102-TQBR-1",
      "clientOrderId": "00000000-0000-0000-0000-000000000001",
      "ticker": "SBER",
      "classCode": "TQBR",
      "side": "BUY",
      "quantity": 10,
      "referencePrice": 285.510000,
      "price": 285.710000,
      "slippagePerUnit": 0.200000,
      "slippageTotal": 2.00,
      "commission": 2.86,
      "strategyId": "momentum"
    }
  ],
  "slippageTotal": 2.00,
  "commissionTotal": 2.86,
  "closedTrades": [],
  "winRate": null,
  "strategies": [
    {
      "strategyId": "momentum",
      "realizedPnl": 0.00,
      "tradeCount": 1,
      "closedTradeCount": 0,
      "winRate": null
    }
  ],
  "backtest": null
}
```

## 5. Внутренний HTTP API

Префикс `/api/internal/reports` — брокер-нейтральный, **без токена БКС**. В продакшене доступ
ограничивается сетевой политикой; отдельная аутентификация не вводится (решение этапа 6).

| Метод | Путь | Назначение |
|---|---|---|
| `POST` | `/api/internal/reports` | сформировать, сохранить и экспортировать отчёт |
| `GET` | `/api/internal/reports?accountId=...` | список сохранённых отчётов |
| `GET` | `/api/internal/reports/{id}` | сохранённый отчёт |
| `GET` | `/api/internal/reports/{id}/journal.csv` | CSV-журнал сделок |

Пример запроса:

```json
{
  "accountId": "PAPER-0001",
  "label": "REPLAY-2026-11-02",
  "from": "2026-10-30",
  "to": "2026-11-02",
  "backtest": {
    "label": "external-backtest-2026-11-02",
    "metrics": {"cagr": 0.10, "sharpe": 1.0}
  }
}
```

Ответ — `ReportResponse`:

```json
{
  "id": 1,
  "report": { "...": "ReportPayload" },
  "files": {
    "json": "C:\\...\\build\\reports\\emulator\\report-1.json",
    "csv": "C:\\...\\build\\reports\\emulator\\report-1-journal.csv"
  }
}
```

Ошибки внутреннего API имеют формат `{"timestamp": "...", "status": 400, "message": "..."}` и не
используют контракт `ErrorResponse` БКС.

## 6. CSV-журнал

UTF-8 без BOM, разделитель — запятая, десятичный разделитель — точка, перевод строки — `\n`.
Заголовок:

```text
executedAt,orderId,clientOrderId,ticker,classCode,side,quantity,referencePrice,price,slippagePerUnit,slippageTotal,commission,strategyId
```

Поля с запятой, кавычкой или переводом строки экранируются двойными кавычками. Пустой период даёт
файл только с заголовком.

## 7. Хранение

Миграция `V4__reporting.sql`:

- `orders.strategy_id VARCHAR(64)` — метка из `X-Strategy-Id`;
- `fills.reference_price NUMERIC(19,6)` — опорная цена до проскальзывания;
- `reports` — сохранённый `ReportPayload` в `payload JSONB`;
- `backtest_results` — вход бэктеста и таблица отклонений в `payload JSONB`, ссылка на отчёт.

Файлы экспорта пишутся в `emulator.reporting.output-dir` (по умолчанию
`build/reports/emulator`) с именами `report-<id>.json` и `report-<id>-journal.csv`.
