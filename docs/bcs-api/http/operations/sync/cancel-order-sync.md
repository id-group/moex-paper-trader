# Отменить заявку

> Синхронный метод дожидается результата обработки заявки и возвращает её актуальное состояние в формате ответа [получения статуса заявки](/http/operations/get-order-by-id). Асинхронный метод возвращает только подтверждение приёма запроса.

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
  path={"/api/v1/orders/cancel/sync"}
  context={"endpoint"}
>
  
</MethodEndpoint>

Синхронный метод дожидается результата обработки заявки и возвращает её актуальное состояние в формате ответа [получения статуса заявки](/http/operations/get-order-by-id). Асинхронный метод возвращает только подтверждение приёма запроса.

Метод позволяет отменить собственную заявку по идентификатору. Поддерживаются заявки, поданные через Trade API и МИ, со статусами `0` (Новая), `1` (Частично исполнена), `5` (Заменена) и `10` (Ожидает подтверждения новой заявки). Для заявок через МИ данные заявки предварительно получаются по биржевому идентификатору.

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
  {...require("./cancel-order-sync.RequestSchema.json")}
>
  
</RequestSchema>

<StatusCodes
  {...require("./cancel-order-sync.StatusCodes.json")}
>
  
</StatusCodes>
