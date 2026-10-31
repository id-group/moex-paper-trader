# Получение статуса заявки

> Получить статус созданной вами заявки

import MethodEndpoint from "@theme/ApiExplorer/MethodEndpoint";
import ParamsDetails from "@theme/ParamsDetails";
import RequestSchema from "@theme/RequestSchema";
import StatusCodes from "@theme/StatusCodes";
import OperationTabs from "@theme/OperationTabs";
import TabItem from "@theme/TabItem";
import Heading from "@theme/Heading";
import Translate from "@docusaurus/Translate";

<Heading
  as={"h1"}
  className={"openapi__heading"}
  children={"Получение статуса заявки"}
>
</Heading>

<MethodEndpoint
  method={"get"}
  path={"/api/v1/orders/{originalClientOrderId}"}
  context={"endpoint"}
>
  
</MethodEndpoint>

Получить статус созданной вами заявки

:::caution[устаревший метод]

Данный метод устарел, используйте новый метод для [получения статуса заявки](/http/operations/get-order-by-id)Метод будет удален 31.01.2027

:::

<Heading
  id={"request"}
  as={"h2"}
  className={"openapi-tabs__heading"}
>
  <Translate id="theme.openapi.request.title">Request</Translate>
</Heading>

<ParamsDetails
  {...require("./status.ParamsDetails.json")}
>
  
</ParamsDetails>

<RequestSchema
  {...require("./status.RequestSchema.json")}
>
  
</RequestSchema>

<StatusCodes
  {...require("./status.StatusCodes.json")}
>
  
</StatusCodes>
