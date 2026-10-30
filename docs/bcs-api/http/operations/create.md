# Создать заявку

> Работает только при наличии прав для торговли. [Подробнее о правах доступа](/#права-доступа)

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
  children={"Создать заявку"}
>
</Heading>

<MethodEndpoint
  method={"post"}
  path={"/api/v1/orders"}
  context={"endpoint"}
>
  
</MethodEndpoint>

Работает только при наличии прав для торговли. [Подробнее о правах доступа](/#права-доступа)

:::info[О торговле вне биржи]
Если вы планируете совершать сделки с иностранными акциями, то ознакомьтесь с [условиями торговли вне биржи](/otc/general) перед выставлением заявок\
[Торговля вне биржи (БКС)](/otc/bcs) с классом **QMEBLCK**\
[Торговля вне биржи (МосБиржа)](/otc/moex) с классом **MTQR**
:::

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
  {...require("./create.RequestSchema.json")}
>
  
</RequestSchema>

<StatusCodes
  {...require("./create.StatusCodes.json")}
>
  
</StatusCodes>
