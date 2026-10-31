# Изменить заявку

> Метод позволяет изменить параметры собственной заявки в допустимом статусе. Изменение выполняется через отмену исходной заявки и создание новой заявки с указанными параметрами. Заявки со статусом `0` (Новая), `1` (Частично исполнена), `5` (Заменена) или `10` (Ожидает подтверждения новой заявки) могут быть изменены.

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
  children={"Изменить заявку"}
>
</Heading>

<MethodEndpoint
  method={"post"}
  path={"/api/v1/orders/edit"}
  context={"endpoint"}
>
  
</MethodEndpoint>

Метод позволяет изменить параметры собственной заявки в допустимом статусе. Изменение выполняется через отмену исходной заявки и создание новой заявки с указанными параметрами. Заявки со статусом `0` (Новая), `1` (Частично исполнена), `5` (Заменена) или `10` (Ожидает подтверждения новой заявки) могут быть изменены.

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
  {...require("./update-order.RequestSchema.json")}
>
  
</RequestSchema>

<StatusCodes
  {...require("./update-order.StatusCodes.json")}
>
  
</StatusCodes>
