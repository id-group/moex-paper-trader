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

## Открытые хвосты

- Индекс git: устаревшие записи `src/.../app/…` от реорганизации пакетов — почистить
  при следующем коммите (`git rm --cached` старых путей, `git add` фактических).
