# Roadmap — этапы реализации

Порядок и границы этапов moex-paper-trader. Архитектурные решения (ADR) — в
`docs/ARCHITECTURE.md`; статусы и прогресс — в `docs/progress.md`; брифы для
агентов-реализаторов — в `docs/agent-briefs/`.

1. ✅ Скелет single-project (сборка, health, ArchUnit, Spotless) — готово
2. Контракт БКС: DTO/контроллеры/авторизация поверх заглушек core — `docs/agent-briefs/stage-2-bks-contract.md`
3. Домен + виртуальный портфель + БД — `docs/agent-briefs/stage-3-portfolio.md`
4. Рыночные данные LIVE/RECORD/REPLAY — `docs/agent-briefs/stage-4-marketdata.md`
5. Модель исполнения — `docs/agent-briefs/stage-5-execution.md`
6. Отчётность и метрики — `docs/agent-briefs/stage-6-reporting.md`
7. Интеграционный прогон, сверка с бэктестом — `docs/agent-briefs/stage-7-integration.md`
