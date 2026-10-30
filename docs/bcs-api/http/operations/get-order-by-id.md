# Получить статус заявки

> Метод позволяет получить актуальный статус собственной заявки любого поддерживаемого типа по идентификатору. При отсутствии заявки возвращается ошибка 404 NOT_FOUND.

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
  children={"Получить статус заявки"}
>
</Heading>

<MethodEndpoint
  method={"get"}
  path={"/api/v1/orders"}
  context={"endpoint"}
>
  
</MethodEndpoint>

Метод позволяет получить актуальный статус собственной заявки любого поддерживаемого типа по идентификатору. При отсутствии заявки возвращается ошибка 404 NOT_FOUND.

<Heading
  id={"request"}
  as={"h2"}
  className={"openapi-tabs__heading"}
>
  <Translate id="theme.openapi.request.title">Request</Translate>
</Heading>

<ParamsDetails
  {...require("./get-order-by-id.ParamsDetails.json")}
>
  
</ParamsDetails>

<RequestSchema
  {...require("./get-order-by-id.RequestSchema.json")}
>
  
</RequestSchema>

<StatusCodes
  {...require("./get-order-by-id.StatusCodes.json")}
>
  
</StatusCodes>
