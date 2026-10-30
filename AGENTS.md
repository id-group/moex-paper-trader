# AGENTS.md — Инструкции для AI-агентов

## Проект и стек

**moex-paper-trader** — эмулятор брокера для paper trading на Московской Бирже.
Принимает запросы в формате API брокера (первая реализация — **БКС Trade API**), виртуально
исполняет ордера по реальным котировкам ММВБ, ведёт портфель с комиссиями и проскальзыванием,
формирует отчётность. Каркас рассчитан на добавление других брокеров (Т-Банк, Алор, Финам)
без изменения ядра.

**Стек:**   
Java 25, Spring Boot 4.1.2, Gradle 9.8.2 (wrapper, один проект — без модулей),
База данных: PostgreSQL 18, 
Тестирование: JUnit 5 + Mockito  
*Дополнительно:*
SpringActuator - для мониторинга
Jackson — JSON сериализация
Lombok — уменьшение boilerplate
MapStruct — маппинг DTO
JaCoCo — покрытие тестами

**Котировки и информация о торгах** 
AlgoPack (https://moexalgo.github.io/) и MOEX ISS (https://iss.moex.ru). 
Контракт брокера: снапшот документации в `docs/bcs-api/`.

## Окружение (Windows) — ВАЖНО

- Перед любым запуском Gradle выставлять JDK 25:
  `$env:JAVA_HOME='C:\Users\id-gr\.jdks\liberica-full-25.0.4'`
- Все исходники и конфиги хранятся в UTF-8.

## Команды

```
./gradlew clean build    # сборка + тесты + проверка стиля
./gradlew bootRun        # запуск сервиса: http://localhost:8080, health: GET /health
./gradlew test           # все тесты
./gradlew test --tests "ru.moex.papertrader.core.portfolio.VirtualPortfolioTest"  # один тест
./gradlew spotlessApply  # автоформатирование (Google Java Format)
./gradlew spotlessCheck  # проверка стиля
```

## Архитектура

Один Gradle-проект, разделение — пакетами (модулей нет):

```text
src/main/java/ru/moex/papertrader/   # корень пакета — запуск: Application, Spring-конфигурация, /health
├── core/            # доменное ядро — ЧИСТАЯ JAVA, без Spring
│   ├── domain/      # Instrument, Order, Fill, Position, Portfolio
│   ├── portfolio/   # кэш, позиции, ГО и вариационная маржа, NAV
│   ├── execution/   # матчинг ордеров, проскальзывание, ликвидность
│   ├── marketdata/  # порт MarketDataProvider + порт Clock; LIVE/RECORD/REPLAY
│   ├── commission/  # модель комиссий
│   └── reporting/   # кривая NAV, метрики, журнал сделок
├── bks/             # адаптер БКС
│   ├── api/         # контроллеры под контракт trade-api.bcs.ru
│   ├── contract/    # DTO — точное зеркало docs/bcs-api
│   └── adapter/     # маппинг DTO ↔ домен core
└── infra/
    ├── repository/ # PostgreSQL (Spring Data JPA) 
    ├── model/      # модели для хранения в БД
    ├── dto/        
    ├── mapper/      
    ├── services/      
    └── algoapi/    # клиенты AlgoPack / MOEX ISS — реализации core/marketdata
```

Полный перечень решений (ADR) — в [`ARCHITECTURE.md`](ARCHITECTURE.md); брифы задач для
агентов-реализаторов по этапам — в [`docs/agent-briefs/`](docs/agent-briefs/).

Принципы (обоснованы в обсуждениях архитектуры, соблюдать обязательно):

1. **core не зависит от Spring и ни от чего внешнего.** Зависимости: `bks/infra/корневые классы → core`,
   никогда обратно. Правило контролировать ArchUnit-тестом.
2. **Новый брокер = sibling-пакет** (`tbank/`, `alor/`, ...) со слоями `api/contract/adapter`
   поверх того же core. Ядро при этом не меняется.
3. **API всех подключённых брокеров доступны всегда одновременно** (каждый под своими путями,
   Например, БКС — `/api/bks/v1/**`). 
4. **Специфика БКС живёт только в `bks/`**: Keycloak-подобная авторизация (refresh→access),
   портфель-массив лимитов типов `moneyLimit/depoLimit/futuresLimit/futuresHolding/otcLimit`
   в разрезе сроков `T0/T1/T2/T365`, ГО фьючерсов. В core — брокер-независимый снапшот портфеля.
5. **Режимы котировок:** LIVE (реальное время), RECORD (LIVE + запись потока в хранилище),
   REPLAY (воспроизведение записи по виртуальным часам `Clock`, детерминизм — фиксируемый
   seed генератора проскальзывания). RECORD нужен, чтобы сравнение с бэктестом шло по тем же данным.
6. **Модель исполнения — по спецификации, не упрощать без согласия владельца:**
   маркет кроссует стакан (buy по ask, sell по bid) + проскальзывание: случайные 0.05–0.15%
   в неблагоприятную сторону + тир от размера относительно ADV (<1% → 0.05%, 1–5% → 0.1%,
   >5% → 0.2%); ордер > 5% ADV (настраивается) → reject или разбивка на части.
   Лимитные ордера: исполнение по лимиту или лучше, иначе ожидание в очереди.
7. **Комиссии:** по умолчанию 0.1% для акций (настраивается), для фьючерсов — ГО ~10%
   (настраивается per-instrument). Корпоративные события (дивиденды/сплиты) — заглушка с TODO.

## Контракт БКС — источник истины

- `docs/bcs-api/` — локальный снапшот https://trade-api.bcs.ru/ (45 страниц MDX + `llms.txt`,
  снят 2026-10-29). DTO в `bks/contract` обязаны точно соответствовать снапшоту.
- Снапшот **полный**: помимо страниц извлечены sidecar-схемы `*.RequestSchema.json` /
  `*.StatusCodes.json` / `*.ParamsDetails.json` (43 файла, 2026-10-29). Поля DTO брать
  **только** из схем снапшота. Детали — в `docs/bcs-api/README.md`.

## Конвенции

### Стиль кода

- Google Java Format — применяется через Spotless (`./gradlew spotlessApply` перед коммитом).
  **Версия GJF жёстко связана с версией Spotless** (текущая пара: Spotless 8.10.4 + GJF 1.36.1;
  GJF 1.37.0 несовместима — `NoSuchMethodError: Style.valueOf`): перед обновлением любой
  из версий проверять `spotlessApply`
- Все файлы — UTF-8 **без BOM** 
- Комментарии на русском языке (кроме Javadoc)
- Имена классов: CamelCase, методы: camelCase, константы: UPPER_SNAKE_CASE
- Логирование только SLF4J
- Для мапперов используем mapstruct ( если целесообразно )
- 

### Именование

- REST-контроллеры: `*Controller` (например, `OrderController`)
- Сервисы: `*Service` (например, `PortfolioService`)
- DTO: `*Request`, `*Response` (например, `OrderRequest`)
- Тесты: `*Test` (например, `VirtualPortfolioTest`)

## Правила работы

### Ветки

- `main` — стабильная версия, защищена от прямых коммитов
- `feature/*` — новые функции (например, `feature/market-data-feed`)
- `fix/*` — исправления ошибок (например, `fix/commission-calculation`)

## Что агенту ЗАПРЕЩЕНО

- Никогда не коммитить файлы `.env`, `.env.local`, `*.key`, `*.pem`
- Не изменять `build.gradle`, `settings.gradle` без явного разрешения
- Не удалять тесты — только добавлять или исправлять
- Не менять DTO в `ru.moex.papertrader.bks.contract` без согласования (зеркалят контракт БКС)
- Не редактировать `docs/bcs-api/*.md` — снапшот заморожен (разрешено только добавлять
  недостающие sidecar-`*.json` рядом со страницами)
- Не импортировать Spring/`bks`/`infra` в пакетах `core`
- Не использовать `System.out.println` — только SLF4J
- Не добавлять новые зависимости без обоснования в commit message

## Что агент ДОЛЖЕН делать

- Писать тесты для всей новой бизнес-логики (минимум 80% покрытия)
- Запускать `./gradlew spotlessApply` и `./gradlew test` перед коммитом
- Обновлять документацию (этот файл, README.md) при изменении архитектуры
- Использовать существующие интерфейсы и не создавать дубликаты функционала

## Текущее состояние (октябрь 2026)

- Этап 1 завершён: скелет single-project (Spring Boot приложение, `/health`, ArchUnit-тесты
  границ слоёв, Spotless) — `./gradlew clean build` зелёный.
- Контракт БКС: снапшот `docs/bcs-api/` полный — 45 страниц + 43 sidecar-схемы.
- Следующие этапы (2–7): брифы в `docs/agent-briefs/` (контракт БКС → портфель → котировки →
  исполнение → отчётность → интеграционные тесты). Порядок и границы — в `ARCHITECTURE.md` §12.
