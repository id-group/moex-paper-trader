# Получить список заявок

> Метод возвращает список собственных заявок клиента.

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
  children={"Получить список заявок"}
>
</Heading>

<MethodEndpoint
  method={"post"}
  path={"/api/v1/orders/search"}
  context={"endpoint"}
>
  
</MethodEndpoint>

Метод возвращает список собственных заявок клиента.

:::warning[Внимание]
В списке отображаются заявки, поданные начиная с **26.01.2026**. Более ранние заявки в список не включены.

Список содержит не более **1000** последних заявок.
:::

<Heading
  id={"request"}
  as={"h2"}
  className={"openapi-tabs__heading"}
>
  <Translate id="theme.openapi.request.title">Request</Translate>
</Heading>

<ParamsDetails
  {...require("./all-order-list.ParamsDetails.json")}
>
  
</ParamsDetails>

<RequestSchema
  {...require("./all-order-list.RequestSchema.json")}
>
  
</RequestSchema>

<StatusCodes
  {...require("./all-order-list.StatusCodes.json")}
>
  
</StatusCodes>
