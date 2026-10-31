# Портфель

> Получение информации о вашем портфеле через сервис «Портфель»

import MethodEndpoint from "@theme/ApiExplorer/MethodEndpoint";
import ParamsDetails from "@theme/ParamsDetails";
import RequestSchema from "@theme/RequestSchema";
import StatusCodes from "@theme/StatusCodes";
import OperationTabs from "@theme/OperationTabs";
import TabItem from "@theme/TabItem";
import Heading from "@theme/Heading";

<Heading
  as={"h1"}
  className={"openapi__heading"}
  children={"Портфель"}
>
</Heading>

<MethodEndpoint
  method={"get"}
  path={"/api/v1/portfolio"}
  context={"endpoint"}
>
  
</MethodEndpoint>

Получение информации о вашем портфеле через сервис «Портфель»

<ParamsDetails
  parameters={undefined}
>
  
</ParamsDetails>

<RequestSchema
  title={"Body"}
  body={undefined}
>
  
</RequestSchema>

<StatusCodes
  id={undefined}
  label={undefined}
  responses={{"200":{"description":"OK","content":{"application/json":{"schema":{"type":"array","items":{"type":"object","properties":{"type":{"type":"string","description":"Тип позиции","enum":["moneyLimit","depoLimit","futuresLimit","futuresHolding","otcLimit"]},"subAccountId":{"type":"string","format":"uuid","description":"ID субсчета","deprecated":true},"agreementId":{"type":"string","format":"uuid","description":"ID генерального соглашения","deprecated":true},"account":{"type":"string","description":"Торговый счет"},"exchange":{"type":"string","description":"Код биржи"},"ticker":{"type":"string","description":"Тикер инструмента"},"displayName":{"type":"string","description":"Наименование инструмента"},"baseAssetTicker":{"type":"string","description":"Тикер базового актива"},"currency":{"type":"string","description":"Валюта цены"},"upperType":{"type":"string","description":"Верхнеуровневый тип инструмента","enum":["CURRENCY","RUSSIA","FOREIGN","OTC"]},"instrumentType":{"type":"string","description":"Тип инструмента"},"term":{"type":"string","description":"Код срока расчетов","enum":["T0","T1","T2","T365"]},"quantity":{"type":"number","description":"Количество (шт.)"},"locked":{"type":"number","description":"Количество активов, занятых под активные заявки или ГО"},"balancePrice":{"type":"number","description":"Балансовая цена (цена открытия позиции) в валюте цены"},"currentPrice":{"type":"number","description":"Текущая цена"},"balanceValue":{"type":"number","description":"Балансовая стоимость (цена открытия позиции) в валюте цены"},"balanceValueRub":{"type":"number","description":"Балансовая стоимость в рублях"},"balanceValueUsd":{"type":"number","description":"Балансовая стоимость в долларах"},"balanceValueEur":{"type":"number","description":"Балансовая стоимость в евро"},"currentValue":{"type":"number","description":"Текущая стоимость в валюте цены"},"currentValueRub":{"type":"number","description":"Текущая стоимость в рублях"},"currentValueUsd":{"type":"number","description":"Текущая стоимость в долларах"},"currentValueEur":{"type":"number","description":"Текущая стоимость в евро"},"unrealizedPL":{"type":"number","description":"Изменение цены с момента открытия позиции"},"unrealizedPercentPL":{"type":"number","description":"Изменение цены с момента открытия позиции, % "},"dailyPL":{"type":"number","description":"Дневное изменение цены с момента открытия позиции"},"dailyPercentPL":{"type":"number","description":"Дневное изменение цены с момента открытия позиции, %"},"portfolioShare":{"type":"number","description":"Доля в портфеле, % (от рублевой стоимости портфеля)"},"scale":{"type":"integer","format":"int32","description":"Количество знаков"},"minimumStep":{"type":"number","description":"Минимальный шаг цены"},"board":{"type":"string","description":"Код класса ценной бумаги"},"priceUnit":{"type":"string","description":"Единица цены"},"faceValue":{"type":"number","description":"Номинал (для облигаций)"},"accruedIncome":{"type":"number","description":"Накопленный купонный доход (для облигаций)"},"logoLink":{"type":"string","description":"Ссылка на логотип инструмента"},"isBlocked":{"type":"boolean","description":"Признак блокированного класса"},"isBlockedTradeAccount":{"type":"boolean","description":"Заблокирован ли торговый счет"},"lockedForFutures":{"type":"number","description":"Занято под ГО по фьючерсам"},"ratioQuantity":{"type":"number","description":"Количество в лоте"},"expireDate":{"type":"string","description":"Дата экспирации"}},"title":"Portfolio"}}}}},"429":{"description":"Too Many Requests","content":{"application/json":{"schema":{"type":"object","properties":{"timestamp":{"type":"integer","format":"int64"},"traceId":{"type":"string"},"type":{"type":"string","enum":["VALIDATION_ERROR","RESOURCE_EXHAUSTED","USER_BLOCKED","BAD_REQUEST","NOT_FOUND","UNAUTHORIZED","FORBIDDEN","CONFLICT","INTERNAL_SERVER_ERROR","SESSION_NOT_FOUND_ERROR","SESSION_EXPIRED_ERROR","SESSION_FAILED_ERROR"]},"errors":{"type":"array","items":{"type":"object","properties":{"type":{"type":"string"},"field":{"type":"string"},"payload":{"type":"object","additionalProperties":{"type":"object"}}},"title":"ErrorDetail"}},"displayOptions":{"type":"object","additionalProperties":{"type":"object"}}},"title":"ErrorResponse"}}}}}}
>
  
</StatusCodes>
