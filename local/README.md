# Локальный запуск

Каталог для запуска проекта на своей машине из IDE или через `bootRun`: PostgreSQL 18 в Docker
и профиль Spring `local` с готовыми параметрами БД и значениями переменных по умолчанию.

| Файл | Назначение |
|---|---|
| `docker-compose.yml` | PostgreSQL 18 (`papertrader` / `papertrader`, порт 5433, том `pgdata`) |
| `../src/main/resources/application-local.yml` | профиль `local`: datasource, режим STUB, локальные refresh-токены БКС |

## 1. Поднять базу

Из корня репозитория:

```powershell
docker compose -f local/docker-compose.yml up -d
docker compose -f local/docker-compose.yml ps      # papertrader-local-postgres: healthy
```

Или из этого каталога:

```powershell
docker compose up -d
```

Пароль по умолчанию — `papertrader` (переопределяется `DB_PASSWORD`), порт хоста — **5433**
(`DB_PORT`). Порт 5433 выбран потому, что 5432 на машине разработчика часто занят системным
PostgreSQL; приложение в профиле `local` смотрит именно на 5433.
Остановка — `docker compose -f local/docker-compose.yml down` (данные остаются в томе
`papertrader-local_pgdata`); удалить данные — добавить `-v`.

## 2. Запуск из IntelliJ IDEA

Профиль лежит в `src/main/resources/application-local.yml` рядом с `application.yml`, поэтому
дополнительные пути конфигурации указывать не нужно.

1. **Run → Edit Configurations… → + → Spring Boot** (или `Application`).
2. Main class: `ru.moex.papertrader.BrokerEmulatorApplication`.
3. Working directory: корень проекта (`$PROJECT_DIR$`).
4. **Active profiles**: `local`.
5. Запуск. Сервис — http://localhost:18080, health —
   `Invoke-RestMethod http://localhost:18080/actuator/health`.

Порт 18080 выбран для профиля `local`, потому что 8080 на машине может быть занят или
сбрасывать соединения; переопределяется `SERVER_PORT`.

Без `local`-профиля приложение возьмёт значения по умолчанию из
`src/main/resources/application.yml` (порт БД 5432) и локальные refresh-токены БКС не получит;
для контейнера из этой папки нужен профиль `local` или переменная `DB_URL`.

## 3. Запуск из терминала

```powershell
$env:SPRING_PROFILES_ACTIVE = 'local'
.\gradlew.bat bootRun
```

или одной строкой: `.\gradlew.bat bootRun --args="--spring.profiles.active=local"`.

## 4. Проверка API

Токен эмулятора БКС с локальными refresh-токенами:

```powershell
$token = (Invoke-RestMethod -Method Post `
  -Uri 'http://localhost:18080/api/bks/trade-api-keycloak/realms/tradeapi/protocol/openid-connect/token' `
  -ContentType 'application/x-www-form-urlencoded' `
  -Body 'client_id=trade-api-write&refresh_token=local-trade-refresh-token&grant_type=refresh_token').access_token

Invoke-RestMethod -Headers @{ Authorization = "Bearer $token" } `
  -Uri 'http://localhost:18080/api/bks/v1/portfolio'
```

## 5. Переопределения

| Переменная | По умолчанию в профиле | Когда менять |
|---|---|---|
| `DB_URL` | `jdbc:postgresql://localhost:5433/papertrader` | нестандартный порт/хост БД |
| `DB_USERNAME` / `DB_PASSWORD` | `papertrader` / `papertrader` | свой пароль (совпадает с `DB_PASSWORD` в compose) |
| `BKS_REFRESH_TOKEN_TRADE` / `..._READ` | `local-trade-refresh-token` / `local-read-refresh-token` | если нужны другие токены |
| `ALGO_PACK_TOKEN` | пусто | режимы `LIVE`/`RECORD` |
| `EMULATOR_MARKETDATA_MODE` | `STUB` | `LIVE`, `RECORD`, `REPLAY` |
| `SERVER_PORT` | `18080` | занят порт |

Тесты и полная сборка описаны в корневом `README.md` (раздел «Локальный запуск»).
