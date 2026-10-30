# Авторизация

> Сервис предназначен для предоставления клиенту access-токена. Перед использованием необходимо получить refresh-токен в веб-версии БКС Мир инвестиций. [Инструкция по получению](/#с-чего-начать)

import MethodEndpoint from "@theme/ApiExplorer/MethodEndpoint";
import ParamsDetails from "@theme/ParamsDetails";
import RequestSchema from "@theme/RequestSchema";
import StatusCodes from "@theme/StatusCodes";
import OperationTabs from "@theme/OperationTabs";
import TabItem from "@theme/TabItem";
import Heading from "@theme/Heading";

<Heading
  as={"h1"}
  className={"openapi__heading"}
  children={"Авторизация"}
>
</Heading>

<MethodEndpoint
  method={"post"}
  path={"/trade-api-keycloak/realms/tradeapi/protocol/openid-connect/token"}
  context={"endpoint"}
>
  
</MethodEndpoint>

Сервис предназначен для предоставления клиенту access-токена. Перед использованием необходимо получить refresh-токен в веб-версии БКС Мир инвестиций. [Инструкция по получению](/#с-чего-начать)

<Heading
  id={"request"}
  as={"h2"}
  className={"openapi-tabs__heading"}
  children={"Request"}
>
</Heading>

<ParamsDetails
  parameters={undefined}
>
  
</ParamsDetails>

<RequestSchema
  title={"Body"}
  body={{"required":true,"content":{"application/x-www-form-urlencoded":{"schema":{"type":"object","properties":{"client_id":{"type":"string","enum":["trade-api-read","trade-api-write"],"description":"Идентификатор типа токена`trade-api-read` — токен для чтения`trade-api-write` – токен для торговли"},"refresh_token":{"type":"string","description":"Refresh-токен, полученный в веб-версии."},"grant_type":{"type":"string","enum":["refresh_token"],"description":"Тип grant. Для обновления токена — `refresh_token`."}},"required":["client_id","refresh_token","grant_type"]}}}}}
>
  
</RequestSchema>

<StatusCodes
  id={undefined}
  label={undefined}
  responses={{"200":{"description":"OK","content":{"application/json":{"schema":{"type":"object","properties":{"access_token":{"type":"string","description":"Токен с запрошенными правами"},"expires_in":{"type":"number","description":"Время жизни токена в секундах (всего 24 часа)"},"refresh_expires_in":{"type":"number","description":"Время жизни refresh-токена (всего 90 суток)"},"refresh_token":{"type":"string","description":"Параметр обновления токена. Используется для обновления пары токенов access/refresh"},"token_type":{"type":"string","description":"Тип токена, определяющий способ его использования. Всегда принимает значение «bearer»"},"not-before-policy":{"type":"string","description":"Дата, с которой начнут действовать токены системы (по UNIX-времени). По умолчанию 0 (т.е. сразу)"},"session_state":{"type":"string","description":"GUID сессии"},"scope":{"type":"string","description":"Права доступа согласно переданному в запросе токену"}}}}}},"400":{"description":"Invalid grant","content":{"application/json":{"schema":{"type":"object","properties":{"error":{"type":"string","description":"Тип ошибки","enum":["invalid_grant"]},"error_description":{"type":"string","description":"Описание ошибки"}}}}}}}}
>
  
</StatusCodes>
