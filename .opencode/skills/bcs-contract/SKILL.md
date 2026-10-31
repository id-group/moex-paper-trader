---
name: BCS Contract
description: Контракт БКС Trade API — правила зеркалирования DTO из снапшота docs/bcs-api, sidecar-схемы, авторизация refresh→access, монтаж контроллеров под /api/bks/v1. Загружать перед работой с bks/contract, bks/api, документацией БКС.
---

# Контракт БКС — источник истины

`docs/bcs-api/` — локальный снапшот https://trade-api.bcs.ru/ (45 страниц MDX + `llms.txt`,
снят 2026-10-29). **Единственный источник правды о полях DTO.**

## Правила

1. DTO в `ru.moex.papertrader.bks.contract` — точное зеркало схем снапшота: имена полей,
   типы, enum-строки (например `"1"`/`"2"`), вложенность, общий контракт ошибки
   `ErrorResponse`. Поля не изобретать.
2. Поля брать **только** из схем: встроенных в страницы MDX или sidecar-файлов
   `*.RequestSchema.json` / `*.StatusCodes.json` / `*.ParamsDetails.json` (43 файла,
   извлечены 2026-10-29).
3. Снапшот **заморожен**: `docs/bcs-api/*.md` не редактировать. Разрешено только добавлять
   недостающие sidecar-`*.json` рядом со страницами.
4. Jackson-аннотации в DTO допустимы; Spring — запрещён (ArchUnit).
5. Авторизация (Keycloak-подобная): обмен refresh-токена на access —
   `docs/bcs-api/http/authorization.md` (access 24 ч, refresh 90 дней).
6. Контроллеры `bks/api` монтируются под префиксом брокера: контрактный `/api/v1/x` →
   `/api/bks/v1/x` (точный префикс — конфигурация).
7. OTC-раздел (`docs/bcs-api/otc/`) эмулятором не реализуется.

## Проверка полноты снапшота

У каждой ссылки `require("./...")` в MDX должен быть соседний `.json`:

```
grep -r "require(" docs/bcs-api
```

Состав снапшота и методика повторного извлечения схем (если сайт пересоберут) —
в `docs/bcs-api/README.md`.

## Связанное

- `docs/ARCHITECTURE.md` §5 — состав MVP контракта БКС (ADR-4…ADR-7)
- `docs/agent-briefs/stage-2-bks-contract.md` — бриф этапа 2
