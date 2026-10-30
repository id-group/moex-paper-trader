# Лимиты

> WebSocket API предоставляет **портфельные данные клиента в реальном времени**: позиции, денежные лимиты, фьючерсные данные, ГО, долговые нагрузки и др.

import Tabs from "@theme/Tabs";
import TabItem from "@theme/TabItem";

# Лимиты

WebSocket API предоставляет **портфельные данные клиента в реальном времени**: позиции, денежные лимиты, фьючерсные данные, ГО, долговые нагрузки и др.

---

## 🔌 WebSocket URL

```
wss://ws.broker.ru/trade-api-bff-limit/api/v1/limits/ws
```

## 🔐 Аутентификация

Перед установкой WebSocket-соединения клиент должен передать `access-token` в HTTP-заголовке:

```text
Authorization: Bearer <ACCESS_TOKEN>
```

Подробнее об авторизации см. раздел [Авторизация](/http/authorization).

---

## 📥 Формат server → client

Сервер отправляет **единый объект**, содержащий актуальные данные:

- `depoLimit`
- `futureHolding`
- `moneyLimits`
- `futuresLimits`

---

### 📦 `depoLimit` — позиции по инструментам

| Поле                | Тип      | Описание                                                     |
| ------------------- | -------- | ------------------------------------------------------------ |
| `ticker`              | string   | Тикер инструмента                                            |
| `classCode`           | string   | Код класса ценной бумаги                                     |
| `exchange`            | string   | Код биржи                                                    |
| `averagePrice`        | number   | Средневзвешенная цена открытия позиции                       |
| `quantity.type`      | string (enum) | Режим расчетов — срок, в течение которого произойдет расчет по сделке. [Подробнее](https://bcs.ru/faq/category/115/581)            |
| `quantity.value`      | number   | Количество этого актива в вашем портфеле в штуках            |
| `quantityBatch.type` | string (enum) | Режим расчетов — срок, в течение которого произойдет расчет по сделке. [Подробнее](https://bcs.ru/faq/category/115/581)             |
| `quantityBatch.value` | number   | Количество этого актива в вашем портфеле в лотах             |
| `instrumentType`      | string (enum)   | Тип инструмента (например, акция, облигация)                 |
| `loadDate`            | string (datetime) | Дата и время обновления данных                               |
| `lockedBuyValue`      | number   | Количество денег в активных заявках на покупку данной бумаги |
| `lockedSellValue`     | number   | Количество денег в активных заявках на продажу данной бумаги |
| `lockedBuyQuantity`     | number   | Количество заблокированных ценных бумаг под заявки на покупку в штуках |
| `lockedSellQuantity`     | number   | Количество заблокированных ценных бумаг под заявки на продажу в штуках |

---

### 📦 `futureHolding` — фьючерсные позиции

| Поле           | Тип      | Описание                                                                                |
| -------------- | -------- | --------------------------------------------------------------------------------------- |
| `ticker`         | string   | Тикер инструмента                                                                       |
| `classCode`      | string   | Код класса ценной бумаги                                                                |
| `exchange`       | string   | Код биржи                                                                               |
| `cbplPlanned`    | number   | Стоимостная оценка планируемых позиций после ближайшего клиринга                        |
| `varMargin`      | number   | Оценка размера вариационной марки по позициям                                           |
| `positionValue`  | number   | Стоимость позиций срочного рынка                                                        |
| `totalNet`       | number   | Текущие чистые позиции                                                                  |
| `executionDate`  | string (datetime) | Дата исполнения                                                                         |
| `totalVarMargin` | number   | Суммарная вариационная маржа по итогам основного клиринга, начисленная по всем позициям |
| `realVarMargin`  | number   | Начисленная в ходе клиринга вариационная маржа                                          |
| `averagePrice`   | number   | Средневзвешенная цена открытия позиции                                                  |
| `instrumentType` | string (enum)   | Тип инструмента                                                                         |
| `tradeDate`      | string (datetime) | Дата торговой сессии                                                                    |

---

### 📦 `moneyLimits` — остатки по валютам

| Поле           | Тип      | Описание                                          |
| -------------- | -------- | ------------------------------------------------- |
| `exchange`       | string   | Код биржи                                         |
| `currencyCode`   | string   | Код валюты                                        |
| `locked`         | number   | Количество денег, занятых в активных заявках      |
| `averagePrice`   | number   | Средневзвешенная цена открытия позиции            |
| `instrumentType` | string (enum)   | Тип инструмента                                   |
| `quantity.type`      | string (enum) | Режим расчетов — срок, в течение которого произойдет расчет по сделке. [Подробнее](https://bcs.ru/faq/category/115/581)            |
| `quantity.value` | number   | Количество этого актива в вашем портфеле в штуках |
| `loadDate`       | string (datetime) | Дата и время обновления данных                    |

---

### 📦 `futuresLimits` — лимиты по срочному рынку

| Поле                 | Тип      | Описание                                                         |
| -------------------- | -------- | ---------------------------------------------------------------- |
| `currencyCode`         | string   | Код валюты                                                       |
| `exchange`             | string   | Код биржи                                                        |
| `accruedint`           | number   | Вариационная маржа на текущий день                               |
| `cbpLimit`             | number   | Текущий лимит открытых позиций                                   |
| `cbplUsed`             | number   | Позиции после последнего клиринга                                |
| `cbplPlanned`          | number   | Стоимостная оценка планируемых позиций после ближайшего клиринга |
| `cbplUsedForOrders`    | number   | Величина ГО, зарезервированного под клиентские заявки            |
| `cbplUsedForPositions` | number   | Величина ГО, зарезервированного под открытые клиентские позиции  |
| `optionsPremium`       | number   | Премии по опционам                                               |
| `instrumentType`       | string (enum)   | Тип инструмента                                                  |
| `loadDate`             | string (datetime) | Дата и время обновления данных                                   |
| `varMargin`            | number   | Оценка размера вариационной марки по позициям                    |
| `realVarMargin`        | number   | Начисленная в ходе клиринга вариационная маржа                   |

---

### 📋 Пример ответа

```json
{
  "depoLimit": [
    {
      "ticker": "SBER",
      "classCode": "TQBR",
      "exchange": "MOEX",
      "averagePrice": 300.526666,
      "quantity": {
        "type": "T365",
        "value": 2
      },
      "quantityBatch": {
        "type": "T365",
        "value": 2
      },
      "instrumentType": "STOCK",
      "loadDate": "2025-11-13T21:00:00.000Z",
      "lockedBuyValue": 0,
      "lockedSellValue": 0,
      "lockedBuyQuantity": 0,
      "lockedSellQuantity": 0
    }
  ],
  "futureHolding": [],
  "moneyLimits": [
    {
      "exchange": "MOEX",
      "currencyCode": "RUB",
      "locked": 0,
      "averagePrice": 0,
      "instrumentType": "MONEY",
      "quantity": {
        "type": "T365",
        "value": 4544.79
      },
      "loadDate": "2025-11-13T21:00:00.000Z"
    },
    {
      "exchange": "MOEX",
      "currencyCode": "USD",
      "locked": 0,
      "averagePrice": 0,
      "instrumentType": "MONEY",
      "quantity": {
        "type": "T365",
        "value": 0
      },
      "loadDate": "2025-11-13T21:00:00.000Z"
    }
  ],
  "futuresLimits": [
    {
      "currencyCode": "RUB",
      "exchange": "FORTS",
      "accruedint": 0,
      "cbpLimit": -0.26,
      "cbplUsed": 0,
      "cbplPlanned": 0,
      "cbplUsedForOrders": 0,
      "cbplUsedForPositions": 0,
      "optionsPremium": 0,
      "instrumentType": "MONEY",
      "loadDate": "2025-11-13T21:00:00.000Z",
      "varMargin": 0,
      "realVarMargin": 0
    }
  ]
}
```

---

## 💻 Примеры подключения

<Tabs>
<TabItem value="js" label="JavaScript (Node.js)">

```js
import WebSocket from "ws";

const URL = "wss://ws.broker.ru/trade-api-bff-limit/api/v1/limits/ws";

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

URL = "wss://ws.broker.ru/trade-api-bff-limit/api/v1/limits/ws"
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
        "wss://ws.broker.ru/trade-api-bff-limit/api/v1/limits/ws",
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

public class LimitsWs {
    public static void main(String[] args) {
        OkHttpClient client = new OkHttpClient();

        Request request = new Request.Builder()
            .url("wss://ws.broker.ru/trade-api-bff-limit/api/v1/limits/ws")
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
            new Uri("wss://ws.broker.ru/trade-api-bff-limit/api/v1/limits/ws"),
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

| HTTP | Ошибка                | Описание                    |
| ---- | --------------------- | --------------------------- |
| 401  | UNAUTHORIZED          | Пользователь не авторизован |
| 500  | INTERNAL SERVER ERROR | Ошибка сервера              |
