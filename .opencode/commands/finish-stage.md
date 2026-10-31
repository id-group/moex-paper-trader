---
description: Закрытие этапа — DoD из AGENTS.md §10, гейт precommit, обновление docs/progress.md
---

Закрой этап roadmap: `$ARGUMENTS` (номер этапа; если не указан — определи по `docs/progress.md` и `docs/roadmap.md`).

Перед gradlew-командами выстави JDK 25: `$env:JAVA_HOME='C:\Users\id-gr\.jdks\liberica-full-25.0.4'`

1. **Бриф и скиллы.** Прочитай `docs/agent-briefs/stage-N-*.md` и загрузи профильные skills
   (см. `AGENTS.md` §9). Сверь критерии приёмки брифа с фактическим кодом; что не выполнено — перечисли.
2. **Гейт precommit.** Выполни шаги 1–3 из `.opencode/commands/precommit.md`: `spotlessCheck`
   (при необходимости `spotlessApply` с разбором diff), `./gradlew test`, `git status --short`.
   Не коммить.
3. **Покрытие.** Выполни `/coverage`. Порог покрытия (сейчас 60%, временно) проверяется задачей `jacocoTestCoverageVerification`,
   которая входит в `check` и `build`. Если порог не выполнен — перечисли классы без покрытия
   и новые сервисы этапа без unit-тестов. Не снижай порог без разрешения владельца.
4. **Архитектура.** Тесты ArchUnit входят в `./gradlew test`; отдельно убедись, что новые
   правила этапа добавлены в `ArchitectureTest`, если бриф их требует.
5. **Документация.** Обнови `docs/progress.md` (статус этапа и итог). Если менялась архитектура —
   `docs/ARCHITECTURE.md` и `README.md`. Если появилось временное решение — `docs/temporary-decisions.md`.
   Не редактируй `docs/bcs-api/*.md`.
6. **Отчёт.** Таблица «пункт DoD / статус / комментарий», список изменённых файлов
   из `git status`, предложенное сообщение коммита (`feature/*` или `fix/*`).
7. **Жди явного подтверждения владельца.** Коммит, переключение ветки и `git push` — только после ответа.
