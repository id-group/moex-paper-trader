# Получить историю неторговых операций

> Получить историю неторговых операций

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
  children={"Получить историю неторговых операций"}
>
</Heading>

<MethodEndpoint
  method={"post"}
  path={"/api/v1/operations/search"}
  context={"endpoint"}
>
  
</MethodEndpoint>

Получить историю неторговых операций

<Heading
  id={"request"}
  as={"h2"}
  className={"openapi-tabs__heading"}
>
  <Translate id="theme.openapi.request.title">Request</Translate>
</Heading>

<ParamsDetails
  {...require("./get-operation-history.ParamsDetails.json")}
>
  
</ParamsDetails>

<RequestSchema
  {...require("./get-operation-history.RequestSchema.json")}
>
  
</RequestSchema>

<StatusCodes
  {...require("./get-operation-history.StatusCodes.json")}
>
  
</StatusCodes>
