# План — текущие и ближайшие работы

Живой план; выполненное переносится в `docs/progress.md`. Порядок этапов — в
`docs/roadmap.md`, временные решения — в `docs/temporary-decisions.md`.

## Сейчас

- [ ] Этапы 5–7 — см. `docs/roadmap.md` (этап 5 — отдельное окно после коммита этапа 4).

## Дальше

- [ ] Этапы 5–7 — см. `docs/roadmap.md`.

## Закрытые пункты

- [x] Этап 4 — рыночные данные LIVE/RECORD/REPLAY (коммит `TODO`,
      2026-11-02; бриф `docs/agent-briefs/stage-4-marketdata.md`,
      отчёт `docs/agent-briefs/stage-4-report.md`).
- [x] Этап 3 — домен + виртуальный портфель + PostgreSQL (коммит `68c4c9c`,
      2026-11-02; бриф `docs/agent-briefs/stage-3-portfolio.md`).

- [x] Подготовка: индекс git приведён в порядок (устаревшие записи `app/` сняты с индекса).
- [x] Фоновый сервис OpenCode: `opencode service set env MOEX_JAVA25_HOME` выполнен,
      сервис перезапущен; проверка `.\gradlew.bat --version` → `Launcher JVM: 25.0.4`
      (закрыто 2026-11-01, подтверждение владельца получено).
- [x] Память агента (claude-mem 15.7.0): работает, наблюдатель пишет (проверено 16:54).
      Телеметрия отключена (решение владельца).
