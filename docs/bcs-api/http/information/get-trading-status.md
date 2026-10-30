# Получить статусы торговли

> Получить статусы торговли

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
  children={"Получить статусы торговли"}
>
</Heading>

<MethodEndpoint
  method={"get"}
  path={"/api/v1/trading-schedule/status"}
  context={"endpoint"}
>
  
</MethodEndpoint>

Получить статусы торговли

<Heading
  id={"request"}
  as={"h2"}
  className={"openapi-tabs__heading"}
  children={"Request"}
>
</Heading>

<ParamsDetails
  parameters={[{"name":"classCode","in":"query","required":true,"schema":{"type":"string","minLength":1},"example":"TQBR","description":"Код класса ценной бумаги"}]}
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
  responses={{"200":{"description":"OK","content":{"application/json":{"schema":{"type":"object","properties":{"tradingSessionTypeId":{"type":"integer","format":"int32","description":"ID сессии"},"tradingSessionType":{"type":"string","description":"Название текущей сессии"},"tradingSessionStatus":{"type":"string","enum":["OPEN","CLOSE"],"description":"Статус сессии"},"nextSessionDate":{"type":"string","format":"date-time","description":"Дата и время следующего изменения статуса"}},"title":"TradingStatusResponse"}}}},"429":{"description":"Too Many Requests","content":{"application/json":{"schema":{"type":"object","properties":{"timestamp":{"type":"integer","format":"int64"},"traceId":{"type":"string"},"type":{"type":"string","enum":["VALIDATION_ERROR","RESOURCE_EXHAUSTED","USER_BLOCKED","BAD_REQUEST","NOT_FOUND","UNAUTHORIZED","FORBIDDEN","CONFLICT","INTERNAL_SERVER_ERROR","SESSION_NOT_FOUND_ERROR","SESSION_EXPIRED_ERROR","SESSION_FAILED_ERROR"]},"errors":{"type":"array","items":{"type":"object","properties":{"type":{"type":"string"},"field":{"type":"string"},"payload":{"type":"object","additionalProperties":{"type":"object"}}},"title":"ErrorDetail"}},"displayOptions":{"type":"object","additionalProperties":{"type":"object"}}},"title":"ErrorResponse"}}}}}}
>
  
</StatusCodes>
