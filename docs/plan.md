# План — текущие и ближайшие работы

Живой план; выполненное переносится в `docs/progress.md`. Порядок этапов — в
`docs/roadmap.md`, временные решения — в `docs/temporary-decisions.md`.

## Сейчас

- Текущих работ нет: этапы 1–7 закрыты, см. `docs/roadmap.md` и `docs/progress.md`.

## Закрытые пункты

- [x] Этап 7 — интеграционный прогон и сверка с бэктестом (2026-11-05; ветка
      `feature/stage-7-integration`; бриф `docs/agent-briefs/stage-7-integration.md`, отчёт
      `docs/agent-briefs/stage-7-report.md`). Максимальное расхождение с эталоном 18.53%.
- [x] Этап 6 — отчётность и метрики (2026-11-04; ветка `feature/stage-6-reporting`; бриф
      `docs/agent-briefs/stage-6-reporting.md`, отчёт `docs/agent-briefs/stage-6-report.md`,
      формулы и контракты — `docs/reporting.md`). Коммит `64a2697`.
- [x] Этап 5 — модель исполнения ордеров (2026-11-04; коммит `544adfe`, ветка
      `feature/stage-5-execution`; бриф `docs/agent-briefs/stage-5-execution.md`,
      отчёт `docs/agent-briefs/stage-5-report.md`).

- [x] Этап 4 — рыночные данные LIVE/RECORD/REPLAY (коммит `7c0b7fd`,
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
