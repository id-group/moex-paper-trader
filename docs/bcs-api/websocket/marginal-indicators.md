# Маржинальные показатели

> WebSocket API позволяет получать **маржинальные показатели портфеля**, включая ГО, вармаржу, уровень риска, задолженности и устойчивость счёта в реальном времени.

import Tabs from '@theme/Tabs';
import TabItem from '@theme/TabItem';

# Маржинальные показатели

WebSocket API позволяет получать **маржинальные показатели портфеля**, включая ГО, вармаржу, уровень риска, задолженности и устойчивость счёта в реальном времени.

---

## 🔌 WebSocket URL

```text
wss://ws.broker.ru/trade-api-bff-marginal-indicators/api/v1/marginal-indicators/ws
```

## 🔐 Аутентификация

Перед установкой WebSocket-соединения клиент должен передать `access-token` в HTTP-заголовке:

```text
Authorization: Bearer <ACCESS_TOKEN>
```

Подробнее об авторизации см. раздел [Авторизация](/http/authorization).

---

## 📥 Формат ответа server → client

Ниже приведена структура данных, соответствующая спецификации из исходного файла.

Все данные приходят **в одном объекте**, содержащем:

- `agreementData`
- `portfolioCurrentValue`
- `portfolioStability`
- `fortsStability`
- `marginParameters`
- `cashByCurrency`
- `moneyDebt`
- `marginSecurities`
- `futuresParameters`

---

## 📄 Структура данных

### 📦 `agreementData` — данные о брокерском счёте

| Поле | Тип | Описание |
|------|------|----------|
| `number` | string | Номер счёта |
| `isIia` | boolean | Является ли счёт ИИС |
| `displayName` | string | Отображаемое имя |
| `subaccountId` | string | ID субсчёта |
| `color` | string | HEX‑код цвета |

---

### 📦 `portfolioCurrentValue` — стоимость портфеля

| Поле | Тип | Описание |
|------|------|----------|
| `currentValueRub` | number | Текущая стоимость |
| `dailyPortfolioChangeRub` | number | Дневное изменение (₽) |
| `dailyPortfolioChangePerc` | number | Дневное изменение (%) |
| `totalPortfolioChangeRub` | number | Изменение за всё время (₽) |
| `totalPortfolioChangePerc` | number | Изменение за всё время (%) |
| `collateralAvailable` | number | Доступное обеспечение |
| `varMargin` | number | Вариационная маржа |

---

### 📦 `portfolioStability` — устойчивость портфеля

| Поле | Тип | Описание |
|------|------|----------|
| `isMarginOn` | boolean | Включена ли маржинальная торговля |
| `borrowedFunds` | number | Все задолженности (₽) |
| `stabilityStatusId` | number | ID статуса (0–5) |
| `stabilityStatusName` | string | Текст статуса |
| `refillSum` | number | Сумма для выхода из маржин-колла |

---

### 📦 `fortsStability` — устойчивость по ФОРТС

| Поле | Тип | Описание |
|------|------|----------|
| `ownFunds` | number | Собственные средства |
| `freeCash` | number | Свободные средства |
| `stabilityStatusId` | number | ID статуса |
| `stabilityStatusName` | string | Статус позиции |
| `refillSum` | number | Сумма пополнения |
| `fortsMarginCall` | number | Уровень маржин-колла |
| `fortsForcedClosure` | number | Уровень принудительного закрытия |
| `riskLevel` | number | Уровень риска (0–4) |

---

### 📦 `marginParameters` — параметры маржинального портфеля

| Поле | Тип | Описание |
|------|------|----------|
| `marginValue` | number | Стоимость маржинальных активов |
| `collateral` | number | ГО |
| `marginCall` | number | Уровень маржин-колла |
| `forcedClosure` | number | Уровень принудительного закрытия |

---

### 📦 `cashByCurrency` — свободные деньги по валютам

| Поле | Тип | Описание |
|------|------|----------|
| `currency` | string | Валюта |
| `sum` | number | Сумма |

---

### 📦 `moneyDebt` — задолженности по валютам

| Поле | Тип | Описание |
|------|------|----------|
| `totalMoneyDebt` | number | Общая задолженность |
| `debtByCurrency[].currency` | string | Валюта |
| `debtByCurrency[].sum` | number | Задолженность |

---

### 📦 `marginSecurities` — маржинальные позиции

| Поле | Тип | Описание |
|------|------|----------|
| `totalMarginSecurity` | number | Общая задолженность |
| `marginBySecurity[].ticker` | string | Биржевой тикер |
| `marginBySecurity[].board` | string | Режим торгов |
| `marginBySecurity[].displayName` | string | Имя |
| ~~`marginBySecurity[].logoLink`~~ | string | Логотип *(deprecated)*|
| `marginBySecurity[].quantity` | number | Кол-во |
| `marginBySecurity[].currentValue` | number | Стоимость |
| `marginBySecurity[].currency` | string | Валюта |
| `marginBySecurity[].discount` | number | Дисконт (%) |

---

### 📦 `futuresParameters` — фьючерсные позиции

| Поле | Тип | Описание |
|------|------|----------|
| `futuresValue` | number | Общая стоимость |
| `futuresPortfolio[].ticker` | string | Биржевой тикер |
| `futuresPortfolio[].board` | string | Режим торгов |
| `futuresPortfolio[].currentValue` | number | Текущая стоимость |
| `futuresPortfolio[].balanceValue` | number | Балансовая стоимость |
| `futuresPortfolio[].futuresCollateral` | number | ГО по позиции |
| `futuresPortfolio[].discount` | number | Дисконт |
| `futuresPortfolio[].displayName` | string | Имя |
| ~~`futuresPortfolio[].logoLink`~~ | string | Логотип *(deprecated)* |

---

## 📋 Пример ответа

```json
{
  "agreementData": {
    "number": "1234567/25",
    "isIia": false,
    "displayName": "Основной",
    "subaccountId": "49909a5f-8cfe-441a-b3b7-86054eb97922",
    "color": "#2E88CB"
  },
  "portfolioCurrentValue": {
    "currentValueRub": 8086.59,
    "dailyPortfolioChangeRub": -6.38,
    "dailyPortfolioChangePerc": -1.07,
    "totalPortfolioChangeRub": -9.25,
    "totalPortfolioChangePerc": -0.24,
    "collateralAvailable": 5018.23,
    "varMargin": 0
  },
  "portfolioStability": {
    "isMarginOn": false,
    "borrowedFunds": 0,
    "stabilityStatusId": 5,
    "stabilityStatusName": "Максимальная устойчивость",
    "refillSum": 0
  },
  "fortsStability": {
    "ownFunds": 0,
    "freeCash": 0,
    "stabilityStatusId": 0,
    "stabilityStatusName": "",
    "refillSum": 0,
    "fortsMarginCall": 0,
    "fortsForcedClosure": 0,
    "riskLevel": 0,
    "optionsPremium": 0
  },
  "marginParameters": {
    "marginValue": 5136.59,
    "collateral": 0,
    "marginCall": 118.36,
    "forcedClosure": 59.18
  },
  "cashByCurrency": [
    {
      "currency": "RUB",
      "sum": 4544.79
    }
  ],
  "moneyDebt": {
    "totalMoneyDebt": 0,
    "debtByCurrency": []
  },
  "marginSecurities": {
    "totalMarginSecurity": 0,
    "marginBySecurity": [
      {
        "ticker": "RUB",
        "board": "",
        "displayName": "",
        "logoLink": "https://mybroker.storage.bcs.ru/FinInstrumentLogo/RUB.png",
        "quantity": 4544.79,
        "currentValue": 4544.79,
        "currency": "RUB",
        "discount": 0
      },
      {
        "ticker": "SBER",
        "board": "TQBR",
        "displayName": "Сбербанк",
        "logoLink": "https://mybroker.storage.bcs.ru/FinInstrumentLogo/RU0009029540.png",
        "quantity": 2,
        "currentValue": 591.8,
        "currency": "RUB",
        "discount": 20
      }
    ]
  },
  "futuresParameters": {
    "futuresValue": 0,
    "futuresPortfolio": []
  }
}
```

---

## 💻 Примеры подключения

<Tabs>
<TabItem value="js" label="JavaScript (Node.js)">

```js
import WebSocket from "ws";

const URL = "wss://ws.broker.ru/trade-api-bff-marginal-indicators/api/v1/marginal-indicators/ws";

const ws = new WebSocket(URL, {
  headers: {
    Authorization: "Bearer YOUR_ACCESS_TOKEN",
  },
});

ws.on("open", () => {
  console.log("connected");
});

ws.on("message", (event) => {
  const payload = JSON.parse(event.toString());
  console.log(payload);
});

ws.on("error", (error) => {
  console.error("WebSocket error:", error);
});
```

</TabItem>

<TabItem value="python" label="Python">

```python
import asyncio
import json
import websockets

URL = "wss://ws.broker.ru/trade-api-bff-marginal-indicators/api/v1/marginal-indicators/ws"
TOKEN = "YOUR_ACCESS_TOKEN"

async def main():
    async with websockets.connect(
        URL,
        additional_headers={"Authorization": f"Bearer {TOKEN}"},
    ) as ws:
        async for message in ws:
            data = json.loads(message)
            print(data)

if __name__ == "__main__":
    asyncio.run(main())
```

</TabItem>

<TabItem value="go" label="Go (Golang)">

```go
package main

import (
    "log"
    "net/http"

    "github.com/gorilla/websocket"
)

func main() {
    header := http.Header{}
    header.Add("Authorization", "Bearer YOUR_ACCESS_TOKEN")

    c, _, err := websocket.DefaultDialer.Dial(
        "wss://ws.broker.ru/trade-api-bff-marginal-indicators/api/v1/marginal-indicators/ws",
        header,
    )
    if err != nil {
        log.Fatal("dial:", err)
    }
    defer c.Close()

    for {
        _, message, err := c.ReadMessage()
        if err != nil {
            log.Println("read:", err)
            return
        }
        log.Printf("received: %s", message)
    }
}
```

</TabItem>

<TabItem value="java" label="Java">

```java
import okhttp3.*;

public class MarginalIndicatorsWs {
    public static void main(String[] args) {
        OkHttpClient client = new OkHttpClient();

        Request request = new Request.Builder()
            .url("wss://ws.broker.ru/trade-api-bff-marginal-indicators/api/v1/marginal-indicators/ws")
            .addHeader("Authorization", "Bearer YOUR_ACCESS_TOKEN")
            .build();

        WebSocketListener listener = new WebSocketListener() {
            @Override
            public void onOpen(WebSocket webSocket, Response response) {
                System.out.println("connected");
            }

            @Override
            public void onMessage(WebSocket webSocket, String text) {
                System.out.println(text);
            }

            @Override
            public void onFailure(WebSocket webSocket, Throwable t, Response response) {
                t.printStackTrace();
            }
        };

        client.newWebSocket(request, listener);
    }
}
```

</TabItem>

<TabItem value="csharp" label="C#">

```csharp
using System;
using System.Net.WebSockets;
using System.Text;
using System.Threading;
using System.Threading.Tasks;

class Program
{
    static async Task Main()
    {
        using var ws = new ClientWebSocket();
        ws.Options.SetRequestHeader("Authorization", "Bearer YOUR_ACCESS_TOKEN");

        await ws.ConnectAsync(
            new Uri("wss://ws.broker.ru/trade-api-bff-marginal-indicators/api/v1/marginal-indicators/ws"),
            CancellationToken.None
        );

        var buffer = new byte[8192];

        while (ws.State == WebSocketState.Open)
        {
            var result = await ws.ReceiveAsync(buffer, CancellationToken.None);
            if (result.MessageType == WebSocketMessageType.Close)
            {
                break;
            }

            var json = Encoding.UTF8.GetString(buffer, 0, result.Count);
            Console.WriteLine(json);
        }
    }
}
```

</TabItem>
</Tabs>

---

## ❗ Ошибки

| HTTP | Ошибка | Описание |
|------|--------|----------|
| 401 | UNAUTHORIZED | Пользователь не авторизован |
| 400 | BAD_REQUEST | Некорректный запрос |
| 404 | NOT_FOUND | Данные не найдены |
| 5xx | INTERNAL SERVER ERROR | Внутренняя ошибка |
