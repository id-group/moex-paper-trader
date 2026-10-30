# Получить текущие котировки

> Метод позволяет получить текущие котировки по инструментам

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
  children={"Получить текущие котировки"}
>
</Heading>

<MethodEndpoint
  method={"post"}
  path={"/api/v1/quotes"}
  context={"endpoint"}
>
  
</MethodEndpoint>

Метод позволяет получить текущие котировки по инструментам

<Heading
  id={"request"}
  as={"h2"}
  className={"openapi-tabs__heading"}
>
  <Translate id="theme.openapi.request.title">Request</Translate>
</Heading>

<ParamsDetails>
  
</ParamsDetails>

<RequestSchema
  {...require("./get-quotes.RequestSchema.json")}
>
  
</RequestSchema>

<StatusCodes
  {...require("./get-quotes.StatusCodes.json")}
>
  
</StatusCodes>
