# Изменение заявки

> Запрос по данному методу позволяет изменить выставленные заявки

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
  children={"Изменение заявки"}
>
</Heading>

<MethodEndpoint
  method={"post"}
  path={"/api/v1/orders/{originalClientOrderId}"}
  context={"endpoint"}
>
  
</MethodEndpoint>

Запрос по данному методу позволяет изменить выставленные заявки

:::caution[устаревший метод]

Данный метод устарел, используйте новый метод для [изменения заявки](/http/operations/update-order)Метод будет удален 31.01.2027

:::

<Heading
  id={"request"}
  as={"h2"}
  className={"openapi-tabs__heading"}
>
  <Translate id="theme.openapi.request.title">Request</Translate>
</Heading>

<ParamsDetails
  {...require("./update.ParamsDetails.json")}
>
  
</ParamsDetails>

<RequestSchema
  {...require("./update.RequestSchema.json")}
>
  
</RequestSchema>

<StatusCodes
  {...require("./update.StatusCodes.json")}
>
  
</StatusCodes>
