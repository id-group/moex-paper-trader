# Получить расписание на день

> Получить расписание на день

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
  children={"Получить расписание на день"}
>
</Heading>

<MethodEndpoint
  method={"get"}
  path={"/api/v1/trading-schedule/daily-schedule"}
  context={"endpoint"}
>
  
</MethodEndpoint>

Получить расписание на день

<Heading
  id={"request"}
  as={"h2"}
  className={"openapi-tabs__heading"}
  children={"Request"}
>
</Heading>

<ParamsDetails
  parameters={[{"name":"classCode","in":"query","required":true,"schema":{"type":"string","minLength":1},"example":"TQBR","description":"Код класса ценной бумаги"},{"name":"ticker","in":"query","required":true,"schema":{"type":"string","minLength":1},"example":"SBER","description":"Тикер инструмента"}]}
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
  responses={{"200":{"description":"OK","content":{"application/json":{"schema":{"type":"object","properties":{"isWorkDay":{"type":"boolean","description":"Признак рабочего дня"},"dailySchedule":{"type":"array","items":{"type":"object","properties":{"startDate":{"type":"string","example":"14:30:00","description":"Время начала"},"endDate":{"type":"string","example":"14:30:00","description":"Время окончания"},"tradingSessionType":{"type":"string","description":"Название текущей сессии","enum":["Технический перерыв QUIK","Аукцион открытия","Не рабочее время/Выходной/Праздник","Вечерняя торговая сессия","Торговый период","Аукцион закрытия"]},"tradingSessionStatus":{"type":"string","enum":["OPEN","CLOSE"],"description":"Статус сессии"}},"title":"Schedule"},"description":"Полное дневное расписание"}},"title":"DailySchedule"}}}},"429":{"description":"Too Many Requests","content":{"application/json":{"schema":{"type":"object","properties":{"timestamp":{"type":"integer","format":"int64"},"traceId":{"type":"string"},"type":{"type":"string","enum":["VALIDATION_ERROR","RESOURCE_EXHAUSTED","USER_BLOCKED","BAD_REQUEST","NOT_FOUND","UNAUTHORIZED","FORBIDDEN","CONFLICT","INTERNAL_SERVER_ERROR","SESSION_NOT_FOUND_ERROR","SESSION_EXPIRED_ERROR","SESSION_FAILED_ERROR"]},"errors":{"type":"array","items":{"type":"object","properties":{"type":{"type":"string"},"field":{"type":"string"},"payload":{"type":"object","additionalProperties":{"type":"object"}}},"title":"ErrorDetail"}},"displayOptions":{"type":"object","additionalProperties":{"type":"object"}}},"title":"ErrorResponse"}}}}}}
>
  
</StatusCodes>
