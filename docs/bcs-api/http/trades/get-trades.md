# Получение списка всех сделок

> Метод возвращает список собственных сделок клиента.

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
  children={"Получение списка всех сделок"}
>
</Heading>

<MethodEndpoint
  method={"post"}
  path={"/api/v1/trades/search"}
  context={"endpoint"}
>
  
</MethodEndpoint>

Метод возвращает список собственных сделок клиента.

:::warning[Внимание]
В списке отображаются сделки, совершённые начиная с **26.01.2026**. Более ранние сделки в список не включены.

Список содержит не более **1000** последних сделок.
:::

<Heading
  id={"request"}
  as={"h2"}
  className={"openapi-tabs__heading"}
>
  <Translate id="theme.openapi.request.title">Request</Translate>
</Heading>

<ParamsDetails
  {...require("./get-trades.ParamsDetails.json")}
>
  
</ParamsDetails>

<RequestSchema
  {...require("./get-trades.RequestSchema.json")}
>
  
</RequestSchema>

<StatusCodes
  {...require("./get-trades.StatusCodes.json")}
>
  
</StatusCodes>
