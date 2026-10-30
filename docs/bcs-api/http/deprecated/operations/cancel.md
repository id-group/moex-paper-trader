# Отменить заявку

> Запрос по данному методу позволяет отменить выставленные заявки

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
  children={"Отменить заявку"}
>
</Heading>

<MethodEndpoint
  method={"post"}
  path={"/api/v1/orders/{originalClientOrderId}/cancel"}
  context={"endpoint"}
>
  
</MethodEndpoint>

Запрос по данному методу позволяет отменить выставленные заявки

:::caution[устаревший метод]

Данный метод устарел, используйте новый метод для [отмены заявки](/http/operations/cancel-order)Метод будет удален 31.01.2027

:::

<Heading
  id={"request"}
  as={"h2"}
  className={"openapi-tabs__heading"}
>
  <Translate id="theme.openapi.request.title">Request</Translate>
</Heading>

<ParamsDetails
  {...require("./cancel.ParamsDetails.json")}
>
  
</ParamsDetails>

<RequestSchema
  {...require("./cancel.RequestSchema.json")}
>
  
</RequestSchema>

<StatusCodes
  {...require("./cancel.StatusCodes.json")}
>
  
</StatusCodes>
