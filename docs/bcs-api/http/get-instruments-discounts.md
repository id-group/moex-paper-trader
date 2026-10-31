# Получение дисконтов по инструментам

> Получение дисконтов по инструментам

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
  children={"Получение дисконтов по инструментам"}
>
</Heading>

<MethodEndpoint
  method={"get"}
  path={"/api/v1/instruments-discounts"}
  context={"endpoint"}
>
  
</MethodEndpoint>

Получение дисконтов по инструментам

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
  responses={{"200":{"description":"OK","content":{"application/json":{"schema":{"type":"array","items":{"type":"object","description":"Инструмент","properties":{"ticker":{"type":"string","description":"Тикер инструмента"},"discountLong":{"type":"number","description":"Ставка на лонг"},"discountShort":{"type":"number","description":"Ставка на шорт"}},"title":"InstrumentDto"}}}}},"429":{"description":"Too Many Requests","content":{"application/json":{"schema":{"type":"object","properties":{"timestamp":{"type":"integer","format":"int64"},"traceId":{"type":"string"},"type":{"type":"string","enum":["VALIDATION_ERROR","RESOURCE_EXHAUSTED","USER_BLOCKED","BAD_REQUEST","NOT_FOUND","UNAUTHORIZED","FORBIDDEN","CONFLICT","INTERNAL_SERVER_ERROR","SESSION_NOT_FOUND_ERROR","SESSION_EXPIRED_ERROR","SESSION_FAILED_ERROR"]},"errors":{"type":"array","items":{"type":"object","properties":{"type":{"type":"string"},"field":{"type":"string"},"payload":{"type":"object","additionalProperties":{"type":"object"}}},"title":"ErrorDetail"}},"displayOptions":{"type":"object","additionalProperties":{"type":"object"}}},"title":"ErrorResponse"}}}}}}
>
  
</StatusCodes>
