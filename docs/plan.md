# План — текущие и ближайшие работы

Живой план; выполненное переносится в `docs/progress.md`. Порядок этапов — в
`docs/roadmap.md`, временные решения — в `docs/temporary-decisions.md`.

## Сейчас

- [ ] Этап 2 — Контракт БКС (HTTP MVP): бриф `docs/agent-briefs/stage-2-bks-contract.md`;
      перед началом загрузить skill `bcs-contract`.
- [x] Подготовка: индекс git приведён в порядок (устаревшие записи `app/` сняты с индекса).
- [ ] Фоновый сервис OpenCode не видит `MOEX_JAVA25_HOME`: `opencode service set env` и `opencode service restart` (ждёт подтверждения владельца, перезапуск прерывает сессию). Проверка: `.\gradlew.bat --version` → `Launcher JVM: 25.x`.
- [x] Память агента (claude-mem 15.7.0): работает, наблюдатель пишет (проверено 16:54). Телеметрия отключена (решение владельца).

## Дальше

- [ ] Этап 3 — домен + виртуальный портфель + PostgreSQL: бриф
      `docs/agent-briefs/stage-3-portfolio.md`; skills `bcs-portfolio`, `execution-model`.
- [ ] Этапы 4–7 — см. `docs/roadmap.md`.
