# moex-paper-trader

## What This Is

Эмулятор брокера для paper trading на Московской бирже. Принимает запросы в формате API реального брокера (первая реализация — БКС Trade API), исполняет ордера виртуально по реальным котировкам ММВБ с проскальзыванием и комиссиями, ведёт портфель акций и фьючерсов и формирует отчётность для сверки с бэктестом.

## Core Value

Стратегия работает с эмулятором так же, как с боевым API брокера, а результат прогона в режиме REPLAY сверяется с бэктестом (целевое расхождение метрик < 30%).

## Requirements

### Validated

- ✓ Скелет single-project: сборка Gradle, Spring Boot 4.1.2, health через Actuator, ArchUnit-границы, Spotless — этап 1

### Active

- [ ] Контракт БКС (HTTP MVP) — см. `.planning/REQUIREMENTS.md`, фаза 2
- [ ] Домен, виртуальный портфель и PostgreSQL — фаза 3
- [ ] Рыночные данные LIVE / RECORD / REPLAY — фаза 4
- [ ] Модель исполнения: проскальзывание, ADV, лимитные ордера — фаза 5
- [ ] Отчётность и метрики — фаза 6
- [ ] Интеграционный прогон и сверка с бэктестом — фаза 7

### Out of Scope

- WebSocket-потоки БКС — отдельный этап после HTTP MVP (`docs/ARCHITECTURE.md` §5)
- Методы deprecated, OTC, discounts, operation-history, MCP — не реализуем; deprecated — только по запросу роутера
- Реальные заявки брокеру — эмулятор исполняет ордера виртуально
- Авто-ликвидация фьючерсов — будущее (`docs/ARCHITECTURE.md` §8)
- Корпоративные события (дивиденды, сплиты) — заглушка с TODO (`docs/temporary-decisions.md` #5)

## Context

- Brownfield: код этапа 1 существует; пакеты `bks`, `core`, `infra` ещё не созданы.
- Источники истины: `docs/bcs-api/` (контракт, заморожен), `docs/ARCHITECTURE.md` (ADR-1…ADR-10), `docs/roadmap.md`, брифы `docs/agent-briefs/`.
- Агентные инструменты: GSD Core 1.19.0 в `.opencode/`; команды `/checkpoint`, `/resume`, `/status`, `/precommit`, `/tdd`, `/coverage`, `/finish-stage`.

## Constraints

- **Stack**: Java 25, Spring Boot 4.1.2, Gradle 9.8.2 (только wrapper). Для каждого вызова Gradle выставлять `JAVA_HOME` на JDK 25 (`AGENTS.md` §1; хук `moex-hooks.ts` делает это автоматически).
- **Architecture**: `core` без Spring и внешних слоёв — проверяется ArchUnit (принцип №1).
- **Dependencies**: `build.gradle`, `settings.gradle`, `gradle/libs.versions.toml` — только с согласования владельца.
- **Coverage**: JaCoCo ≥ 60% по bundle, временно (`docs/temporary-decisions.md` #9).
- **Security**: см. `docs/security-checklist.md`.

## Key Decisions

| Decision | Rationale | Outcome |
|----------|-----------|---------|
| Один Gradle-проект, слои — пакеты (ADR-1) | Проще сборка; границы держит ArchUnit | ✓ Good |
| Новый брокер = sibling-пакет (ADR-3) | API брокеров доступны одновременно | — Pending (этап 2) |
| Портфель БКС — массив лимитов, core — снапшот (ADR-5) | Зеркалит контракт, ядро переносимо | — Pending (этап 2–3) |
| Авторизация refresh → access из конфигурации (ADR-7) | Роутер проходит тот же сценарий, что с боевым API | ⚠️ Revisit — хранение токенов, вопрос владельцу |
| Модель исполнения без упрощений (ADR-9) | Требование владельца | — Pending (этап 5) |
| Персистентность в infra, без портов в core (ADR-10) | Пакет `core/spi` упразднён | — Pending (этап 3) |
| GSD Core 1.19.0, профиль `standard` (модуль 2) | Меньше стартового контекста, чем `full` | ✓ Good |
| claude-mem не подключён (модуль 2) | Нет провайдера наблюдателя; установка откатана | ⚠️ Revisit |
| GSD-файлы в git: вендорная копия 1.19.0; `opencode.json` с относительными путями (модуль 2) | Среда видна проверяющему; переустановка даёт шум, схема для команды открыта | ⚠️ Revisit |

---
*Last updated: 2026-10-31 after discovery (модуль 2)*
