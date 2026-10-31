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

Ветка `feature/homework-2`. Коммиты модуля 2 — в истории ветки. Все изменения модуля 2 закоммичены (см. `git log`). Пуш в удалённый репозиторий — по решению владельца.

### Итоги по заданию

| Пункт задания | Статус | Где |
|---|---|---|
| gsd-core установлен, проект инициализирован: roadmap, plan, progress, architecture, security-checklist | ✅ | `.opencode/` (установщик); `docs/roadmap.md`, `docs/plan.md`, `docs/progress.md`, `docs/ARCHITECTURE.md`, `docs/security-checklist.md`; `.planning/` |
| Память агента (claude-mem или аналог) | ✅ работает: наблюдатель пишет, поиск отвечает (проверено 2026-10-31, 16:54) | раздел «Память» |
| Быстрые команды и auto hooks | ✅ | `.opencode/commands/checkpoint.md`, `resume.md`, `status.md`; `.opencode/plugins/moex-hooks.ts`; хуки GSD |
| Управление контекстом (сжатие, чек-пойнты) и usage до/после | ✅ (снимки — отрисованный вывод `opencode stats`, см. ниже) | `opencode.json` (`compaction`); `docs/checkpoints/`; `docs/homework-2/` |
| Discovery в GSD | ✅ по шаблонам GSD, с открытыми вопросами | `.planning/DISCOVERY.md` |

### Окружение

- Node 24, npm 11, OpenCode 2.1.4, Docker 29, Python 3.14 (системный).
- GSD Core ставится командой `npx @opengsd/gsd-core@1.19.0 --opencode --local --profile=standard --relative-includes` (версия закреплена). Флаг `--relative-includes` пишет относительные пути вместо путей этой машины: без него GSD ломается в другом checkout, например в git worktree. Пакет `get-shit-done-cc` в npm помечен как deprecated — не используется.
- Глобальный побочный эффект установщика GSD: `~/.gsd/defaults.json` (`runtime: opencode`, `resolve_model_ids: omit`).
- MCP-сервер `gsd` выключить у себя: в `.opencode/opencode.json` (файл генерируется установщиком и в git не хранится) у `mcp.gsd` поставить `"enabled": false`. Установщик при повторном запуске это значение сохраняет.
- Bun 1.4.4 и uv 0.13.6 поставил установщик claude-mem; при откате их не удаляли.
- Переменная пользователя `MOEX_JAVA25_HOME` — путь к JDK 25 на машине. Нужна для Gradle через агента (хук `moex-hooks.ts`). Задаётся один раз; после задания перезапустите OpenCode, иначе сервер не увидит её.

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

- **История (до решения владельца).** Установщик `claude-mem@15.7.0 --ide opencode --provider host` завершился ошибкой: режим `host` требует уже запущенного наблюдателя на `127.0.0.1:37777` или `37778`, а claude-mem его не запускает. Установку откатили (`npx claude-mem uninstall`): плагин OpenCode удалён, глобальный `opencode.json` возвращён к исходному виду, запись в PATH удалена. Папка данных `~/.claude-mem` осталась (её сохраняет установщик).
- **Статус: работает** (проверено 2026-10-31, 16:54). `claude-mem@15.7.0` для OpenCode, провайдер `openai-compatible`, пресет `opencode-go`, модель `deepseek-v4.1-flash`, авто-редакция включена (`CLAUDE_MEM_REDACT_ENABLED=true`). До исправления OpenCode Go отклонял каждый запрос: `400 … This Go model requires Global regions. Select Global in your workspace's Privacy settings to use it.` До исправления счётчик неудач дошёл до 69 подряд (`~/.claude-mem/observer-health.json`). Запросы доходили до opencode.ai и отклонялись. **Что сделано:** в консоли OpenCode для workspace выбран регион **Global** (**Settings › Privacy → Regions**; менять может только owner или admin), воркер перезапущен.
- **Ключ** лежит в `~/.claude-mem/.env` — вне репозитория, в git не попадает. Файл создан вручную; ключ не выводился ни в чат, ни в файлы проекта.
- **Что уходит провайдеру.** Наблюдения о работе агента (сжатые записи вызовов инструментов и итогов) отправляются OpenCode Go. Установка глобальная: память ведётся для **всех** проектов OpenCode на этой машине. Регион Global снимает ограничение OpenCode «по умолчанию только Европа и США»; страны обработки в документации OpenCode не перечислены.
- **Телеметрия.** Отключена целиком по решению владельца (`npx claude-mem telemetry disable`, 2026-10-31). Проверка: `npx claude-mem telemetry status` → `DISABLED`. Уже отправленные события не отзываются.
- **Редакция.** Работает по регулярным выражениям: известные форматы ключей и токенов заменяются маркерами. Пароли и токены без префикса (`password=…`) не покрыты: `CLAUDE_MEM_REDACT_GENERIC_ENABLED=false`.
- **Проверка.** `npx claude-mem status` → `Worker is running`. В `~/.claude-mem/observer-health.json` `consecutiveFailures` — 0, `lastSuccessAt` новее `lastErrorAt` (поле `lastErrorMessage` хранит последнюю ошибку и может не очищаться). В `GET http://127.0.0.1:37777/api/health` поле `ai.lastInteraction` перестаёт быть пустым после первых вызовов инструментов.
- **На другой машине:** выбрать регион Global в настройках OpenCode Go, положить ключ в `~/.claude-mem/.env`, затем `npx --yes claude-mem@15.7.0 install --ide opencode`, `npx claude-mem start`, перезапустить OpenCode.
- Работающий аналог уже сейчас: чек-пойнты (`/checkpoint` → `docs/checkpoints/`), `/resume` и `.planning/STATE.md`.

### Быстрые команды и хуки

- `/checkpoint [заметка]` — сохраняет чек-пойнт в `docs/checkpoints/` (состояние, решения, следующий шаг).
- `/resume [тема]` — восстанавливает контекст по последнему чек-пойнту, плану и памяти (claude-mem, см. раздел «Память»).
- `/status [этап]` — краткий статус и незакоммиченные файлы.
- Уже были: `/precommit`, `/tdd`, `/coverage`, `/finish-stage`; из GSD: `/gsd-*`.
- Хук `.opencode/plugins/moex-hooks.ts` (`shell.create.before`): для команд с `gradlew` выставляет `JAVA_HOME` на JDK 25; путь берётся только из переменной `MOEX_JAVA25_HOME`; если её нет, хук `JAVA_HOME` не меняет. Статус: плагин загружен и активен (проверено через API `plugin.list`). Поведение на реальном запуске Gradle не проверено.
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
6. Память: `npx claude-mem status` → `Worker is running`; после перезапуска OpenCode инструменты набора `claude-mem` (например, `work_state_read`) отвечают без ошибки.

### Известные ограничения

- Память: работает (проверено 2026-10-31). Телеметрия claude-mem отключена.
- Хук `JAVA_HOME` не проверен на реальном запуске Gradle.
- `compaction.buffer` не поддерживается сборкой 2.1.4: порог сжатия дефолтный.
- Снимки usage — отрисованный текст, не скриншот окна.
- `opencode.local.json` OpenCode V2 не читает: проверено через `opencode debug config` (файл и в корне, и в `.opencode/` в конфигурацию не попал). Личные настройки — в глобальном `~/.config/opencode/opencode.json` (проектный конфиг его переопределяет) или в переменных окружения, например `MOEX_JAVA25_HOME`.
