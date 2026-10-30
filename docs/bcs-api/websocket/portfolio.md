# Портфель

> WebSocket API предоставляет **данные о позициях портфеля**, стоимости активов, P/L, валютных остатках и блокировках в режиме реального времени.

import Tabs from '@theme/Tabs';
import TabItem from '@theme/TabItem';

# Портфель

WebSocket API предоставляет **данные о позициях портфеля**, стоимости активов, P/L, валютных остатках и блокировках в режиме реального времени.

---

## 🔌 WebSocket URL

```
wss://ws.broker.ru/trade-api-bff-portfolio/api/v1/portfolio/ws
```

## 🔐 Аутентификация

Перед установкой WebSocket-соединения клиент должен передать `access-token` в HTTP-заголовке:

```text
Authorization: Bearer <ACCESS_TOKEN>
```

Подробнее об авторизации см. раздел [Авторизация](/http/authorization).

---

## 📥 Формат server → client

Сервер отправляет массив объектов — каждая запись описывает одну позицию в портфеле.

---

### Структура данных

Ниже приведены ключевые параметры позиций.

| Поле | Тип | Описание |
|------|------|----------|
| `type` | string | Тип позиции |
| ~~`subAccountId`~~ | string (uuid) | ID субсчета *(deprecated)* |
| ~~`agreementId`~~ | string (uuid) | ID генерального соглашения *(deprecated)* |
| `account` | string | Торговый счет |
| `exchange` | string | Код биржи |
| `ticker` | string | Тикер инструмента |
| `displayName` | string | Наименование инструмента |
| `baseAssetTicker` | string | Тикер базового актива |
| `currency` | string | Валюта цены |
| `expireDate` | date | Дата экспирации |
| `upperType` | string (enum) | Верхнеуровневый тип инструмента |
| `instrumentType` | string (enum) | Тип инструмента |
| `term` | string | Код срока расчетов |
| `quantity` | number | Количество (шт.) |
| `locked` | number | Количество активов, занятых под активные заявки или ГО |
| `balancePrice` | number | Балансовая цена (цена открытия позиции) в валюте цены |
| `currentPrice` | number | Текущая цена |
| `balanceValue` | number | Балансовая стоимость (цена открытия позиции) в валюте цены |
| `balanceValueRub` | number | Балансовая стоимость в рублях |
| `balanceValueUsd` | number | Балансовая стоимость в долларах |
| `balanceValueEur` | number | Балансовая стоимость в евро |
| `currentValue` | number | Текущая стоимость в валюте цены |
| `currentValueRub` | number | Текущая стоимость в рублях |
| `currentValueUsd` | number | Текущая стоимость в долларах |
| `currentValueEur` | number | Текущая стоимость в евро |
| `unrealizedPL` | number | Изменение цены с момента открытия позиции |
| `unrealizedPercentPL` | number | Изменение цены с момента открытия позиции, % |
| `dailyPL` | number | Дневное изменение цены с момента открытия позиции |
| `dailyPercentPL` | number | Дневное изменение цены с момента открытия позиции, % |
| `portfolioShare` | number | Доля в портфеле, % (от рублевой стоимости портфеля) |
| `scale` | integer | Количество знаков |
| `minimumStep` | number | Минимальный шаг цены |
| `board` | string | Код класса ценной бумаги |
| `priceUnit` | string | Единица цены |
| `faceValue` | number | Номинал (для облигаций) |
| `accruedIncome` | number | Накопленный купонный доход (для облигаций) |
| ~~`logoLink`~~ | string | Ссылка на логотип инструмента *(deprecated)* |
| `isBlocked` | boolean | Признак блокированного класса |
| `isBlockedTradeAccount` | boolean | Заблокирован ли торговый счет |
| `lockedForFutures` | number | Занято под ГО по фьючерсам |
| `ratioQuantity` | number | Количество в лоте |

---

## 📋 Пример ответа

```json
[
  {
    "type": "moneyLimit",
    "subAccountId": "49909a5f-8cfe-441a-b3b7-86054eb97922",
    "agreementId": "80675226-b724-4abb-92f8-56bf5c7bf7f0",
    "account": "1234567/25",
    "exchange": "MOEX",
    "ticker": "RUB",
    "displayName": "RUB",
    "baseAssetTicker": "",
    "currency": "RUB",
    "upperType": "CURRENCY",
    "instrumentType": "CURRENCY",
    "term": "T0",
    "quantity": 4544.79,
    "locked": 0,
    "balancePrice": 1,
    "currentPrice": 1,
    "balanceValue": 4544.79,
    "balanceValueRub": 4544.79,
    "balanceValueUsd": 55.8843,
    "balanceValueEur": 48.0981,
    "currentValue": 4544.79,
    "currentValueRub": 4544.79,
    "currentValueUsd": 55.8843,
    "currentValueEur": 48.0981,
    "unrealizedPL": 0,
    "unrealizedPercentPL": 0,
    "dailyPL": 0,
    "dailyPercentPL": 0,
    "portfolioShare": 0,
    "scale": 4,
    "minimumStep": 0.01,
    "board": "",
    "priceUnit": "",
    "faceValue": 0,
    "accruedIncome": 0,
    "isBlocked": false,
    "isBlockedTradeAccount": false,
    "lockedForFutures": 0,
    "ratioQuantity": 0,
    "expireDate": ""
  },
  {
    "type": "depoLimit",
    "subAccountId": "49909a5f-8cfe-441a-b3b7-86054eb97922",
    "agreementId": "80675226-b724-4abb-92f8-56bf5c7bf7f0",
    "account": "3534991/25",
    "exchange": "MOEX",
    "ticker": "SBER",
    "displayName": "Сбербанк",
    "baseAssetTicker": "",
    "currency": "RUB",
    "upperType": "RUSSIA",
    "instrumentType": "STOCK",
    "term": "T0",
    "quantity": 2,
    "locked": 0,
    "balancePrice": 300.5267,
    "currentPrice": 296,
    "balanceValue": 601.0533,
    "balanceValueRub": 601.0533,
    "balanceValueUsd": 7.3908,
    "balanceValueEur": 6.361,
    "currentValue": 592,
    "currentValueRub": 592,
    "currentValueUsd": 7.2794,
    "currentValueEur": 6.2652,
    "unrealizedPL": -9.0533,
    "unrealizedPercentPL": -1.5062,
    "dailyPL": 0,
    "dailyPercentPL": 0,
    "portfolioShare": 0,
    "scale": 2,
    "minimumStep": 0.01,
    "board": "TQBR",
    "priceUnit": "",
    "faceValue": 3,
    "accruedIncome": 0,
    "isBlocked": false,
    "isBlockedTradeAccount": false,
    "lockedForFutures": 0,
    "ratioQuantity": 0,
    "expireDate": ""
  }
]
```

---

## 💻 Примеры подключения

<Tabs>
<TabItem value="js" label="JavaScript (Node.js)">

```js
import WebSocket from "ws";

const URL = "wss://ws.broker.ru/trade-api-bff-portfolio/api/v1/portfolio/ws";

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

URL = "wss://ws.broker.ru/trade-api-bff-portfolio/api/v1/portfolio/ws"
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
        "wss://ws.broker.ru/trade-api-bff-portfolio/api/v1/portfolio/ws",
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

public class PortfolioWs {
    public static void main(String[] args) {
        OkHttpClient client = new OkHttpClient();

        Request request = new Request.Builder()
            .url("wss://ws.broker.ru/trade-api-bff-portfolio/api/v1/portfolio/ws")
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
            new Uri("wss://ws.broker.ru/trade-api-bff-portfolio/api/v1/portfolio/ws"),
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
| 400 | VALIDATION_ERROR | Ошибка валидации |
| 404 | NOT_FOUND | Данные не найдены |
| 500 | INTERNAL_SERVER_ERROR | Ошибка сервера |
