# Отчёт этапа 4: Рыночные данные — LIVE / RECORD / REPLAY

Дата: 2026-11-02. Бриф: `stage-4-marketdata.md`. Ветка: `feature/stage-4-marketdata`.

## Итог

- [x] Порт времени `core/marketdata/Clock` (`SystemClock` + `VirtualClock`), порт
      `TradingCalendar`, модели `Trade`/`TradingDay`, снапшоты `MarketDataSnapshot`
      (QUOTE/ORDER_BOOK/TRADE), расширение `MarketDataProvider` (trades/ADV/tradingDays).
- [x] RECORD: `RecordingMarketDataProvider` (декоратор) → `market_data_snapshots` (JSONB),
      retention 7 дней.
- [x] REPLAY: `ReplayMarketDataProvider` по виртуальному таймлайну, детерминизм тестами
      (два прогона идентичны), seed/speed/start из конфига.
- [x] LIVE: клиенты AlgoPack + MOEX ISS (`infra/algoapi`), деградация до ISS без токена.
- [x] Торговый календарь: `TradingCalendarService`, кэш в БД (`trading_calendar`), источник —
      история торгов MOEX ISS.
- [x] Переключение режимов `emulator.marketdata.mode` (STUB по умолчанию / LIVE / RECORD /
      REPLAY) в `MarketDataConfiguration`; в REPLAY таймлайн продвигает `ReplayClockAdvancer`.
- [x] Миграция всех потребителей `java.time.Clock` на порт core `Clock`; ArchUnit-правило
      «системные часы читает только infra» (10 правил).
- [x] Документация: README (режимы), `docs/ARCHITECTURE.md` §9/§11, этот отчёт.

Проверка: `./gradlew test` — **548 тестов, 74 сюита, 0 падений/ошибок/пропусков**;
spotlessCheck зелёный.

## Контрактное ядро (коммит `3a34330`)

Порты и модели зафиксированы оркестратором до старта субагентов, чтобы параллельные части
компилировались независимо:

- `core/marketdata`: `Clock`, `SystemClock` (единственное место core с системным временем),
  `TradingCalendar`, `Trade`, `TradeDirection`, `TradingDay`, `SnapshotKind`,
  `MarketDataSnapshot` (sealed) + `QuoteSnapshot`/`OrderBookEvent`/`TradeEvent`.
- `MarketDataProvider extends TradingCalendar` + default-методы `trades`, `adv`,
  `tradingDays` — существующие реализации (заглушка этапа 2) менять не потребовалось.

## Субагенты (параллельно, модель `deepseek-v4.1-flash`)

| Агент | Область | Результат |
|---|---|---|
| CORE | `VirtualClock`, `RecordingMarketDataProvider`, `ReplayMarketDataProvider` + тесты | 36 тестов; покрытие ≥ 99 % строк |
| ALGO | `infra/algoapi`: клиенты AlgoPack/ISS, провайдеры, fallback, fixtures | 65 тестов; 93 % строк |
| PERSIST | `V2__market_data.sql`, сущности/репозитории/мапперы, `MarketDataSnapshotStore`, `TradingCalendarService` | 25 тестов + IT (PostgreSQL 18, `ddl-auto=validate`) |
| INTEGRATE | Миграция на core `Clock` (12 main + тесты), ArchUnit, `application.yml`, README, §9/§11 | целевые тесты + все IT зелёные |

Все патчи слиты в ветку оркестратором; worktree удалены.

## Отклонения от брифа и решения (внимание владельца)

1. **Эндпоинт `trading-schedule` у ISS не существует** (проверены `tradingschedule`,
   `timetable`, `sessions`, `calendar`, `dates` — все отдают карточку режима). Календарь
   строится по торговым дням из истории: `GET /iss/history/engines/{engine}/markets/{market}/
   boards/{board}/securities/{security}.json` (колонка `TRADEDATE`, эталонный тикер
   `calendar-security`, по умолчанию SBER).
2. **Границы сессии ISS не публикует** — окно основной сессии задаётся настройками
   `session-open`/`session-close` (умолчания 10:00–18:50 МСК), конвертация в UTC через
   `Europe/Moscow`.
3. **Домен `iss.moex.ru` выведен из эксплуатации** (не разрешается в DNS) — рабочий
   `iss.moex.com` (везде в дефолтах и `application.yml`).
4. **Текущий/будущие дни не пишутся в кэш календаря** как неторговые: история за сегодня
   появляется после закрытия, иначе день был бы навсегда «выходным». Осознанный компромисс —
   диапазоны, захватывающие текущий день, повторно ходят в ISS.
5. **ADV** — средний дневной объём в штуках (Σ `volume × lotSize` / число торговых дней в
   ответе) за последние 30 торговых дней (в REPLAY — из сделок таймлайна); ошибка источника →
   `ZERO` + warn.
6. **Направление сделки** ISS/AlgoPack не всегда отдаёт сторону: `BUYSELL=B→BUY`, `S→SELL`,
   пусто → `BUY` (допущение).
7. **Авторизация AlgoPack** — решение владельца (2026-11-02): `Authorization: Bearer <APIKEY>`;
   шлюз `algo-api.moex.com` остаётся по ADR-8 (переопределяется конфигурацией).
8. **Таймфреймы ISS/AlgoPack** поддерживают интервалы 1/10/60/24/7/31/4(квартал); M5/M15/M30/H4
   → пустая серия + warn.
9. **Валюта**: `CURRENCYID=SUR` нормализуется в RUB; `tradingStatus` всегда 17 (в API
   строковый статус).
10. **`market_data_snapshots.payload` — JSONB** (структура определяется доменными record-ами
    снапшотов); колонки `ts/kind/ticker/class_code` продублированы для индекса таймлайна
    `(ticker, class_code, ts)`.
11. **REPLAY**: справочник инструментов берётся из таблицы `instruments` по парам
    (ticker, classCode) таймлайна; календарь — из кэша `trading_calendar` (дозагрузка ISS
    возможна); `replay.start` пусто → старт с первого снапшота.
12. **Стопка LIVE в RECORD/REPLAY**: Fallback(AlgoPack, ISS) → Recording-декоратор →
    store; в REPLAY провайдер читает только запись (сеть не используется).

## Тесты (обязательные из брифа)

- Клиенты AlgoPack/ISS — на моках HTTP (JDK `HttpServer` + fixtures реальных ответов ISS;
  AlgoPack — по схемам доков, токена нет): ретраи, таймауты, заголовки, парсинг табличной схемы.
- `VirtualClock` + `ReplayMarketDataProvider`: детерминизм (два прогона идентичны), порядок
  событий, невидимость будущих снапшотов, поведение вне торговой сессии.
- RECORD → REPLAY round-trip: записал → воспроизвёл = те же котировки.
- ADV-расчёт на fixture-истории.
- `TradingCalendarService`: кэш без лишних HTTP-хитов, дозагрузка недостающего диапазона,
  отказ сети → частичный результат без исключения.
- ArchUnit: 10 правил, включая «системные часы читает только infra».

## DoD

- LIVE-демонстрация: `bootRun` в режиме LIVE (без токена AlgoPack → деградация до ISS),
  PostgreSQL 18 в Docker, сквозные вызовы через контракт БКС — см. транскрипт ниже.

### Curl-лог (LIVE, ISS-fallback, без токена AlgoPack)

```
$env:EMULATOR_MARKETDATA_MODE='LIVE'; $env:BKS_REFRESH_TOKEN_READ='demo-refresh'
./gradlew bootRun   # http://127.0.0.1:18080

POST /api/bks/trade-api-keycloak/realms/tradeapi/protocol/openid-connect/token
     (client_id=trade-api-read, grant_type=refresh_token)
  -> 200 {"token_type":"bearer","expires_in":86400,"access_token":"bks-…"}

POST /api/bks/v1/instruments/by-tickers {"tickers":["SBER","GAZP"]}  (Bearer)
  -> 200, реальный справочник ISS: SBER/GAZP, boards TQBR/MOEX, ISIN RU0009029540,
     lotSize 10 (GAZP), minimumStep 0.01, currency RUB

GET /api/bks/v1/candles-chart?ticker=SBER&classCode=TQBR&timeFrame=D
    &startDate=2026-09-01T00:00:00Z&endDate=2026-11-02T00:00:00Z  (Bearer)
  -> 200, 59 реальных дневных свечей ISS. Первая: time 2026-08-31T21:00:00Z, open 276.03,
     close 272.21, volume 26826095. Последняя: time 2026-11-01T21:00:00Z, close 290.85.

GET /api/bks/v1/trading-schedule/status  (Bearer)
  -> 200 {"tradingSessionTypeId":1,"tradingSessionStatus":"CLOSE","nextSessionDate":"…"}
     (календарь из кэша trading_calendar; источник — история торгов ISS)

GET /api/bks/v1/order-book?ticker=SBER&classCode=TQBR&depth=5  (Bearer)
  -> 500 INTERNAL_SERVER_ERROR: стакан ISS отдаётся только по подписке (проверено прямым
     запросом ISS — HTML-заглушка для неподписчиков), AlgoPack недоступен без токена.
     Ограничение бесплатных источников, зафиксировано ниже.
```

Примечания прогона:

- Порт 8080 в этом окружении блокируется локальным фильтром (RST на запрос), демо
  выполнялось на 18080 (`SERVER_PORT`).
- Во время прогона найден и исправлен баг `IssClient.candles`: ISS принимает `from`/`till`
  только в формате даты (`yyyy-MM-dd`); передача даты-времени молча возвращает пустой набор
  свечей. Регресс-тест усилен (`IssClientTest` проверяет точный формат параметров).
- `nextSessionDate` в статусе сессии = текущему моменту, когда следующего торгового дня ещё
  нет в кэше календаря (завтрашний день появляется в истории ISS только после закрытия) —
  осознанный компромисс, см. решения ниже.

## Решения владельца (2026-11-02) — закрывают открытые вопросы

1. **Авторизация AlgoPack**: при запуске будет API-ключ; формат заголовка —
   `Authorization: Bearer <APIKEY>` (вместо `X-AlgoPack-Token`). Код обновлён:
   `AlgoPackClient` шлёт Bearer при непустом ключе; ключ — только `ALGO_PACK_TOKEN` из env.
2. **M5/M15/M30/H4 не агрегировать**: оставлены пустыми (серия + warn) до тарифа AlgoPack —
   поведение без изменений.
3. **Окно основной сессии — из API биржи**: `TradingCalendarService` теперь берёт границы
   сессии каждого торгового дня из минутных свечей MOEX ISS эталонного инструмента
   (открытие — первый бар дня, закрытие — последний бар вечернего фрагмента с 18:00 МСК;
   например, для SBER 2026-10-30: 06:59–23:49:59 МСК). Настройки
   `session-open`/`session-close` остались только как fallback при недоступности данных биржи.
   Ограничение: ISS не публикует расписание отдельным эндпоинтом (проверено по
   `iss/reference`), а минутные свечи обрезаются на 500 строк — поэтому закрытие берётся
   вторым запросом с 18:00, а открытие — первым баром запроса по дате.

## Открытые вопросы владельца (закрыты выше)

- ~~Формат авторизации AlgoPack при наличии токена (`X-AlgoPack-Token` vs Bearer, шлюз).~~ →
  `Authorization: Bearer <APIKEY>`.
- ~~M5/M15/M30/H4: агрегировать из минутных свечей или оставить пустыми до тарифа AlgoPack.~~ →
  не агрегировать, пусто до тарифа.
- ~~Окно основной сессии (10:00–18:50 МСК) — уточнить у брокера/биржи.~~ → из API биржи
  (минутные свечи), 10:00–18:50 — fallback.
