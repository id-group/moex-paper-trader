# Снапшот документации БКС Trade API

Источник: https://trade-api.bcs.ru/ (Docusaurus). Снят 2026-10-29 по индексу `llms.txt`:
45 страниц в формате MDX (исходники страниц документации, включая встроенные OpenAPI-схемы).
В тот же день из webpack-чанков сайта извлечены недостававшие sidecar-схемы (43 JSON-файла) —
**снапшот полный**.

**Снапшот заморожен.** Файлы не редактировать — это источник истины для DTO пакета
`ru.moex.papertrader.bks.contract` (поля брать только из схем, не изобретать).

## Состав

- `llms.txt` — индекс страниц с описаниями
- `intro.md`, `special_info.md`, `restrictions.md` (rate limits), `mcp.md` — общие разделы
- `http/` — HTTP API:
  - `authorization.md` — обмен refresh-токена на access (Keycloak-эндпоинт
    `POST /trade-api-keycloak/realms/tradeapi/protocol/openid-connect/token`; access 24 ч, refresh 90 дней)
  - `portfolio.md`, `limits.md` — портфель и лимиты (схемы встроены в страницы; портфель —
    массив записей `type ∈ {moneyLimit, depoLimit, futuresLimit, futuresHolding, otcLimit}`
    в разрезе `term ∈ {T0, T1, T2, T365}`; там же общий контракт ошибки `ErrorResponse`)
  - `information/` (5 стр.) — справочник инструментов, статусы торгов, расписание (схемы встроены)
  - `operations/` — заявки: sync-варианты (`sync/`), статус по id, список; `trades/` — сделки
  - `market-data/` — котировки, стакан, свечи, обезличенные сделки
  - `get-instruments-discounts.md`, `get-operation-history.md`, `deprecated/` — прочее
- `websocket/` — WS-потоки: портфель, лимиты, маржинальные показатели, стакан/котировки/сделки/
  последняя свеча, статусы заявок, ping/pong (схемы встроены)
- `otc/` — внебиржевая торговля (эмулятором не реализуется)

## Sidecar-схемы (извлечены 2026-10-29)

Часть операционных страниц вместо встроенных схем ссылается на соседние JSON через
`require("./<имя>.RequestSchema.json" | *.StatusCodes.json | *.ParamsDetails.json)`.
Статически сайт эти файлы не отдаёт; они были извлечены из webpack-чанков сайта и лежат
рядом со страницами — 43 файла на 17 страницах:

- `http/operations/`: create, cancel-order, update-order, get-order-by-id, all-order-list
- `http/operations/sync/`: create-order-sync, cancel-order-sync, update-order-sync
- `http/trades/get-trades`
- `http/market-data/`: get-quotes, get-order-book, get-candles-chart, get-last-trades
- `http/get-operation-history`
- `http/deprecated/operations/`: cancel, status, update

Проверка полноты: `grep -r "require(" docs/bcs-api` — у каждой ссылки есть соседний `.json`.

## Методика повторного извлечения (если сайт пересоберут)

Хеши чанков меняются при каждой сборке сайта, поэтому при обновлении снапшота схемы
извлекаются заново:

1. В HTML любой страницы (например `https://trade-api.bcs.ru/http/operations/create`) найти
   `runtime~main.<hash>.js`; внутри функция `n.u=` содержит две карты — chunkId→имя и
   chunkId→хеш; URL чанков: `/assets/js/<имя|chunkId>.<hash>.js` (всего ~78).
2. Скачать все чанки; извлечь из них строковые литералы `JSON.parse('...')` (JS-строка
   с экранированием: `\\`, `\'`, `\uXXXX`, `\xHH`) и распарсить их.
3. **Особый случай:** RequestSchema GET-страниц без тела запроса — это плоские модули
   `e.exports={title:"Body"}` (без JSON.parse), их тоже нужно собирать.
4. Классифицировать нагрузки по ключам верхнего уровня: `responses` → StatusCodes,
   `body` / `title:"Body"` → RequestSchema, `parameters` → ParamsDetails.
5. Сопоставить чанк со страницей по маркеру скомпилированного MDX
   `method:"post",path:"/api/v1/..."` — пара (method, path) уникальна для всех страниц.
6. Сохранить файлы под точными именами из `require("./...")` рядом с `.md`.

Альтернативные источники: Postman-воркспейс (ссылка в `llms.txt`) или код роутера
(если в нём уже есть клиент БКС — его DTO точнее всего).
