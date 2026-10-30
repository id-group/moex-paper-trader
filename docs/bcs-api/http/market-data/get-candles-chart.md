# Исторические свечи

> Метод предназначен для получения исторических данных по инструменту.

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
  children={"Исторические свечи"}
>
</Heading>

<MethodEndpoint
  method={"get"}
  path={"/api/v1/candles-chart"}
  context={"endpoint"}
>
  
</MethodEndpoint>

Метод предназначен для получения исторических данных по инструменту.

## Ограничение

Максимальное количество баров в одном запросе — **1440**

Если запрошенный временной интервал с учётом параметра `timeFrame` содержит **более 1440 свечей**, сервер вернёт ошибку.

### Зачем это ограничение
Ограничение введено для обеспечения стабильной работы сервиса и предсказуемого времени ответа.

### Как избежать ошибки
- Уменьшите временной интервал (`startDate` / `endDate`)
- Используйте более крупный `timeFrame` (например, `H1` вместо `M5`)
- Разбивайте большой период на несколько последовательных запросов

### Пример
Если вы запрашиваете свечи с таймфреймом `M1`, максимальный допустимый период — 1440 минут.\
Запрос большего интервала приведёт к ошибке.

## Последний бар может быть не закрыт

Метод возвращает свечи за запрошенный период на момент запроса. Если `endDate` попадает в текущий, ещё не завершённый интервал `timeFrame`, последний бар в массиве `bars` — это формирующаяся свеча, а не историческая. Она отражает сделки только за прошедшую часть интервала и будет меняться при повторных запросах, пока интервал не завершится.

### Пример
Запрос свечей `H1` в 12:00:03 в период торгов. Свеча 11:00–12:00 уже закрыта. Интервал 12:00–13:00 вернётся как открытая свеча, построенная по сделкам за первые секунды (12:00:00–12:00:03), и не совпадёт с итоговой свечой за этот час.

### Как получить закрытую свечу
- Запрашивайте период с `endDate`, не позже начала текущего незавершённого интервала (для `H1` — не позже начала текущего часа).
- Либо проверяйте последний бар: если время его начала плюс длительность `timeFrame` больше текущего времени, считайте свечу неокончательной и не используйте её как историческую.
- Повторный запрос после завершения интервала вернёт свечу в итоговом виде.

<Heading
  id={"request"}
  as={"h2"}
  className={"openapi-tabs__heading"}
>
  <Translate id="theme.openapi.request.title">Request</Translate>
</Heading>

<ParamsDetails
  {...require("./get-candles-chart.ParamsDetails.json")}
>
  
</ParamsDetails>

<RequestSchema
  {...require("./get-candles-chart.RequestSchema.json")}
>
  
</RequestSchema>

<StatusCodes
  {...require("./get-candles-chart.StatusCodes.json")}
>
  
</StatusCodes>
