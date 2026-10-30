# Получить обезличенные сделки

> Метод предназначен для получения истории обезличенные сделок за текущую торговую сессию

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
  children={"Получить обезличенные сделки"}
>
</Heading>

<MethodEndpoint
  method={"post"}
  path={"/api/v1/last-trades"}
  context={"endpoint"}
>
  
</MethodEndpoint>

Метод предназначен для получения истории обезличенные сделок за текущую торговую сессию

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
  {...require("./get-last-trades.RequestSchema.json")}
>
  
</RequestSchema>

<StatusCodes
  {...require("./get-last-trades.StatusCodes.json")}
>
  
</StatusCodes>
