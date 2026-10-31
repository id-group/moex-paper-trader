---
name: Java Conventions
description: Соглашения по коду Java 25 / Spring Boot 4 в moex-paper-trader — record-DTO, null-safety, SLF4J, слои транзакций и исключений, именование, Google Java Format. Загружать перед написанием или ревью Java-кода в src/main.
---

# Соглашения по коду Java

Источник — `AGENTS.md` §3–§4 и §6. При конфликте приоритет у `AGENTS.md`.

## Архитектура слоёв

- `core` — чистая Java без Spring, `bks`, `infra`. Нарушение ловит `ArchitectureTest`.
- `bks/contract` — точное зеркало снапшота `docs/bcs-api`. Не менять без согласования владельца.
- `bks/api` — контроллеры; `bks/adapter` — маппинг DTO ↔ `core`.
- `infra/services` — интерфейс `*Service` + реализация `*ServiceImpl`; здесь же `@Transactional`.
- `infra/repository`, `infra/model` (JPA), `infra/mapper` (MapStruct, если целесообразно).

## DTO и модели

- DTO для REST — `record` с именами `*Request` / `*Response`.
- JPA-сущности `@Entity` — обычные классы, не record.
- DTO в `bks/contract` — поля и enum-строки строго из схем снапшота, без выдуманных полей.

## Null-safety

- Использовать `@NonNull` / `@Nullable` (`org.springframework.lang` или Lombok) на публичных
  параметрах и возвращаемых значениях.
- Для возвращаемого значения, которое может отсутствовать, — `Optional<T>`, а не `null`.
- Не возвращать `null` из коллекций: пустой `List.of()` / `Map.of()`.

## Транзакции и исключения

- `@Transactional` — только на сервисном слое (`infra/services`). Не на контроллерах, не
  на репозиториях и не в `core`.
- Бизнес-исключения — свои классы (например, `ResourceNotFoundException`). Ловятся в
  глобальном `@RestControllerAdvice`, а не в каждом контроллере.
- Не глотать исключения без логирования. Не использовать исключения для обычного потока управления.

## Логирование

- Только SLF4J через Lombok `@Slf4j`.
- `System.out` и `System.err` запрещены.
- Не логировать секреты: токены БКС, refresh-токены, заголовки авторизации.

## Именование

- Контроллеры — `*Controller`, сервисы — `*Service` / `*ServiceImpl`, тесты — `*Test`.
- Пакеты — строчные, без подчёркиваний.
- Константы — `UPPER_SNAKE_CASE`, поля — `camelCase`.

## Устаревшие API

- Даты и время — только `java.time.*`. `java.util.Date`, `Calendar`, `SimpleDateFormat` не использовать.

## Комментарии

- Комментарии в коде — на русском языке, кроме Javadoc.
- Добавлять комментарий только там, где поведение не очевидно из кода (ограничения, неочевидные
  инварианты, ссылки на ADR или спецификацию skill).

## Форматирование

- Google Java Format через Spotless. Перед коммитом: `./gradlew spotlessApply`, затем
  `./gradlew spotlessCheck`.
- Версия GJF жёстко связана со Spotless (пара 8.10.4 + 1.36.1). Не обновлять ни одну без проверки
  `spotlessApply`.
- Перед запуском Gradle выставить JDK 25:
  `$env:JAVA_HOME='C:\Users\id-gr\.jdks\liberica-full-25.0.4'`.

## Чек-лист перед завершением

- [ ] Слои соблюдены (`core` без Spring; `@Transactional` только в сервисах).
- [ ] Нет `System.out`, `java.util.Date`, `null` вместо `Optional`.
- [ ] DTO — `record`, поля совпадают со схемами (для `bks/contract`).
- [ ] `spotlessCheck` зелёный.
- [ ] Для новой логики есть тесты (см. skill `testing`).
