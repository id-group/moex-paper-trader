# Получить инструменты по ISIN

> Получить инструменты по ISIN

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
  children={"Получить инструменты по ISIN"}
>
</Heading>

<MethodEndpoint
  method={"post"}
  path={"/api/v1/instruments/by-isins"}
  context={"endpoint"}
>
  
</MethodEndpoint>

Получить инструменты по ISIN

**Определение наличия последующих страниц осуществляется клиентом:**
в случае если количество объектов в ответе меньше значения параметра `size`,
выборка считается завершённой;
при равенстве количества объектов значению `size` клиенту следует выполнить
запрос следующей страницы (`page + 1`).

<Heading
  id={"request"}
  as={"h2"}
  className={"openapi-tabs__heading"}
  children={"Request"}
>
</Heading>

<ParamsDetails
  parameters={[{"name":"size","in":"path","required":false,"schema":{"type":"number","format":"int64","default":50,"minimum":1,"maximum":100},"description":"Количество записей на странице"},{"name":"page","in":"path","required":false,"schema":{"type":"number","format":"int64","default":0},"description":"Номер страницы"}]}
>
  
</ParamsDetails>

<RequestSchema
  title={"Body"}
  body={{"content":{"application/json":{"schema":{"type":"object","properties":{"isins":{"type":"array","items":{"type":"string","minLength":1},"minItems":1,"uniqueItems":true,"example":["RU0009029540","RU0007661625","RU000A0J2Q06"],"description":"ISIN'ы инструментов"}},"title":"IsinsRequest"}}},"required":true}}
>
  
</RequestSchema>

<StatusCodes
  id={undefined}
  label={undefined}
  responses={{"200":{"description":"OK","content":{"application/json":{"schema":{"type":"array","items":{"type":"object","properties":{"ticker":{"type":"string","description":"Тикер инструмента"},"boards":{"type":"array","items":{"type":"object","properties":{"classCode":{"type":"string","description":"Код класса ценной бумаги","example":"TQBR"},"exchange":{"type":"string","description":"Код биржи","example":"MOEX"}},"title":"BoardAndExchange"},"uniqueItems":true},"shortName":{"type":"string","description":"Краткое наименование"},"displayName":{"type":"string","description":"Наименование для отображения"},"type":{"type":"string","description":"Тип инструмента:`CURRENCY` - Валюта`STOCK` - Акции РФ`FOREIGN_STOCK` - Иностранные акции`BONDS` - Облигации`NOTES` - Ноты`DEPOSITARY_RECEIPTS` - Депозитарные расписки`EURO_BONDS` - Европейские облигации`MUTUAL_FUNDS` - Фонды`ETF` - ETF`FUTURES` - Фьючерсы`OPTIONS` - Опционы`GOODS` - Товары`INDICES` - Индексы","enum":["CURRENCY","STOCK","FOREIGN_STOCK","BONDS","NOTES","DEPOSITARY_RECEIPTS","EURO_BONDS","MUTUAL_FUNDS","ETF","FUTURES","OPTIONS","GOODS","INDICES"]},"isin":{"type":"string","description":"ISIN инструмента"},"registrationCode":{"type":"string","description":"Код гос. регистрации"},"issuerName":{"type":"string","description":"Наименование эмитента"},"tradingCurrency":{"type":"string","description":"Валюта инструмента"},"faceValue":{"type":"number","format":"double","description":"Номинал"},"scale":{"type":"integer","format":"int32","description":"Точность"},"minimumStep":{"type":"number","format":"double","description":"Минимальный шаг цены"},"accruedInt":{"type":"number","format":"double","description":"НКД на дату расчетов"},"currencyStepPrice":{"type":"string","description":"Валюта шага цены"},"settleCode":{"type":"string","description":"Код расчетов по умолчанию"},"instrumentType":{"type":"string","enum":["CURRENCY","STOCK","FOREIGN_STOCK","BONDS","NOTES","DEPOSITARY_RECEIPTS","EURO_BONDS","MUTUAL_FUNDS","ETF","FUTURES","OPTIONS","GOODS","INDICES"],"description":"Тип инструмента:`CURRENCY` - Валюта`STOCK` - Акции РФ`FOREIGN_STOCK` - Иностранные акции`BONDS` - Облигации`NOTES` - Ноты`DEPOSITARY_RECEIPTS` - Депозитарные расписки`EURO_BONDS` - Европейские облигации`MUTUAL_FUNDS` - Фонды`ETF` - ETF`FUTURES` - Фьючерсы`OPTIONS` - Опционы`GOODS` - Товары`INDICES` - Индексы"},"settlementCurrency":{"type":"string","description":"Валюта инструмента"},"settlementDate":{"type":"string","format":"date-time"},"maturityDate":{"type":"date-time","description":"Дата погашения"},"lotSize":{"type":"number","format":"double","description":"Размер лота"},"promoIdx":{"type":"integer","format":"int32","deprecated":true},"isQualifiedOnly":{"type":"boolean","description":"Признак инструмента для квалов"},"isCanShort":{"type":"boolean","description":"Признак доступности шорта"},"baseAsset":{"type":"string","description":"Название базового актива для срочного контракта"},"qualifiedTestId":{"type":"integer","format":"int32","description":"Маркировка по тестам согласно действующей версии Базового стандарта на сегодня"},"qualifiedTestIdTm":{"type":"integer","format":"int32","description":"Маркировка по тестам согласно действующей версии Базового стандарта на завтра"},"availableForUnqualified":{"type":"boolean","description":"Разрешено для неквалифицированного инвестора"},"currencyNominal":{"type":"string","description":"Валюта номинала"},"stepPrice":{"type":"number","format":"double","description":"Стоимость шага цены"},"isBcsProduct":{"type":"boolean","description":"Признак продукта БКС"},"logoLink":{"type":"string","description":"Ссылка на лого","deprecated":true},"couponsPerYear":{"type":"integer","format":"int64","description":"Количество купонов в год"},"couponRate":{"type":"number","format":"double","description":"Ставка купона, процентов годовых"},"nextCoupon":{"type":"string","format":"date-time","description":"Дата выплаты купона"},"complexProduct":{"type":"number","format":"double","description":"Маркировка по тестам"},"baseAssetFuture":{"type":"string","description":"Тип базового актива для фьючерсов"},"subType":{"type":"string","description":"Наименование подтипа инструмента"},"percentTargetCurrent":{"type":"number","format":"double","description":"% роста до прогноза от цены закрытия предыдущей сессии"},"businessSector":{"type":"string","description":"Бизнес сектор эмитента"},"peNorm":{"type":"number","format":"double","description":"P/E Нормализованный, последний финансовый год"},"priceTangible":{"type":"number","format":"double","description":"Материальная балансовая стоимость на акцию"},"epsGrowthRate":{"type":"number","format":"double","description":"Темп роста EPS, 5 лет"},"predictedDps":{"type":"number","format":"double","description":"Прогнозируемый DPS"},"dividendYield":{"type":"number","format":"double","description":"Дивидендная доходность - указанный годовой дивиденд, деленный на цену закрытия"},"priceChangeYear":{"type":"number","format":"double","description":"Изменение цены в % за год"},"targetPrice":{"type":"number","format":"double","description":"Целевая цена"},"mktcap":{"type":"number","format":"double","description":"Рыночная капитализация"},"isBlocked":{"type":"boolean","description":"Признак блокированного класса"},"businessSectorId":{"type":"integer","format":"int32","description":"ID Бизнес сектора эмитента"},"primaryBoard":{"type":"string","description":"Первичный борд для инструмента"},"secondaryBoards":{"type":"array","items":{"type":"string"},"uniqueItems":true,"description":"Борды, отличные от первичного, но по ним осуществляется торговля"},"isCanMargin":{"type":"boolean","description":"Признак доступности для лонг"},"isReplacementBond":{"type":"boolean","description":"Признак замещающей облигации"},"subTitle":{"type":"string","description":"Подзаголовок элемента"},"couponTypeName":{"type":"string","description":"Тип купона на русском"},"emissionDate":{"type":"date-time","description":"Дата выпуска"},"excludeTypeFlags":{"type":"integer","format":"int32","deprecated":true},"creditRating":{"type":"string","description":"Уровень кредитного риска облигаций по шкале \"светофора\""},"liquidityRating":{"type":"string","description":"Рейтинг ликвидности облигаций"},"bcsScore":{"type":"integer","format":"int32","description":"Рейтинг БКС"},"bcsScoreColor":{"type":"string","description":"Цвет рейтинга"},"cfi":{"type":"string","deprecated":true},"nrdCode":{"type":"string","description":"Код инструмента в НРД"},"strike":{"type":"number","format":"double","description":"Страйк исполнения опциона"},"baseAssetSecuritySecCode":{"type":"string","description":"Тикер базового актива"},"baseAssetSecurityClassCode":{"type":"string","description":"Код класса базового актива"},"businessCountry":{"type":"string","description":"Cтрана ведения бизнеса"},"businessCountryCode":{"type":"string","description":"Код страны ведения бизнеса"},"priceChangeHalfYear":{"type":"number","format":"double","description":"Изменение цены в % за полгода"},"priceChangeMonth":{"type":"number","format":"double","description":"Изменение цены в % за месяц"},"priceChangeEarlyYear":{"type":"number","format":"double","description":"Изменение цены в % с начала года"},"excludeTypes":{"type":"array","items":{"type":"integer","format":"int32"},"uniqueItems":true,"deprecated":true},"displayNameSecond":{"type":"string","deprecated":true},"firstCurrCode":{"type":"string","description":"Код котируемой валюты в паре"},"amortisedMty":{"type":"boolean","description":"Признак наличия амортизационного погашения по выпуску"}},"title":"Instrument"}}}}},"429":{"description":"Too Many Requests","content":{"application/json":{"schema":{"type":"object","properties":{"timestamp":{"type":"integer","format":"int64"},"traceId":{"type":"string"},"type":{"type":"string","enum":["VALIDATION_ERROR","RESOURCE_EXHAUSTED","USER_BLOCKED","BAD_REQUEST","NOT_FOUND","UNAUTHORIZED","FORBIDDEN","CONFLICT","INTERNAL_SERVER_ERROR","SESSION_NOT_FOUND_ERROR","SESSION_EXPIRED_ERROR","SESSION_FAILED_ERROR"]},"errors":{"type":"array","items":{"type":"object","properties":{"type":{"type":"string"},"field":{"type":"string"},"payload":{"type":"object","additionalProperties":{"type":"object"}}},"title":"ErrorDetail"}},"displayOptions":{"type":"object","additionalProperties":{"type":"object"}}},"title":"ErrorResponse"}}}}}}
>
  
</StatusCodes>
