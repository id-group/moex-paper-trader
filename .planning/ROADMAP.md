# Roadmap: moex-paper-trader

## Overview

Семь этапов из `docs/roadmap.md`: от скелета до интеграционного прогона и сверки с бэктестом. Номера фаз совпадают с номерами этапов. Брифы — в `docs/agent-briefs/`, статус — в `docs/progress.md`. Этот файл — рабочая копия для GSD; при расхождении источник истины — `docs/roadmap.md`.

## Phases

- [x] **Phase 1: Скелет** — сборка, health, ArchUnit, Spotless (этап 1, готово)
- [ ] **Phase 2: Контракт БКС (HTTP MVP)** — бриф `stage-2-bks-contract.md`
- [ ] **Phase 3: Домен, виртуальный портфель, PostgreSQL** — бриф `stage-3-portfolio.md`
- [ ] **Phase 4: Рыночные данные LIVE / RECORD / REPLAY** — бриф `stage-4-marketdata.md`
- [ ] **Phase 5: Модель исполнения** — бриф `stage-5-execution.md`
- [ ] **Phase 6: Отчётность и метрики** — бриф `stage-6-reporting.md`
- [ ] **Phase 7: Интеграционный прогон и сверка с бэктестом** — бриф `stage-7-integration.md`

## Phase Details

### Phase 1: Скелет
**Goal**: сервис собирается и отвечает на health-check
**Depends on**: nothing (first phase)
**Requirements**: —
**Success Criteria** (what must be TRUE):
  1. `./gradlew clean build` зелёный
  2. `GET /actuator/health` отвечает
**Plans**: выполнено вне GSD

### Phase 2: Контракт БКС (HTTP MVP)
**Goal**: через API эмулятора проходят авторизация, заявки, статус, портфель, лимиты и рыночные данные по контракту БКС
**Depends on**: Phase 1
**Requirements**: BKS-AUTH-01…03, BKS-PORT-01…02, BKS-ORD-01…04, BKS-TRD-01, BKS-MD-01, BKS-REF-01, BKS-ERR-01, BKS-DTO-01, BKS-CFG-01
**Success Criteria** (what must be TRUE):
  1. `bootRun` и curl-сценарий: token → create → status → portfolio → cancel
  2. Контроллеры покрыты тестами: happy path и ошибки 401 / 400 / 404
  3. DTO сверены со схемами снапшота: таблица «эндпоинт → файл схемы» в отчёте
**Plans**: TBD

### Phase 3: Домен, виртуальный портфель, PostgreSQL
**Goal**: состояние портфеля и заявок хранится в PostgreSQL; домен остаётся без Spring
**Depends on**: Phase 2
**Requirements**: CORE-PF-01, CORE-PF-02, INF-DB-01
**Success Criteria** (what must be TRUE):
  1. Портфель пересчитывается после сделок и восстанавливается после перезапуска
  2. ГО и вариационная маржа считаются по настраиваемым ставкам
**Plans**: TBD

### Phase 4: Рыночные данные
**Goal**: котировки работают в трёх режимах; REPLAY детерминирован
**Depends on**: Phase 3
**Requirements**: MD-01, MD-02
**Success Criteria** (what must be TRUE):
  1. Два прогона REPLAY с одним seed дают идентичный результат
  2. RECORD пишет снапшоты, REPLAY читает их
**Plans**: TBD

### Phase 5: Модель исполнения
**Goal**: ордера исполняются по спецификации `execution-model` без упрощений
**Depends on**: Phase 4
**Requirements**: EX-01, EX-02, EX-03
**Success Criteria** (what must be TRUE):
  1. Тиры проскальзывания покрыты тестами (< 1%, 1–5%, > 5% ADV)
  2. Лимитный ордер исполняется по цене «лимит или лучше»
**Plans**: TBD

### Phase 6: Отчётность и метрики
**Goal**: NAV, метрики, журнал сделок и сравнение с бэктестом
**Depends on**: Phase 5
**Requirements**: RPT-01, RPT-02, RPT-03
**Success Criteria** (what must be TRUE):
  1. Метрики считаются из кривой NAV
  2. Сравнение с бэктестом выдаёт таблицу отклонений
**Plans**: TBD

### Phase 7: Интеграционный прогон
**Goal**: сквозной REPLAY и сверка с бэктестом в пределах 30%
**Depends on**: Phase 6
**Requirements**: INT-01
**Success Criteria** (what must be TRUE):
  1. Интеграционный тест проходит порог 30%
  2. Порог покрытия JaCoCo возвращён к 80% (`docs/temporary-decisions.md` #9)
**Plans**: TBD

## Progress

| Phase | Plans Complete | Status | Completed |
|-------|----------------|--------|-----------|
| 1. Скелет | — | Complete | 2026-10 |
| 2. Контракт БКС | 0/TBD | Not started | - |
| 3. Домен и портфель | 0/TBD | Not started | - |
| 4. Рыночные данные | 0/TBD | Not started | - |
| 5. Исполнение | 0/TBD | Not started | - |
| 6. Отчётность | 0/TBD | Not started | - |
| 7. Интеграция | 0/TBD | Not started | - |
