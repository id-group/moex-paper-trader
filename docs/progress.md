# Прогресс — статус реализации

Обновлять по завершении каждого этапа и промежуточных результатов. Порядок этапов — в
`docs/roadmap.md`, текущий план — в `docs/plan.md`, временные решения — в
`docs/temporary-decisions.md`.

## Октябрь 2026

- **Этап 1 завершён**: скелет single-project — Spring Boot приложение, ArchUnit-тесты
  границ слоёв, Spotless; `./gradlew clean build` зелёный.
- Контракт БКС: снапшот `docs/bcs-api/` полный — 45 страниц + 43 sidecar-схемы.
- Рефакторинг структуры: классы переведены из пакета `app/` в корневой
  (`ru.moex.papertrader.BrokerEmulatorApplication`); `HealthController` удалён —
  health через Spring Actuator, зависимость `spring-boot-starter-actuator` добавлена
  в `build.gradle` (`/actuator/health`).
- Инструкции для агентов: доменные спецификации вынесены из `AGENTS.md` в skills
  (`bcs-contract`, `bcs-portfolio`, `marketdata-modes`, `execution-model`); добавлена
  команда `/precommit`; документация проекта консолидирована в `docs/`.

- **Модуль 2 (ДЗ OTUS), окружение агента**: GSD Core 1.19.0 (профиль `standard`); команды `/checkpoint`, `/resume`, `/status`; хук JDK 25 для Gradle; сжатие контекста; чек-лист безопасности; discovery в `.planning/`. Память (claude-mem 15.7.0, OpenCode Go, регион Global) работает: наблюдатель пишет, поиск отвечает (проверено 2026-10-31, 16:54); телеметрия claude-mem отключена; см. README.

## Ноябрь 2026

- Закрыт хвост окружения: `MOEX_JAVA25_HOME` прописан в конфиг фонового сервиса
  OpenCode (`opencode service set env ...`), сервис перезапущен; `.\gradlew.bat --version`
  показывает `Launcher JVM: 25.0.4` без ручного выставления `JAVA_HOME`.

- **Этап 2 завершён — Контракт БКС (HTTP MVP)** (2026-11-01). Отчёт:
  `docs/agent-briefs/stage-2-report.md`.
  - `core/domain` + `core/marketdata`: `Order`/`Instrument`/`PortfolioSnapshot`, статусы,
    порт `MarketDataProvider` — чистая Java без Spring.
  - `bks/contract`: DTO — покамерное зеркало схем снапшота (`ErrorResponse`, токены,
    заявки, сделки, котировки/стакан/свечи, справочник, портфель/лимиты).
  - `bks/api`: контроллеры под `/api/bks` (token, portfolio, limits, orders create/cancel/
    status/search sync+async, trades/search, quotes, order-book, candles-chart,
    instruments/by-tickers, trading-schedule/status), Bearer-фильтр с правами trade/read-only,
    `@RestControllerAdvice` → `ErrorResponse`.
  - `bks/adapter`: обмен refresh→access (TTL 24 ч, refresh 90 дней), маппинг DTO ↔ core.
  - `infra/services`: in-memory заявки/сделки, портфель (кэш + блокировки под заявки),
    конфигурируемая заглушка рыночных данных.
  - Проверка: `./gradlew clean build` зелёный; 119 тестов; JaCoCo bundle 92.8% инструкций /
    94.0% строк; сквозной сценарий `bootRun` token → create → status → portfolio → cancel
    пройден (транскрипт в отчёте).
  - Закрыты временные решения №1 (Actuator: только `/actuator/health`) и №7 (префикс
    `emulator.brokers.bks.base-path`, по умолчанию `/api/bks`). Решение №2 (in-memory)
    остаётся в силе до этапа 3.

## Открытые хвосты

- Индекс git: устаревшие записи `app/` сняты с индекса (модуль 2). Закрыто.
- Фоновый сервис OpenCode: `MOEX_JAVA25_HOME` в env сервиса. Закрыто 2026-11-01.
