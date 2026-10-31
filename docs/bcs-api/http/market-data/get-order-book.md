# Получить данные по стакану

> Метод позволяет получить текущий стакан по инструменту

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
  children={"Получить данные по стакану"}
>
</Heading>

<MethodEndpoint
  method={"get"}
  path={"/api/v1/order-book"}
  context={"endpoint"}
>
  
</MethodEndpoint>

Метод позволяет получить текущий стакан по инструменту

> **Примечание:** `bidVolume` и `askVolume` зависят от значения `depth`, указанного в запросе: они считаются как сумма `quantity` по уровням `bids`/`asks`, фактически попавшим в ответ (не более `depth` уровней с каждой стороны), а не по всему стакану целиком. При изменении `depth` значения `bidVolume`/`askVolume` изменятся соответственно.

<Heading
  id={"request"}
  as={"h2"}
  className={"openapi-tabs__heading"}
>
  <Translate id="theme.openapi.request.title">Request</Translate>
</Heading>

<ParamsDetails
  {...require("./get-order-book.ParamsDetails.json")}
>
  
</ParamsDetails>

<RequestSchema
  {...require("./get-order-book.RequestSchema.json")}
>
  
</RequestSchema>

<StatusCodes
  {...require("./get-order-book.StatusCodes.json")}
>
  
</StatusCodes>
