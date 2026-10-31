# moex-paper-trader

Эмулятор брокера для paper trading на Московской бирже. Принимает запросы в формате API
реального брокера (первая реализация — БКС Trade API), виртуально исполняет ордера по
реальным котировкам ММВБ с проскальзыванием и комиссиями, ведёт портфель акций и фьючерсов
(маржинальное обеспечение, NAV) и формирует отчётность для сверки с бэктестом.

Ядро брокер-независимо: добавление нового брокера (Т-Банк, Алор, Финам) — это пакет-адаптер
поверх того же ядра; API всех подключённых брокеров доступны одновременно, каждый под своим
префиксом (БКС — `/api/bks/v1/**`).

Режимы работы с котировками: **LIVE** (реальное время), **RECORD** (запись потока),
**REPLAY** (детерминированное воспроизведение — для сверки с бэктестом, целевое расхождение
метрик < 30%).

## Документация

| Документ | Содержание |
|---|---|
| [`AGENTS.md`](AGENTS.md) | стек, окружение, структура пакетов, конвенции, правила — основной рабочий документ |
| [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) | целевая архитектура, решения (ADR) |
| [`docs/roadmap.md`](docs/roadmap.md) | этапы реализации (roadmap) |
| [`docs/progress.md`](docs/progress.md) | статус и прогресс по этапам |
| [`docs/plan.md`](docs/plan.md) | текущий план работ |
| [`docs/temporary-decisions.md`](docs/temporary-decisions.md) | временные решения и условия их пересмотра |
| [`docs/agent-briefs/`](docs/agent-briefs/) | брифы для агентов-реализаторов (этапы 2–7) |
| [`docs/bcs-api/`](docs/bcs-api/) | полный снапшот контракта БКС Trade API (страницы + JSON-схемы) |

Сборка, запуск и тесты — см. `AGENTS.md` (разделы «Окружение» и «Команды»): сервис на
http://localhost:8080, health check `GET /actuator/health` (Spring Actuator).

## Разработка с AI-агентами

Конфигурация агента лежит в `opencode.json` и `.opencode/`:

| Путь | Содержание |
|---|---|
| `opencode.json` | MCP-серверы (Context7) и правила `permissions`: что агенту можно править, а что только с подтверждением |
| `.opencode/skills/` | спецификации, загружаются по задаче: `bcs-contract`, `bcs-portfolio`, `marketdata-modes`, `execution-model`, `java-conventions`, `testing` |
| `.opencode/commands/` | slash-команды: `/tdd` — цикл red-green-refactor; `/coverage` — покрытие JaCoCo ≥ 60% (временно); `/precommit` — гейт перед коммитом; `/finish-stage` — DoD этапа |

Правила агента — в `AGENTS.md`; коммит выполняется только с явного подтверждения владельца.

## Модуль 2: агентно-ориентированный пайплайн (результаты для проверки)

Ветка `feature/homework-2`. Изменения не закоммичены: коммит — с явного подтверждения владельца.

### Итоги по заданию

| Пункт задания | Статус | Где |
|---|---|---|
| gsd-core установлен, проект инициализирован: roadmap, plan, progress, architecture, security-checklist | ✅ | `.opencode/` (установщик); `docs/roadmap.md`, `docs/plan.md`, `docs/progress.md`, `docs/ARCHITECTURE.md`, `docs/security-checklist.md`; `.planning/` |
| Память агента (claude-mem или аналог) | ❗ не подключена | блокер, см. раздел «Память» |
| Быстрые команды и auto hooks | ✅ | `.opencode/commands/checkpoint.md`, `resume.md`, `status.md`; `.opencode/plugins/moex-hooks.ts`; хуки GSD |
| Управление контекстом (сжатие, чек-пойнты) и usage до/после | ✅ (снимки — отрисованный вывод `opencode stats`, см. ниже) | `opencode.json` (`compaction`); `docs/checkpoints/`; `docs/homework-2/` |
| Discovery в GSD | ✅ по шаблонам GSD, с открытыми вопросами | `.planning/DISCOVERY.md` |

### Окружение

- Node 24, npm 11, OpenCode 2.1.4, Docker 29, Python 3.14 (системный).
- GSD Core ставится командой `npx @opengsd/gsd-core@1.19.0 --opencode --local --profile=standard --relative-includes` (версия закреплена). Флаг `--relative-includes` пишет относительные пути вместо путей этой машины: без него GSD ломается в другом checkout, например в git worktree. Пакет `get-shit-done-cc` в npm помечен как deprecated — не используется.
- Глобальный побочный эффект установщика GSD: `~/.gsd/defaults.json` (`runtime: opencode`, `resolve_model_ids: omit`).
- MCP-сервер `gsd` выключить у себя: в `.opencode/opencode.json` (файл генерируется установщиком и в git не хранится) у `mcp.gsd` поставить `"enabled": false`. Установщик при повторном запуске это значение сохраняет.
- Bun 1.4.4 и uv 0.13.6 поставил установщик claude-mem; при откате их не удаляли.

### Структура проекта (артефакты курса)

| Артефакт | Файл | Примечание |
|---|---|---|
| roadmap | `docs/roadmap.md` | источник истины по этапам |
| roadmap (рабочая копия GSD) | `.planning/ROADMAP.md` | фазы 1–7 с требованиями и критериями |
| plan | `docs/plan.md` | живой план |
| progress | `docs/progress.md` и `.planning/STATE.md` | статус этапов и позиция GSD |
| architecture | `docs/ARCHITECTURE.md` | ADR-1…ADR-10 |
| security-checklist | `docs/security-checklist.md` | статусы S/A/N/D/G/L |
| requirements | `.planning/REQUIREMENTS.md` | идентификаторы для трассировки |

### Память

- **Статус: не подключена.** Установщик `claude-mem@15.7.0 --ide opencode --provider host` завершился ошибкой: режим `host` требует уже запущенного наблюдателя на `127.0.0.1:37777` или `37778`, а claude-mem его не запускает. Установку откатили (`npx claude-mem uninstall`): плагин OpenCode удалён, глобальный `opencode.json` возвращён к исходному виду, запись в PATH удалена. Папка данных `~/.claude-mem` осталась (её сохраняет установщик).
- Чтобы подключить: выбрать провайдера (варианты в `.planning/DISCOVERY.md` §4), положить ключ в `~/.claude-mem/.env` (не в репозиторий), включить `CLAUDE_MEM_REDACT_ENABLED=true`, затем `npx --yes claude-mem@15.7.0 install --ide opencode`, `npx claude-mem start`, перезапустить OpenCode.
- Работающий аналог уже сейчас: чек-пойнты (`/checkpoint` → `docs/checkpoints/`), `/resume` и `.planning/STATE.md`.

### Быстрые команды и хуки

- `/checkpoint [заметка]` — сохраняет чек-пойнт в `docs/checkpoints/` (состояние, решения, следующий шаг).
- `/resume [тема]` — восстанавливает контекст по последнему чек-пойнту, плану и памяти (память пока не подключена).
- `/status [этап]` — краткий статус и незакоммиченные файлы.
- Уже были: `/precommit`, `/tdd`, `/coverage`, `/finish-stage`; из GSD: `/gsd-*`.
- Хук `.opencode/plugins/moex-hooks.ts` (`shell.create.before`): для команд с `gradlew` выставляет `JAVA_HOME` на JDK 25; путь переопределяется переменной `MOEX_JAVA25_HOME`. Статус: плагин загружен и активен (проверено через API `plugin.list`). Поведение на реальном запуске Gradle не проверено.
- Встроенный плагин GSD `.opencode/plugins/gsd-core.js`: активен (проверено); по документации GSD даёт защиту от промпт-инъекций, проверку read-before-edit и монитор контекста.

### Управление контекстом и экономия токенов

- `opencode.json`: `compaction: { auto: true, keep: { tokens: 20000 } }`. Порог — дефолтный (`buffer` 15%, не меньше 16k). Ключ `buffer` описан в документации V2, но сборка 2.1.4 его отбрасывает (проверено через `opencode api config.get`), поэтому в конфиге его нет.
- Чек-пойнты: `/checkpoint` и `/resume`; в GSD также есть `pause-work` и `resume-work`.
- Экономия: профиль `standard` вместо `full`; MCP `gsd` выключен; GSD-воркфлоу работают через CLI `gsd-tools.cjs`.
- Usage до и после: `docs/homework-2/usage-before.png` и `usage-after.png` (+ `.txt`). Это отрисованный вывод `opencode stats --project . --days 30 --cost --models`, а не скриншот окна TUI. Снимок «до» сделан после установки GSD и до настройки сжатия.

| Показатель (30 дней, проект) | До | После |
|---|---|---|
| input | 415.2k | 415.3k |
| output | 257.4k | 280.4k |
| reasoning | 231.8k | 324.4k |
| cache read | 26.9m | 35.6m |
| cached input | 93.3% | 94.4% |

Вывод: снимки накопительные и включают эту сессию, поэтому это не измерение эффекта сжатия. Рост cache read при почти неизменном input показывает, что новая работа шла через prompt cache. Честная оценка эффекта сжатия требует отдельной длинной сессии.

### Discovery

- Файлы `.planning/`: `PROJECT.md` (ценность, требования, ограничения, решения), `REQUIREMENTS.md` (идентификаторы BKS-*, CORE-*, MD-*, EX-*, RPT-*, INT-*), `ROADMAP.md` (фазы 1–7 с критериями), `STATE.md` (позиция: фаза 2, готовность к планированию), `DISCOVERY.md` (источники, допущения, открытые вопросы).
- Метод: шаблоны GSD плюс разбор существующих документов. Интерактивные вопросы GSD клиент не показал, поэтому решения записаны как допущения и ждут подтверждения.
- Открытые вопросы владельцу — `.planning/DISCOVERY.md` §4.

### Git и установка GSD

- В git лежат наши файлы: `.planning/`, `docs/`, `README.md`, общий `opencode.json` в корне (сжатие контекста, правила, MCP `context7`), команды `checkpoint`, `resume`, `status`, `precommit`, `tdd`, `coverage`, `finish-stage`, плагин `moex-hooks.ts`, наши skills (`bcs-*`, `execution-model`, `java-conventions`, `marketdata-modes`, `testing`).
- Файлы GSD Core в git не хранятся: установщик генерирует их на каждой машине. Это `.opencode/gsd-core/`, `gsd-*` skills, commands и agents, `hooks/`, `scripts/`, манифесты и `.opencode/opencode.json`. Правила для них в `.gitignore`.
- Почему так: при переустановке установщик меняет около 60 файлов `workflows/*.md` (случайные маркеры heredoc) и манифест, а в `.opencode/opencode.json` дописывает абсолютные пути. В git это давало бы постоянный шум. Подробности: `.planning/DISCOVERY.md`, раздел 6.
- Локальные настройки (MCP `gsd` и подобное) не коммитятся: они живут в сгенерированном `.opencode/opencode.json`.

### Как проверить

1. Открыть OpenCode в каталоге проекта и набрать `/`: должны быть `checkpoint`, `resume`, `status`, а также `gsd-*`.
2. `opencode debug config`: в блоке `compaction` есть `keep.tokens: 20000`; у `mcp.gsd` стоит `disabled: true`.
3. `docs/homework-2/usage-before.png` и `usage-after.png` — снимки usage.
4. `.planning/DISCOVERY.md` — discovery, допущения и открытые вопросы.
5. `docs/security-checklist.md` — статусы требований безопасности.

### Известные ограничения

- Память не подключена: нужен выбор провайдера и ключ (см. выше).
- Хук `JAVA_HOME` не проверен на реальном запуске Gradle.
- `compaction.buffer` не поддерживается сборкой 2.1.4: порог сжатия дефолтный.
- Снимки usage — отрисованный текст, не скриншот окна.
- `opencode.local.json` OpenCode V2 не читает: проверено через `opencode debug config` (файл и в корне, и в `.opencode/` в конфигурацию не попал). Личные настройки — в глобальном `~/.config/opencode/opencode.json` (проектный конфиг его переопределяет) или в переменных окружения, например `MOEX_JAVA25_HOME`.
