# Статус заявок

> WebSocket API предоставляет обновления о статусе заявок в реальном времени.

import Tabs from '@theme/Tabs';
import TabItem from '@theme/TabItem';

# Статус заявок

WebSocket API предоставляет обновления о статусе заявок в реальном времени.

---

## 🔌 WebSocket URL

```text
wss://ws.broker.ru/trade-api-bff-operations/api/v1/orders/events/ws
```

## 🔐 Аутентификация

Перед установкой WebSocket-соединения клиент должен передать `access-token` в HTTP-заголовке:

```text
Authorization: Bearer <ACCESS_TOKEN>
```

Подробнее об авторизации см. раздел [Авторизация](/http/authorization).

---

## 📥 Формат ответов server → client

### Успешный ответ

```json
{
  "originalClientOrderId": "517661df-d051-461d-9389-988abf24de4d",
  "clientOrderId": "",
  "data": {
    "messageType": "8",
    "orderStatus": "2",
    "executionType": "2",
    "orderQuantity": 100,
    "executedQuantity": 100,
    "lastQuantity": 50,
    "remainedQuantity": 0,
    "ticker": "SBER",
    "classCode": "TQBR",
    "side": "1",
    "orderType": "2",
    "averagePrice": 244.5,
    "orderId": "20241030-TQBR-12345678910",
    "executionId": "TQBR-Z3fE7c-S-1-1-N",
    "price": 244.5,
    "currency": "RUB",
    "clientCode": "123456",
    "transactionTime": "2024-10-30T09:01:00.000Z",
    "tradeDate": "2024-10-30",
    "orderNumber": "12345678910",
    "accruedCoupon": 0,
    "executionValue": 24450,
    "commission": 12.3,
    "securityExchange": "TQBR"
  }
}
```

---

## 📄 Описание параметров

### Поля ответа

| Поле | Тип | Описание |
|------|-----|----------|
| `originalClientOrderId` | string (uuid) | Уникальный идентификатор измененной заявки |
| `clientOrderId` | string (uuid) | Уникальный идентификатор заявки |
| `data` | object | Детали исполнения |

### Поля объекта `data`

| Поле | Тип | Описание |
|------|-----|----------|
| `messageType` | string | Тип сообщения |
| `orderStatus` | string (enum) | Состояние заявки:`0` — Новая`1` — Частично исполнена`2` — Полностью исполнена`4` — Отменена`5` — Заменена`6` — Отменяется (в процессе отмены)`8` — Отклонена`9` — Заменяется (например, если вы изменяли заявку)`10` — Ожидает подтверждения новой заявки |
| `executionType` | string (enum) | Тип отчета. Возможные значения:`0` — Новая`1` — Частично исполнена`2` — Исполнена`4` — Отменена`6` — Ожидает отмены`5` — Заменена`8` — Отклонена`9` — Приостановлена`10` — Ожидает подтверждения новой`11` — Сделка`12` — Статус заявки`13` — Исправлено |
| `orderQuantity` | number | Количество в заявке (шт.) |
| `executedQuantity` | number | Исполненное количество (шт.) |
| `lastQuantity` | number | Количество в текущей сделке (шт.) |
| `remainedQuantity` | number | Оставшееся количество (шт.) |
| `ticker` | string | Тикер инструмента |
| `classCode` | string | Код класса ценной бумаги |
| `side` | string (enum) | `1` – Покупка`2` – Продажа |
| `orderType` | string (enum) | `1` – Рыночная заявка`2` – Лимитная заявка |
| `averagePrice` | number | Средняя цена исполнения. *Для сделок РЕПО/спредов:* цена первой части |
| `orderId` | string | Уникальный идентификатор заявки |
| `executionId` | string | Уникальный идентификатор сделки |
| `price` | number | Цена заявки |
| `currency` | string | Валюта |
| `clientCode` | string | Код клиента |
| `transactionTime` | string (datetime) | Дата и время транзакции |
| `tradeDate` | string (datetime) | Дата сделки:Для вечерней сессии FORTS - дата следующей сессииОбычные сделки - дата исполнения |
| `orderNumber` | string | Номер заявки |
| `accruedCoupon` | number | Накопленный купонный доход |
| `executionValue` | number | Объем сделки |
| `commission` | number | Комиссия |
| `securityExchange` | string | Идентификатор биржи |
---

## 💻 Примеры использования

<Tabs>

<TabItem value="js" label="JavaScript (Node.js)">

```js
import WebSocket from "ws";

const URL = "wss://ws.broker.ru/trade-api-bff-operations/api/v1/orders/events/ws";

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

URL = "wss://ws.broker.ru/trade-api-bff-operations/api/v1/orders/events/ws"
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
        "wss://ws.broker.ru/trade-api-bff-operations/api/v1/orders/events/ws",
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

public class ExecutionStatusWs {
    public static void main(String[] args) {
        OkHttpClient client = new OkHttpClient();

        Request request = new Request.Builder()
            .url("wss://ws.broker.ru/trade-api-bff-operations/api/v1/orders/events/ws")
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
            new Uri("wss://ws.broker.ru/trade-api-bff-operations/api/v1/orders/events/ws"),
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
| 401 | UNAUTHORIZED | Ошибка авторизации |
| 500 | INTERNAL SERVER ERROR | Внутренняя ошибка сервера |
