# Лимиты

> Получение информации о вашем портфеле через сервис «Лимиты»

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
  children={"Лимиты"}
>
</Heading>

<MethodEndpoint
  method={"get"}
  path={"/api/v1/limits"}
  context={"endpoint"}
>
  
</MethodEndpoint>

Получение информации о вашем портфеле через сервис «Лимиты»

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
  responses={{"200":{"description":"OK","content":{"application/json":{"schema":{"type":"object","properties":{"depoLimit":{"type":"array","items":{"type":"object","properties":{"ticker":{"type":"string","description":"Тикер инструмента"},"classCode":{"type":"string","description":"Код класса ценной бумаги"},"exchange":{"type":"string","description":"Код биржи"},"averagePrice":{"type":"number","description":"Средневзвешенная цена открытия позиции"},"quantity":{"type":"object","properties":{"type":{"type":"string","enum":["T0","T1","T2","T365"],"description":"Режим расчетов — срок, в течение которого произойдет расчет по сделке. [Подробнее](https://bcs.ru/faq/category/115/581)"},"value":{"type":"number","description":"Количество этого актива в вашем портфеле в штуках"}}},"quantityBatch":{"type":"object","properties":{"type":{"type":"string","enum":["T0","T1","T2","T365"],"description":"Режим расчетов — срок, в течение которого произойдет расчет по сделке. [Подробнее](https://bcs.ru/faq/category/115/581)"},"value":{"type":"number","description":"Количество этого актива в вашем портфеле в лотах"}}},"instrumentType":{"type":"string","description":"Тип инструмента (например, акция, облигация)"},"loadDate":{"type":"string","format":"date-time","description":"Дата и время обновления данных"},"lockedBuyValue":{"type":"number","description":"Количество денег в активных заявках на покупку данной бумаги"},"lockedSellValue":{"type":"number","description":"Количество денег в активных заявках на продажу данной бумаги"},"lockedBuyQuantity":{"type":"number","description":"Количество заблокированных ценных бумаг под заявки на покупку в штуках"},"lockedSellQuantity":{"type":"number","description":"Количество заблокированных ценных бумаг под заявки на продажу в штуках"}},"title":"DepoLimitResponse"}},"futureHolding":{"type":"array","items":{"type":"object","properties":{"ticker":{"type":"string","description":"Тикер инструмента"},"classCode":{"type":"string","description":"Код класса ценной бумаги"},"exchange":{"type":"string","description":"Код биржи"},"cbplPlanned":{"type":"number","description":"Стоимостная оценка планируемых позиций после ближайшего клиринга"},"varMargin":{"type":"number","description":"Оценка размера вариационной марки по позициям"},"positionValue":{"type":"number","description":"Стоимость позиций срочного рынка"},"totalNet":{"type":"number","description":"Текущие чистые позиции"},"executionDate":{"type":"string","format":"date-time","description":"Дата исполнения"},"totalVarMargin":{"type":"number","description":"Суммарная вариационная маржа по итогам основного клиринга, начисленная по всем позициям "},"realVarMargin":{"type":"number","description":"Начисленная в ходе клиринга вариационная маржа"},"averagePrice":{"type":"number","description":"Средневзвешенная цена открытия позиции"},"instrumentType":{"type":"string","description":"Тип инструмента"},"tradeDate":{"type":"string","format":"date-time","description":"Дата торговой сессии"}},"title":"FutureHoldingResponse"}},"moneyLimits":{"type":"array","items":{"type":"object","properties":{"exchange":{"type":"string","description":"Код биржи"},"currencyCode":{"type":"string","description":"Код валюты"},"locked":{"type":"number","description":"Количество денег, занятых в активных заявках"},"averagePrice":{"type":"number","description":"Средневзвешенная цена открытия позиции"},"instrumentType":{"type":"string","description":"Тип инструмента"},"quantity":{"type":"object","properties":{"type":{"type":"string","enum":["T0","T1","T2","T365"],"description":"Режим расчетов — срок, в течение которого произойдет расчет по сделке. [Подробнее](https://bcs.ru/faq/category/115/581)"},"value":{"type":"number","description":"Количество этого актива в вашем портфеле в штуках"}}},"loadDate":{"type":"string","format":"date-time","description":"Дата и время обновления данных"}},"title":"MoneyLimitResponse"}},"futuresLimits":{"type":"array","items":{"type":"object","properties":{"currencyCode":{"type":"string","description":"Код валюты"},"exchange":{"type":"string","description":"Код биржи"},"accruedint":{"type":"number","description":"Вариационная маржа на текущий день"},"cbpLimit":{"type":"number","description":"Текущий лимит открытых позиций"},"cbplUsed":{"type":"number","description":"Позиции после последнего клиринга"},"cbplPlanned":{"type":"number","description":"Стоимостная оценка планируемых позиций после ближайшего клиринга"},"cbplUsedForOrders":{"type":"number","description":"Величина ГО, зарезервированного под клиентские заявки"},"cbplUsedForPositions":{"type":"number","description":"Величина ГО, зарезервированного под открытые клиентские позиции"},"optionsPremium":{"type":"number","description":"Премии по опционам"},"instrumentType":{"type":"string","description":"Тип инструмента"},"loadDate":{"type":"string","format":"date-time","description":"Дата и время обновления данных"},"varMargin":{"type":"number","description":"Оценка размера вариационной марки по позициям"},"realVarMargin":{"type":"number","description":"Начисленная в ходе клиринга вариационная маржа"}},"title":"FuturesLimitResponse"}}},"title":"LimitResponse"}}}},"429":{"description":"Too Many Requests","content":{"application/json":{"schema":{"type":"object","properties":{"timestamp":{"type":"integer","format":"int64"},"traceId":{"type":"string"},"type":{"type":"string","enum":["VALIDATION_ERROR","RESOURCE_EXHAUSTED","USER_BLOCKED","BAD_REQUEST","NOT_FOUND","UNAUTHORIZED","FORBIDDEN","CONFLICT","INTERNAL_SERVER_ERROR","SESSION_NOT_FOUND_ERROR","SESSION_EXPIRED_ERROR","SESSION_FAILED_ERROR"]},"errors":{"type":"array","items":{"type":"object","properties":{"type":{"type":"string"},"field":{"type":"string"},"payload":{"type":"object","additionalProperties":{"type":"object"}}},"title":"ErrorDetail"}},"displayOptions":{"type":"object","additionalProperties":{"type":"object"}}},"title":"ErrorResponse"}}}}}}
>
  
</StatusCodes>
