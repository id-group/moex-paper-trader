# Котировки

> WebSocket API позволяет получать **котировки инструмента** в реальном времени.

import Tabs from '@theme/Tabs';
import TabItem from '@theme/TabItem';

# Котировки

WebSocket API позволяет получать **котировки инструмента** в реальном времени.

---

## 🔌 WebSocket URL

```text
wss://ws.broker.ru/trade-api-market-data-connector/api/v1/market-data/ws
```

## 🔐 Аутентификация

Перед установкой WebSocket-соединения клиент должен передать `access-token` в HTTP-заголовке:

```text
Authorization: Bearer <ACCESS_TOKEN>
```

Подробнее об авторизации см. раздел [Авторизация](/http/authorization).

---

## 🧱 Общий формат сообщений

Все сообщения в WebSocket передаются в формате **JSON**.

- Клиент → сервер: команды подписки / отписки.
- Сервер → клиент: подтверждения подписки, данные котировок, ошибки.

---

## 📤 Сообщения client → server

### Подписка на котировки

```json
{
  "subscribeType": 0,
  "dataType": 3,
  "instruments": [
    { "ticker": "SBER", "classCode": "TQBR" }
  ]
}
```

### Поля запроса

| Поле | Где | Тип | Обяз. | Описание |
|------|-----|-----|-------|----------|
| `subscribeType` | Тело | number (enum) | да | Тип сообщения:`0` — Подписка`1` — Отписка |
| `dataType` | Тело | number (enum) | да | Тип данных:`3` — Котировка |
| `instruments` | Тело | array | да | Список инструментов для подписки |
| `instruments[].ticker` | Тело | string | да | Биржевой тикер |
| `instruments[].classCode` | Тело | string | да | Код класса бумаги |

### Отписка

```json
{
  "subscribeType": 1,
  "dataType": 3,
  "instruments": [
    { "ticker": "SBER", "classCode": "TQBR" }
  ]
}
```

---

## 📥 Ответы server → client

### Успешный ответ

```json
{
  "responseType": "Quotes",
  "ticker": "SBER",
  "classCode": "TQBR",
  "type": "refresh",
  "dateTime": "2024-11-01T10:00:00.000Z",
  "securityTradingStatus": 17,
  "currency": "RUB",
  "bid": 305.86,
  "offer": 305.96,
  "open": 304.76,
  "close": 304.75,
  "high": 307.24,
  "low": 304.75,
  "theoreticalPrice": 0,
  "last": 305.97,
  "bidYield": 0,
  "offerYield": 0,
  "change": 1.05,
  "changeRate": 0.34
}
```

### Поля ответа

| Поле | Тип | Описание                                                                                                                                                                                                                                                |
|------|-----|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `responseType` | string (enum) | Тип сообщения: `Quotes` — данные котировки                                                                                                                                                                                                              |
| `ticker` | string | Биржевой тикер                                                                                                                                                                                                                                          |
| `classCode` | string | Код класса бумаги                                                                                                                                                                                                                                       |
| `type` | string | Тип объекта котировки                                                                                                                                                                                                                                                    |
| `dateTime` | string (datetime) | Время обновления данных (UTC)                                                                                                                                                                                                                           |
| `securityTradingStatus` | number (enum) | Статус торговли инструментом`2` — Торги приостановлены`17` — Торги открыты`18` — Торги закрыты`100` – Закрытие торгов`101` — Открытие торгов`102` — Аукцион`103` — Аукцион закрытия`104` — Дискретный аукцион |
| `currency` | string | Валюта котировки                                                                                                                                                                                                                                        |
| `last` | number | Последняя цена сделки                                                                                                                                                                                                                                   |
| `bid` | number | Лучшая цена покупки                                                                                                                                                                                                                                     |
| `offer` | number | Лучшая цена продажи                                                                                                                                                                                                                                     |
| `open` | number | Цена открытия торговой сессии                                                                                                                                                                                                                           |
| `close` | number | Цена закрытия предыдущей сессии                                                                                                                                                                                                                         |
| `high` | number | Максимальная цена за день                                                                                                                                                                                                                               |
| `low` | number | Минимальная цена за день                                                                                                                                                                                                                                |
| `theoreticalPrice` | number | Цена расчетная (у опциона)                                                                                                                                                                                                                              |
| `bidYield` | number | Доходность котировок покупки (для облигаций)                                                                                                                                                                                                            |
| `offerYield` | number | Доходность котировок продажи (для облигаций)                                                                                                                                                                                                            |
| `change` | number | Изменение цены за текущую сессию, в валюте цены                                                                                                                                                                                                         |
| `changeRate` | number | Изменение цены за текущую сессию, в %                                                                                                                                                                                                                   |

---

### Пример ответа с ошибкой

```json
{
  "responseType": "Quotes",
  "errors": [
    {
      "message": "Input JSON structure does not match structure, 'ticker' field is undefined.",
      "code": "INCORRECT_JSON"
    }
  ]
}
```

### Структура данных ошибки

| Поле | Тип | Описание |
|------|-----|----------|
| `responseType` | string (enum) | Тип сообщения. Для ошибок по сделкам — `Quotes` |
| `errors[].message` | string | Текст ошибки |
| `errors[].code`    | string (enum) | Код ошибки:`NO_DATE` — нет данных`NOT_FOUND` — инструмент не найден`INCORRECT_JSON` – невалидный json`BAD_REQUEST` — ошибка выполнения`UNAUTHORIZED` — клиент не авторизован |

---

## 💻 Примеры использования

<Tabs>

<TabItem value="js" label="JavaScript (Node.js)">

```js
import WebSocket from "ws";

const URL = "wss://ws.broker.ru/trade-api-market-data-connector/api/v1/market-data/ws";

const ws = new WebSocket(URL, {
  headers: {
    Authorization: "Bearer YOUR_ACCESS_TOKEN",
  },
});

ws.on("open", () => {
  const subscribeMessage = {
    subscribeType: 0,
    dataType: 3,
    instruments: [{ ticker: "SBER", classCode: "TQBR" }],
  };

  ws.send(JSON.stringify(subscribeMessage));
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

URL = "wss://ws.broker.ru/trade-api-market-data-connector/api/v1/market-data/ws"
TOKEN = "YOUR_ACCESS_TOKEN"

async def main():
    async with websockets.connect(
        URL,
        additional_headers={"Authorization": f"Bearer {TOKEN}"},
    ) as ws:
        subscribe_message = {
            "subscribeType": 0,
            "dataType": 3,
            "instruments": [{"ticker": "SBER", "classCode": "TQBR"}],
        }
        await ws.send(json.dumps(subscribe_message))

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
        "wss://ws.broker.ru/trade-api-market-data-connector/api/v1/market-data/ws",
        header,
    )
    if err != nil {
        log.Fatal("dial:", err)
    }
    defer c.Close()

    subscribeMessage := map[string]interface{}{
        "subscribeType": 0,
        "dataType":       3,
        "instruments": []map[string]string{
            {"ticker": "SBER", "classCode": "TQBR"},
        },
    }

    if err := c.WriteJSON(subscribeMessage); err != nil {
        log.Fatal("write:", err)
    }

    for {
        var payload map[string]interface{}
        if err := c.ReadJSON(&payload); err != nil {
            log.Println("read:", err)
            return
        }
        log.Printf("received: %#v", payload)
    }
}
```

</TabItem>

<TabItem value="java" label="Java">

```java
import okhttp3.*;

public class QuotesWs {
    public static void main(String[] args) {
        OkHttpClient client = new OkHttpClient();

        Request request = new Request.Builder()
            .url("wss://ws.broker.ru/trade-api-market-data-connector/api/v1/market-data/ws")
            .addHeader("Authorization", "Bearer YOUR_ACCESS_TOKEN")
            .build();

        WebSocketListener listener = new WebSocketListener() {
            @Override
            public void onOpen(WebSocket webSocket, Response response) {
                String subscribeMessage = """
                    {
                      "subscribeType": 0,
                      "dataType": 3,
                      "instruments": [
                        { "ticker": "SBER", "classCode": "TQBR" }
                      ]
                    }
                    """;
                webSocket.send(subscribeMessage);
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
using System.Text.Json;
using System.Threading;
using System.Threading.Tasks;

class Program
{
    static async Task Main()
    {
        using var ws = new ClientWebSocket();
        ws.Options.SetRequestHeader("Authorization", "Bearer YOUR_ACCESS_TOKEN");

        await ws.ConnectAsync(
            new Uri("wss://ws.broker.ru/trade-api-market-data-connector/api/v1/market-data/ws"),
            CancellationToken.None
        );

        var subscribeMessage = new
        {
            subscribeType = 0,
            dataType = 3,
            instruments = new[]
            {
                new { ticker = "SBER", classCode = "TQBR" }
            }
        };

        string payload = JsonSerializer.Serialize(subscribeMessage);
        await ws.SendAsync(
            Encoding.UTF8.GetBytes(payload),
            WebSocketMessageType.Text,
            endOfMessage: true,
            cancellationToken: CancellationToken.None
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
| 400 | BadRequest | Неверные параметры |
| 500 | Internal Server Error | Внутренняя ошибка сервера |
