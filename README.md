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
| `opencode.json` | MCP-серверы (Context7), сжатие контекста (`compaction`) и правила `permissions`: что агенту можно править, а что только с подтверждением |
| `.opencode/skills/` | спецификации, загружаются по задаче: `bcs-contract`, `bcs-portfolio`, `marketdata-modes`, `execution-model`, `java-conventions`, `testing` |
| `.opencode/commands/` | slash-команды: `/tdd` — цикл red-green-refactor; `/coverage` — покрытие JaCoCo ≥ 60% (временно); `/precommit` — гейт перед коммитом; `/finish-stage` — DoD этапа |

Правила агента — в `AGENTS.md`; коммит выполняется только с явного подтверждения владельца.

## Модуль 2: агентно-ориентированный пайплайн (результаты для проверки)

Критерий модуля: подготовлено окружение под конкретный проект, в README описаны результаты для
проверки. Ветка `feature/homework-2`; коммиты модуля — в истории ветки (`git log`). Пуш в
удалённый репозиторий — по решению владельца.

### Что составляет окружение проекта

- `AGENTS.md` — правила агента: JDK 25 перед Gradle, запреты, DoD.
- `opencode.json` — `permissions`: правки `docs/bcs-api/*` запрещены, кроме `*.json`; `.env`, `*.key`, `*.pem` запрещены; `git push` запрещён; `git commit` — с подтверждением. Сжатие контекста, MCP `context7`.
- `.opencode/skills/` — 6 спецификаций проекта (контракт БКС, портфель, исполнение, рыночные данные, Java, тесты).
- `.opencode/commands/` — `/checkpoint`, `/resume`, `/status` (модуль 2), `/precommit`, `/tdd`, `/coverage`, `/finish-stage`.
- `.opencode/plugins/moex-hooks.ts` — хук: команды с `gradlew` получают JDK 25.
- `.planning/` — проектный контекст GSD: `PROJECT`, `REQUIREMENTS`, `ROADMAP`, `STATE`, `DISCOVERY`.
- `docs/` — roadmap, plan, progress, `ARCHITECTURE`, `security-checklist`, чек-пойнты.
- Память агента — claude-mem (раздел «Память»).

### Итоги по заданию

| Пункт задания | Статус | Что проверено и где |
|---|---|---|
| gsd-core установлен, проект инициализирован: roadmap, plan, progress, architecture, security-checklist | ✅ | GSD Core 1.19.0 в `.opencode/` (не в git). `docs/roadmap.md`, `docs/plan.md`, `docs/progress.md`, `docs/ARCHITECTURE.md`, `docs/security-checklist.md`, `.planning/`. Проверено: чистый клон ветки → установка → `git status` пустой |
| Память агента (claude-mem или аналог) | ✅ | claude-mem 15.7.0 для OpenCode. Наблюдатель пишет (проверено 2026-10-31: последний успешный запрос 19:39, счётчик неудач 0). Поиск через MCP находит наблюдения. Не проверено: подача памяти в новой сессии |
| Быстрые команды и auto hooks | ⚠️ | Команды видны в меню (проверено через API). Хук JDK 25 работает на сервере, запущенном с переменной `MOEX_JAVA25_HOME`: `gradlew --version` → `Launcher JVM: 25.0.4`. Фоновый сервис OpenCode эту переменную не видит, поэтому до исправления хук в нём не работает (раздел «Окружение») |
| Управление контекстом (сжатие, чек-пойнты) и usage до/после | ⚠️ | Сжатие: `compaction` в `opencode.json` (проверено через `opencode debug config`). Чек-пойнты: `/checkpoint`, `/resume`. Usage до/после — текстовый вывод `opencode stats`, сохранённый как изображение, а не скриншот окна TUI. Эффект сжатия не измерен |
| Discovery в GSD (или аналогичный) | ⚠️ | Выполнен по шаблонам GSD вручную. Интерактивный `gsd-new-project` с вопросами не запускался. Решения владельца внесены (`.planning/DISCOVERY.md`, §4) |

### Окружение

- Node 24, npm 11, OpenCode 2.1.4, Docker 29, Python 3.14 (системный).
- GSD Core ставится командой `npx @opengsd/gsd-core@1.19.0 --opencode --local --profile=standard --relative-includes` (версия закреплена). Флаг `--relative-includes` пишет относительные пути вместо путей этой машины: без него GSD ломается в другом checkout, например в git worktree. Пакет `get-shit-done-cc` в npm помечен как deprecated — не используется.
- Побочный эффект установщика: `~/.gsd/defaults.json` (`runtime: opencode`, `resolve_model_ids: omit`).
- MCP-сервер `gsd` выключить локально: в `.opencode/opencode.json` (файл генерируется установщиком и в git не хранится) у `mcp.gsd` поставить `"enabled": false`. Установщик при повторном запуске это значение сохраняет.
- Bun 1.4.4 и uv 0.13.6 поставил установщик claude-mem.
- Переменная пользователя `MOEX_JAVA25_HOME` — путь к JDK 25 на машине. Её читают хук `moex-hooks.ts` и правила `AGENTS.md`.
  - Фоновый сервис OpenCode получает переменные при своём запуске. Переменная, заданная после этого, в нём не видна. Симптом: `$env:MOEX_JAVA25_HOME` в командах агента пуст, `JAVA_HOME` остаётся на JDK 17.
  - Исправление по документации OpenCode CLI (`opencode service`): `opencode service set env MOEX_JAVA25_HOME "<путь из переменной пользователя>"`, затем `opencode service restart`. Перезапуск прерывает открытые сессии.
  - Статус на момент проверки: не применено к фоновому сервису на этой машине. Проверка после применения: `.\gradlew.bat --version` в OpenCode → `Launcher JVM: 25.x`.

### Память

- История: установщик `claude-mem@15.7.0 --provider host` завершился ошибкой: режим требует уже запущенного наблюдателя. Установку откатили и поставили заново с провайдером OpenCode Go.
- Статус: работает (проверено 2026-10-31): наблюдатель пишет, поиск через MCP отвечает, телеметрия выключена.
- Настройки в `~/.claude-mem/settings.json`: `CLAUDE_MEM_PROVIDER` = `openai-compatible`, `CLAUDE_MEM_OPENAI_COMPAT_PRESET` = `opencode-go`, `CLAUDE_MEM_OPENAI_COMPAT_MODEL` = `deepseek-v4.1-flash`, `CLAUDE_MEM_REDACT_ENABLED` = `true`.
- Ключ OpenCode Go лежит в `~/.claude-mem/.env` (переменная `OPENAI_COMPAT_API_KEY`, по документации claude-mem). В git не попадает. Ключ не выводился ни в чат, ни в файлы проекта.
- Регион: в OpenCode Go для workspace выбран **Global** (Settings › Privacy → Regions; менять может только owner или admin). Без него наблюдатель получал `400 … requires Global regions` и не писал.
- Что уходит провайдеру: сжатые записи о вызовах инструментов и итогах. Регион Global снимает ограничение «по умолчанию только Европа и США»; страны обработки в документации OpenCode не перечислены.
- Установка глобальная: память ведётся для всех проектов OpenCode на этой машине.
- Телеметрия: отключена целиком (`npx claude-mem telemetry disable`, решение владельца, 2026-10-31). Уже отправленные события не отзываются.
- Редакция секретов: регулярные выражения для известных форматов ключей и токенов. Пароли и токены без префикса не покрыты (`CLAUDE_MEM_REDACT_GENERIC_ENABLED=false`).
- На другой машине: 1) выбрать регион Global в OpenCode Go; 2) положить ключ в `~/.claude-mem/.env`; 3) прописать четыре настройки из списка выше в `settings.json`; 4) `npx --yes claude-mem@15.7.0 install --ide opencode`, затем `npx claude-mem telemetry disable` и `npx claude-mem start`; 5) перезапустить фоновый сервис OpenCode (см. «Окружение»).

### Быстрые команды и хуки

- `/checkpoint [заметка]` — сохраняет чек-пойнт в `docs/checkpoints/`: состояние, решения, следующий шаг.
- `/resume [тема]` — восстанавливает контекст по последнему чек-пойнту, плану, статусу и памяти.
- `/status [этап]` — краткий статус и незакоммиченные файлы.
- Уже были: `/precommit`, `/tdd`, `/coverage`, `/finish-stage`; из GSD: `/gsd-*`.
- Хук `.opencode/plugins/moex-hooks.ts` (`shell.create.before`): для команд с `gradlew` ставит `JAVA_HOME` на JDK 25; путь берётся только из `MOEX_JAVA25_HOME`. Если переменной нет, `JAVA_HOME` не меняется.
  - Проверено 2026-10-31 на сервере, запущенном с переменной: команда с `gradlew` получает JDK 25; команда без `gradlew` остаётся на JDK 17; `gradlew --version` печатает `Launcher JVM: 25.0.4`.
  - Фоновый сервис в момент проверки переменной не видел (см. «Окружение»).
- Встроенный плагин GSD `.opencode/plugins/gsd-core.js`: активен (проверено через API `plugin.list`).

### Управление контекстом и экономия токенов

- `opencode.json`: `compaction: { auto: true, keep: { tokens: 20000 } }`. Порог — дефолтный (буфер 15%, не меньше 16k). Ключ `buffer` сборка 2.1.4 не принимает (проверено через `opencode api config.get`), поэтому в конфиге его нет.
- Чек-пойнты: `/checkpoint` и `/resume`; в GSD также `pause-work` и `resume-work`.
- Экономия: профиль GSD `standard` вместо `full`; MCP `gsd` выключен; воркфлоу GSD работают через CLI `gsd-tools.cjs`.
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

- Файлы `.planning/`: `PROJECT.md` (ценность, требования, ограничения, решения), `REQUIREMENTS.md` (идентификаторы BKS-*, CORE-*, MD-*, EX-*, RPT-*, INT-*), `ROADMAP.md` (фазы 1–7 с критериями), `STATE.md` (позиция: фаза 2), `DISCOVERY.md` (источники, допущения, решения владельца).
- Метод: шаблоны GSD плюс разбор существующих документов. Интерактивные вопросы GSD клиент не показал: вопросы записаны в `DISCOVERY.md` §4, владелец ответил, решения внесены в документы.

### Git и установка GSD

- В git лежат наши файлы: `.planning/`, `docs/`, `README.md`, общий `opencode.json` в корне, команды `checkpoint`, `resume`, `status`, `precommit`, `tdd`, `coverage`, `finish-stage`, плагин `moex-hooks.ts`, наши skills.
- Файлы GSD Core в git не хранятся: `.opencode/gsd-core/`, `gsd-*` skills, commands и agents, `hooks/`, `scripts/`, манифесты и `.opencode/opencode.json`. Правила для них — в `.gitignore`.
- Почему: при переустановке установщик меняет около 60 файлов `workflows/*.md` (случайные маркеры heredoc) и манифест, а в `.opencode/opencode.json` дописывает абсолютные пути. В git это давало бы постоянный шум. Подробности: `.planning/DISCOVERY.md`, раздел 6.

### Как проверить

Каждый пункт — команда и ожидаемый результат.

1. **Команды.** В OpenCode в каталоге проекта набрать `/`. Ожидаемо: `checkpoint`, `resume`, `status`, `precommit`, `tdd`, `coverage`, `finish-stage` и команды `gsd-*`.
2. **Сжатие.** `opencode debug config` → блок `compaction` → `keep.tokens: 20000`.
3. **MCP `gsd` выключен.** `opencode debug config` → `mcp.gsd` → `disabled: true`. Источник — локальный `.opencode/opencode.json` (не в git) с `"enabled": false`.
4. **Хук JDK 25.** В OpenCode выполнить `.\gradlew.bat --version`. Ожидаемо: `Launcher JVM: 25.x`. Если `17` — переменная не видна фоновому сервису, см. «Окружение».
5. **Память.** `npx claude-mem status` → `Worker is running`. `npx claude-mem telemetry status` → `DISABLED`. В `~/.claude-mem/observer-health.json` поле `consecutiveFailures` = 0, `lastSuccessAt` новее `lastErrorAt`.
6. **Discovery и безопасность.** `.planning/DISCOVERY.md` (решения владельца в §4); `docs/security-checklist.md`.
7. **Воспроизводимость.** Клонировать ветку, установить GSD командой из раздела «Окружение»: `git status` должен остаться пустым.

### Известные ограничения

- Фоновый сервис OpenCode не видит `MOEX_JAVA25_HOME`. Исправление описано в «Окружении»; на этой машине ещё не применено, потому что перезапуск прерывает текущую сессию.
- Снимки usage — текстовый вывод, не скриншот окна TUI.
- Эффект сжатия не измерен: снимки накопительные.
- `compaction.buffer` не поддерживается сборкой 2.1.4: порог сжатия дефолтный.
- Интерактивный `gsd-new-project` не запускался: discovery выполнен по шаблонам GSD.
- Наблюдения памяти отправляются провайдеру OpenCode Go (регион Global).
- `opencode.local.json` OpenCode V2 не читает (проверено через `opencode debug config`). Личные настройки — в глобальном `~/.config/opencode/opencode.json` (проектный конфиг его переопределяет) или в переменных окружения.
