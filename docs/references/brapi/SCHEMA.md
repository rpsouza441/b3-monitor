# Esquema observado da Brapi

Fontes: documentação lida em 5 de outubro de 2026 e os corpos em `analysis/brapi/evidence/`. Valores abaixo são os que voltaram nesta auditoria. Onde a documentação e a resposta divergem, as duas ficam registradas.

Documentação usada:

- https://brapi.dev/docs
- https://brapi.dev/docs/acoes
- https://brapi.dev/docs/acoes/cotacao
- https://brapi.dev/docs/acoes/historico
- https://brapi.dev/docs/acoes/migracao-v2
- https://brapi.dev/docs/authentication
- https://brapi.dev/docs/fiis
- https://brapi.dev/docs/fiis/historico
- https://brapi.dev/docs/fundos
- https://brapi.dev/docs/tickers
- https://brapi.dev/faq/quais-as-limitacoes

Autenticação que funcionou: header `Authorization: Bearer`. A documentação também aceita `?token=` para planilhas e desaconselha esse modo. Este teste não usou query string.

Base: `https://brapi.dev/api`.

## Envelope de cotação v2

`GET /api/v2/stocks/quote?symbols={umTicker}`

```json
{
  "results": [
    {
      "requestedSymbol": "WEGE3",
      "symbol": "WEGE3",
      "changed": false,
      "data": {}
    }
  ],
  "requestedAt": "2026-10-05T17:18:58.748Z",
  "took": 0
}
```

`requestedSymbol` é o ticker enviado. `symbol` é o ticker dos dados. `changed=true` indicaria remapeamento; nos 23 veio `false`. `requestedAt` é o relógio do servidor em UTC. `took` é tempo interno da brapi em milissegundos, não a latência vista pelo cliente.

### `results[].data` na cotação

Presente nos 23, ações, units, FIIs e SNAG11:

| Campo | Papel observado |
| --- | --- |
| `shortName` | Veio igual ao ticker |
| `longName` | Nome descritivo |
| `currency` | `BRL` nos 23 |
| `regularMarketPrice` | Último preço numérico |
| `regularMarketTime` | ISO-8601 UTC com milissegundos, grade de 30 s nesta sessão (`*.000Z`) |
| `regularMarketPreviousClose` | Número; insumo seguro para variação calculada no cliente |
| `regularMarketOpen` | Abertura do dia |
| `regularMarketDayHigh` | Máxima do dia |
| `regularMarketDayLow` | Mínima do dia |
| `regularMarketDayRange` | String `mín - máx` |
| `regularMarketVolume` | Volume acumulado do dia, inteiro |
| `regularMarketChange` | Número presente, porém não igual a preço menos fechamento anterior em 22 dos 23 |
| `regularMarketChangePercent` | Número presente; em geral perto de `change / previousClose * 100`, mas não é a variação do preço contra o fechamento |
| `fiftyTwoWeekLow` | Número |
| `fiftyTwoWeekHigh` | Número |
| `fiftyTwoWeekRange` | String |
| `logourl` | URL `https://icons.brapi.dev/icons/{ticker}.svg` |
| `marketCap` | Número nas seis ações. `null` em BPAC11, TAEE11, SAPR11, nos 13 FIIs e em SNAG11 |

Não veio em nenhum dos 23 na v2: `priceEarnings`, `earningsPerShare`, tipo do instrumento, horário de negócio separado de `regularMarketTime`.

O legado `GET /api/quote/ITUB4` devolve os mesmos campos de preço no próprio item de `results[]` e, nesse ticker de sandbox, também `priceEarnings` e `earningsPerShare`. Sem `requestedSymbol`, `changed` e sem objeto `data`.

## Tipo do instrumento

A cotação não traz `assetType`. Isso veio de `GET /api/v2/tickers`.

| Ticker | `assetType` | `subType` | `isActive` |
| --- | --- | --- | --- |
| WEGE3, ITUB4, EGIE3, SBSP3, TIMS3, CMIG4 | stock | stock | true |
| BPAC11, TAEE11, SAPR11 | stock | unit | true |
| HGLG11, VRTA11, BTLG11, RBVA11, HGBS11, GARE11, ALZR11, MXRF11, BTHF11, GGRC11, XPLG11, KNCR11, KNHY11 | fund | fii | true |
| SNAG11 | fund | fi-agro | true |

`sector` veio `null` em BTHF11 e SNAG11 e preenchido nos demais. A lista pagina com `pagination.page`, `limit`, `totalItems`, `totalPages`, `hasNextPage`. Os filtros usados couberam numa página (`limit=2000`).

## Histórico v2 de ações, FII e FIAGRO pela rota de ações

`GET /api/v2/stocks/historical?symbols={umTicker}&range=3mo&interval=1d&sortOrder=asc`

Mesmo envelope `results[]` / `requestedAt` / `took`. Dentro de `data`:

| Campo | Observado no gratuito (HGLG11 e SNAG11, 3 meses) |
| --- | --- |
| `usedRange` | `3mo` |
| `usedInterval` | `1d` |
| `historicalDataPrice[]` | 64 objetos |

Cada ponto:

| Campo | Tipo observado | Notas |
| --- | --- | --- |
| `date` | inteiro Unix em segundos | Sempre `03:00:00Z` no diário, isto é 00:00 em `America/Sao_Paulo`. Guardar o inteiro e a data civil BRT |
| `open`, `high`, `low`, `close` | número | Nenhum nulo nas séries de 3 meses |
| `volume` | número | Nenhum nulo |
| `adjustedClose` | número | Nenhum nulo no diário. Diferente de `close` na maior parte da janela. Documentação: usar este campo para retorno, porque inclui proventos e eventos societários |

`sortOrder=asc` produziu datas estritamente crescentes, sem duplicata. O default documentado é `desc`. A chamada de ITUB4 com `includeRaw` e sem `sortOrder` veio decrescente.

O ponto cuja data BRT é o dia corrente é um candle ainda aberto. Preço e volume mudaram entre a cotação e o histórico, com segundos de diferença, em HGLG11.

### O que o plano recusou

Corpo real de `INVALID_RANGE` (WEGE3, `range=1y`):

- `error`: true
- `code`: `INVALID_RANGE`
- `details.currentPlan`: `free`
- `details.limit.current`: `1d`, `5d`, `1mo`, `3mo`
- `details.requiredPlan`: `startup`

Corpo real de `INVALID_INTERVAL` (HGLG11 e SNAG11, `interval=15m`):

- `code`: `INVALID_INTERVAL`
- `details.currentPlan`: `free`
- `details.limit.current`: somente `1d`

A documentação publica uma enum maior (`2d`, `7d`, `6mo`, `1y`, `2y`, `5y`, `10y`, `ytd`, `max`, e intervalos de `1m` a `3mo`). O plano gratuito não aceita essa enum inteira.

No sandbox de ITUB4, `interval=15m` devolveu pontos com `adjustedClose: null` e timestamps no meio do pregão (o último em 14:15 BRT). Esse formato não está disponível na cota medida.

`includeRaw=true` está documentado como Pro. No sandbox de ITUB4 as chaves `rawOpen`, `rawHigh`, `rawLow` e `rawClose` apareceram; no ponto mais recente `rawClose` era `null`. Sem teste na cota de 15.000.

## Histórico dedicado de FII

`GET /api/v2/fii/historical?symbols=HGLG11` respondeu 200 só no limitador de sandbox (20 requisições / 60 s), não na cota de 15.000. O formato é outro:

```json
{
  "fiis": [
    {
      "symbol": "HGLG11",
      "historicalDataPrice": [
        {
          "date": 1790910000,
          "open": 147.11,
          "high": 148.2,
          "low": 146.6,
          "close": 147.9,
          "volume": 110708,
          "adjustedClose": 147.9
        }
      ]
    }
  ],
  "requestedAt": "...",
  "took": 0
}
```

Não há `results[]`, `usedRange` nem `usedInterval`. A série dessa chamada passou de 3 meses (251 pontos). Documentação: plano mínimo Pro; sem token, só MXRF11 e HGLG11. Não tratar esse 200 como liberação para VRTA11 e o restante da carteira.

Endpoints em `/api/v2/fii/` (indicadores, dividendos, imóveis, carteira) e os de `/docs/fundos` estão documentados como Pro e não foram chamados.

## Erros vistos

| HTTP | `code` | Quando | Debitou a cota de 15.000 nesta sessão? |
| --- | --- | --- | --- |
| 400 | `QUOTES_PER_REQUEST_EXCEEDED` | Dois tickers na mesma cotação | Sim |
| 400 | `INVALID_RANGE` | `range=1y` em WEGE3 | Não |
| 400 | `INVALID_INTERVAL` | `interval=15m` em HGLG11 e SNAG11 | Não |

Não apareceram 401, 403, 404, 429, 500 nem 503. A documentação reserva 401 para token ausente ou inválido, 403 para recurso fora do plano, 429 para cota ou concorrência, com `Retry-After`. Parte das recusas de plano desta conta veio como 400 com `currentPlan=free`, não como 403.

## Headers de limite

Nas chamadas debitadas na conta:

| Header | Valor nesta conta |
| --- | --- |
| `ratelimit-limit`, `x-ratelimit-limit` | `15000` |
| `ratelimit-remaining`, `x-ratelimit-remaining` | desce 1 a cada débito |
| `ratelimit-reset` | segundos até o fim do ciclo |
| `x-ratelimit-window` | `billing-cycle` |
| `x-brapi-concurrency-limit` | `1` |
| `x-plan-data-freshness` | `30s` |
| `x-brapi-stale` | `1` só em alguns ativos (VRTA11, KNHY11 e um histórico sandbox de ITUB4). Ausente nos outros |
| `x-brapi-queue-wait-ms` | `0` |
| `x-request-id` | id da chamada |

Nas chamadas de sandbox (ITUB4 na rota de ações, HGLG11 na rota `/api/v2/fii/historical`, lista `/api/v2/tickers`): `ratelimit-limit=20`, `ratelimit-remaining=20`, `ratelimit-reset=60`. A lista pública não trouxe `x-brapi-concurrency-limit` nem `x-plan-data-freshness`.

`RateLimit-Reset` da documentação é este delta em segundos. Não é um timestamp Unix. Não houve `Retry-After`.

## Semântica de tempo para o Java

| Campo | Forma crua | Forma normalizada usada nesta auditoria |
| --- | --- | --- |
| `regularMarketTime` | string ISO-8601 com `Z` | `Instant` UTC. Exibir também em `America/Sao_Paulo` |
| `requestedAt` | string ISO-8601 | `Instant` UTC do servidor |
| `historicalDataPrice[].date` | Unix segundos | `Instant` UTC e `LocalDate` em `America/Sao_Paulo` (o instante é meia-noite local) |
| Idade | não vem pronta | `requestedAt - regularMarketTime`, e em paralelo o relógio do cliente menos `regularMarketTime` |

Não usar o horário em que o HTTP chegou como se fosse o horário do negócio. Não usar `date` diário como horário intradiário. O Brasil está em UTC−3 o ano inteiro desde 2019; ainda assim, converter com `America/Sao_Paulo`, não com um offset fixo espalhado no código.
