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

## Открытые хвосты

- Индекс git: устаревшие записи `app/` сняты с индекса (модуль 2). Закрыто.
