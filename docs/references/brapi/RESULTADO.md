# Auditoria Brapi Free — cobertura e frescor

**Veredito: PARCIALMENTE VIÁVEL.**

Em 5 de outubro de 2026, com o pregão da B3 aberto, a conta gratuita devolveu cotação válida para os 23 ativos. O atraso observado ficou entre 27 e 209 segundos, e a cota comporta a carteira de 23 nomes consultada a cada 30 minutos. Indicadores diários (SMA20, SMA50, RSI14, EMA9, EMA21 e volume contra a média) cabem na janela de 3 meses. Candles intradiários não cabem no plano gratuito. Os campos prontos de variação não batem com o preço e o fechamento anterior.

Foram executadas **40 requisições HTTP**. Dessas, **25 debitaram** a cota de 15.000. Ao final, `ratelimit-remaining` estava em **14.975**. Nenhuma credencial foi gravada.

A matriz dos 23 tickers está em `analysis/brapi/COBERTURA.csv`. O esquema está em `SCHEMA.md`. A conta de consumo está em `CONSUMO.md`. Os corpos sanitizados estão em `analysis/brapi/evidence/`.

## O que esta conta é

A primeira resposta medida pela cota da conta, `GET /api/v2/stocks/quote?symbols=ITUB4,WEGE3`, voltou HTTP 400 com `code=QUOTES_PER_REQUEST_EXCEEDED` e a mensagem de que o plano permite no máximo 1 ativo por requisição. Os headers dessa chamada:

| Header | Valor observado |
| --- | --- |
| `ratelimit-limit` e `x-ratelimit-limit` | 15000 |
| `ratelimit-remaining` | 14999 (a conta estava cheia antes desta auditoria) |
| `ratelimit-reset` | 2519336 segundos |
| `x-ratelimit-window` | `billing-cycle` |
| `x-brapi-concurrency-limit` | 1 |
| `x-plan-data-freshness` | `30s` |
| `Date` | Mon, 05 Oct 2026 17:18:58 GMT |

`ratelimit-reset` é um delta em segundos, não uma data absoluta. Na chamada das 17:18:58 GMT, 2.519.336 segundos caem em **3 de novembro de 2026, 18:07:54 BRT** (21:07:54 UTC). Uma chamada posterior, às 17:21:37 GMT, trouxe 2.519.177 segundos: a diferença é de 159 segundos, igual ao intervalo entre os headers `Date`. O ciclo observado termina nessa data. O dia de início do ciclo não veio em header nenhum.

A FAQ oficial ([Quais são os limites](https://brapi.dev/faq/quais-as-limitacoes)) descreve o gratuito como 15.000 requisições por ciclo, 1 requisição simultânea, 1 ticker por chamada, histórico de até 3 meses e atraso de ações de cerca de 30 minutos. O reset “não necessariamente no primeiro dia do mês” foi confirmado pelo header `x-ratelimit-window: billing-cycle`.

O que a FAQ diz e esta sessão não mostrou: atraso de ~30 minutos. O header `x-plan-data-freshness: 30s` e os timestamps abaixo mostram frescor de dezenas de segundos a poucos minutos. A FAQ continua sendo a regra publicada; o comportamento medido nesta tarde é outro. Isso precisa de uma segunda amostra em outro pregão antes de virar premissa permanente do serviço.

## Sandbox e cota não são a mesma coisa

ITUB4, e também HGLG11 em endpoints de FII, são tickers de demonstração da documentação. Nessas chamadas o limitador foi outro: `ratelimit-limit: 20`, `ratelimit-reset: 60`, sem `x-plan-data-freshness` e sem débito da cota de 15.000. A lista `/api/v2/tickers` caiu no mesmo balde de 20/60 s e a documentação diz que ela não exige token.

Por isso, série de 6 meses, série de 1 ano, candle de 15 minutos e `includeRaw=true` **em ITUB4** não provam direito do plano gratuito. O mesmo vale para `GET /api/v2/fii/historical?symbols=HGLG11`, que respondeu 200 com cerca de 251 pregões (além do teto de 3 meses) nesse balde de sandbox.

O plano gratuito foi medido nas chamadas que trouxeram limite 15.000 e `x-plan-data-freshness: 30s`: as 22 cotações fora de ITUB4, o histórico diário de 3 meses de HGLG11 e de SNAG11, e os erros de plano em HGLG11, SNAG11 e WEGE3.

## Cobertura dos 23 ativos

Endpoint usado: `GET /api/v2/stocks/quote?symbols={ticker}` com `Authorization: Bearer`.

Todos os 23 voltaram HTTP 200, `changed=false`, ticker retornado igual ao solicitado, moeda BRL e preço numérico dentro da máxima e da mínima do dia. Não houve 401, 403, 404 nem 429. A latência no cliente ficou entre 170 ms e 619 ms (HGBS11 619 ms, ALZR11 516 ms; as demais cotações abaixo de 260 ms). O campo `took` da brapi veio 0 ou 1 ms: ele mede processamento no servidor, não o tempo até o cliente.

O tipo veio de `GET /api/v2/tickers`, não do sufixo do ticker. Os 13 FIIs estavam na lista `subType=fii` (335 fundos, uma página). As três units estavam na lista `subType=unit` (12 nomes). As seis ações estavam na lista `subType=stock` (767 nomes). SNAG11 veio em `search=SNAG11` como `assetType=fund` e `subType=fi-agro`, `isActive=true`.

| Ticker | Tipo API | Preço | `regularMarketTime` (UTC) | Idade vs pedido | Volume | Máx / mín | Fech. ant. |
| --- | --- | ---: | --- | ---: | ---: | --- | ---: |
| WEGE3 | stock | 50,49 | 2026-10-05T17:17:30.000Z | 87 s | 11.484.200 | 52,50 / 49,95 | 50,47 |
| BPAC11 | unit | 82,92 | 2026-10-05T17:17:30.000Z | 87 s | 40.588.700 | 84,59 / 75,40 | 82,93 |
| ITUB4 | stock | 49,96 | 2026-10-05T17:17:30.000Z | 87 s | 65.880.100 | 50,68 / 48,08 | 49,95 |
| EGIE3 | stock | 32,53 | 2026-10-05T17:18:30.000Z | 27 s | 4.447.800 | 33,10 / 31,09 | 32,53 |
| TAEE11 | unit | 44,41 | 2026-10-05T17:17:30.000Z | 88 s | 4.646.400 | 46,45 / 43,40 | 44,42 |
| SBSP3 | stock | 31,69 | 2026-10-05T17:17:30.000Z | 88 s | 35.305.300 | 31,88 / 30,50 | 31,66 |
| SAPR11 | unit | 38,21 | 2026-10-05T17:17:30.000Z | 88 s | 2.330.600 | 38,65 / 35,98 | 38,21 |
| TIMS3 | stock | 19,40 | 2026-10-05T17:18:30.000Z | 28 s | 10.186.300 | 20,05 / 19,30 | 19,40 |
| CMIG4 | stock | 12,10 | 2026-10-05T17:17:30.000Z | 88 s | 32.548.200 | 12,33 / 11,90 | 12,11 |
| HGLG11 | fii | 150,46 | 2026-10-05T17:17:30.000Z | 89 s | 165.103 | 155,00 / 148,63 | 150,39 |
| VRTA11 | fii | 72,47 | 2026-10-05T17:15:30.000Z | 209 s | 24.013 | 73,50 / 71,13 | 72,47 |
| BTLG11 | fii | 100,76 | 2026-10-05T17:17:30.000Z | 89 s | 226.810 | 102,38 / 100,06 | 100,76 |
| RBVA11 | fii | 8,80 | 2026-10-05T17:18:30.000Z | 29 s | 170.520 | 8,90 / 8,75 | 8,80 |
| HGBS11 | fii | 18,57 | 2026-10-05T17:18:30.000Z | 29 s | 283.409 | 18,85 / 18,36 | 18,58 |
| GARE11 | fii | 8,46 | 2026-10-05T17:17:30.000Z | 90 s | 1.057.032 | 8,58 / 8,42 | 8,46 |
| ALZR11 | fii | 10,22 | 2026-10-05T17:18:30.000Z | 30 s | 427.594 | 10,50 / 10,10 | 10,21 |
| MXRF11 | fii | 9,16 | 2026-10-05T17:17:30.000Z | 91 s | 2.413.710 | 9,34 / 9,12 | 9,16 |
| BTHF11 | fii | 8,84 | 2026-10-05T17:18:30.000Z | 31 s | 368.008 | 8,98 / 8,76 | 8,84 |
| GGRC11 | fii | 9,08 | 2026-10-05T17:18:30.000Z | 31 s | 1.330.698 | 9,20 / 8,95 | 9,07 |
| XPLG11 | fii | 96,08 | 2026-10-05T17:17:30.000Z | 91 s | 79.848 | 97,29 / 94,54 | 96,08 |
| KNCR11 | fii | 104,44 | 2026-10-05T17:17:30.000Z | 92 s | 386.147 | 106,37 / 104,02 | 104,44 |
| KNHY11 | fii | 94,66 | 2026-10-05T17:17:30.000Z | 92 s | 53.528 | 95,70 / 93,67 | 94,66 |
| SNAG11 | fi-agro | 9,91 | 2026-10-05T17:18:30.000Z | 32 s | 159.360 | 10,06 / 9,89 | 9,91 |

Pedidos entre 17:18:56Z e 17:19:03Z. `requestedAt` do servidor ficou cerca de 2 segundos à frente do relógio do cliente. A idade contra `requestedAt` é essa mesma idade mais ~2 segundos.

`marketCap` veio preenchido nas seis ações e `null` nas três units, nos 13 FIIs e em SNAG11. Nenhum campo relevante de cotação faltou na chave. `shortName` repetiu o ticker; o nome descritivo está em `longName`.

### Idade da cotação

Os timestamps caem numa grade de 30 segundos: 17:15:30Z, 17:17:30Z ou 17:18:30Z. Como os pedidos saíram juntos por volta de 17:19:00Z, a idade se agrupou em ~30 s ou ~90 s. A média foi 74 s. O único ponto fora dessa grade foi VRTA11, 209 s (14:15:30 BRT), com header `x-brapi-stale: 1`.

KNHY11 também veio com `x-brapi-stale: 1`, mas o timestamp foi 17:17:30Z, igual ao de vários ativos sem esse header. Nesta amostra o header não significa, sozinho, “cotação muito mais velha”.

Isso foi medido numa tarde de pregão (por volta de 14:19 BRT). Não há amostra de abertura, de fechamento nem de mercado fechado. A idade mede a distância entre o pedido e `regularMarketTime`. Ela não prova que houve negócio naquele segundo.

A documentação de cotação ([/docs/acoes/cotacao](https://brapi.dev/docs/acoes/cotacao)) e a de autenticação ([/docs/authentication](https://brapi.dev/docs/authentication)) descrevem `regularMarketTime` como o horário embutido na cotação e mandam ler `RateLimit-*`, `Retry-After` e `X-Brapi-Concurrency-Limit`. `Retry-After` não apareceu, porque não houve 429.

## Variação pronta não fecha com o preço

Em 22 dos 23 ativos, `regularMarketChange` não é `regularMarketPrice - regularMarketPreviousClose` (tolerância de R$ 0,02). Também não é a diferença contra `regularMarketOpen`, exceto em ALZR11 e KNCR11. Em 16 dos 23, `regularMarketChangePercent` fica a menos de 0,15 ponto percentual de `change / previousClose * 100`. Nos outros sete, nem essa conta interna fecha.

Exemplo medido, BPAC11: preço 82,92, fechamento anterior 82,93, abertura 76,00, `regularMarketChange` 16,9 e `regularMarketChangePercent` 25,6. O preço está dentro da faixa do dia (75,40–84,59), mas a variação pronta não sai desses preços.

Para alerta de variação desde o fechamento anterior, o serviço deve calcular `(preço - fechamentoAnterior) / fechamentoAnterior` e guardar os dois insumos. Tratar `regularMarketChangePercent` como percentual oficial produz alerta falso.

## Histórico

Endpoint de integração nova, conforme a documentação de histórico ([/docs/acoes/historico](https://brapi.dev/docs/acoes/historico)): `GET /api/v2/stocks/historical`. O endpoint legado `/api/quote/{ticker}?range&interval` continua ativo e sem data de remoção ([/docs/acoes](https://brapi.dev/docs/acoes)); a migração está em [/docs/acoes/migracao-v2](https://brapi.dev/docs/acoes/migracao-v2).

### Janela e intervalo no plano gratuito

`GET /api/v2/stocks/historical?symbols=WEGE3&range=1y&interval=1d` (chamada 40, ticker fora do sandbox) voltou HTTP 400, `code=INVALID_RANGE`, `currentPlan=free`. Ranges permitidos no corpo do erro: **`1d`, `5d`, `1mo`, `3mo`**. O upgrade citado para `6mo` e `1y` é o Startup. A cota permaneceu em 14.975: esse 400 não debitou.

`range=3mo&interval=1d&sortOrder=asc` em HGLG11 e SNAG11 voltou HTTP 200 e debitou a cota. Cada série tem **64 pregões**, de 2026-07-07 a 2026-10-05, ordem estritamente crescente, sem data duplicada, OHLC e volume preenchidos. O único dia útil sem candle nas duas séries é **2026-09-07** (feriado de 7 de setembro). ITUB4 3 meses trouxe a mesma janela e o mesmo buraco, mas essa chamada caiu no sandbox.

`range=5d&interval=15m` em HGLG11 e em SNAG11 voltou HTTP 400, `code=INVALID_INTERVAL`, `currentPlan=free`. Intervalo permitido: **somente `1d`**. A mensagem cita o Startup para `1m`, `2m`, `5m`, `15m`, `30m`, `60m`, `90m`, `1h`, `1d`, `5d`, `1wk`, `1mo` e `3mo`. Esses 400 também não debitaram.

ITUB4 com `6mo`, `1y` e `15m` voltou HTTP 200 no limitador de 20/60 s (125, 249 e 98 pontos). Isso fica registrado como comportamento de sandbox, não como direito do gratuito. No candle de 15 minutos de ITUB4, `adjustedClose` veio `null` nos 98 pontos; o último candle estava em 2026-10-05T14:15:00-03:00.

A enum da documentação lista mais ranges e intervalos do que o plano aceita. Um valor documentado e fora da lista do plano volta 400.

### `adjustedClose` e o candle do dia

Na série diária de 3 meses, `adjustedClose` veio sempre preenchido. Diferiu de `close` em 61 de 64 pontos (HGLG11; o mesmo padrão apareceu no sandbox de ITUB4) e em 50 de 64 (SNAG11). Os pontos recentes coincidem. A documentação manda usar `adjustedClose` para retorno, porque ele incorpora proventos, desdobramentos e grupamentos, e avisa que `close` pode vir sem ajuste. A desigualdade medida confirma que os dois campos não são aliases.

O `date` diário é Unix em segundos em **03:00:00Z**, que é **00:00 em America/Sao_Paulo**. É a data do pregão, não o horário do último negócio. Preservar o inteiro e a data BRT derivada.

O candle de 2026-10-05 ainda se mexia durante a auditoria. Em HGLG11, o histórico de 3 meses fechou o dia em 150,31 com volume 165.311; a cotação, segundos antes, estava em 150,46 com volume 165.103. O candle do dia corrente é parcial até o fim do pregão.

### `includeRaw` e histórico de FII dedicado

A documentação marca `includeRaw=true` como recurso Pro (`rawOpen`, `rawHigh`, `rawLow`, `rawClose`). Em ITUB4 sandbox a chamada voltou 200 e as chaves existiram; no candle mais recente `rawClose` veio `null`. Não houve teste desse parâmetro num ticker debitado na cota de 15.000. Não usar no gratuito.

`GET /api/v2/fii/historical` está documentado como plano Pro, com exceção de sandbox para MXRF11 e HGLG11 ([/docs/fiis/historico](https://brapi.dev/docs/fiis/historico) e [/docs/fiis](https://brapi.dev/docs/fiis)). A chamada com HGLG11 voltou 200 no balde de 20/60 s, no formato `{ "fiis": [...] }`, não no envelope `results[]` de ações, com série bem mais longa que 3 meses. Isso é a exceção de sandbox, não a liberação do endpoint para os outros 12 FIIs. Os endpoints de fundos (FIAGRO, FI-Infra, FIF, FIDC, FIP) estão documentados como Pro ([/docs/fundos](https://brapi.dev/docs/fundos)) e não foram chamados.

O caminho que funcionou para FII e para FIAGRO na cota gratuita foi `/api/v2/stocks/historical` e `/api/v2/stocks/quote`.

### Legado `/api/quote/ITUB4`

HTTP 200, mesmo preço (49,96) e mesmo `regularMarketTime` da v2. O corpo legado coloca os campos no item de `results[]`, sem `requestedSymbol`, `changed` nem objeto `data`. Acrescenta `priceEarnings` e `earningsPerShare`, ausentes na v2 desta sessão. A chamada caiu no sandbox porque o ticker era ITUB4. Para código novo, a documentação manda usar v2.

## O que dá para monitorar

| Necessidade | Classificação | Evidência |
| --- | --- | --- |
| Alerta de preço acima ou abaixo de um nível | Verificado, com buraco entre coletas | Preço numérico nos 23; idade de 27–209 s neste pregão |
| Variação percentual desde o fechamento anterior | Verificado se for calculada no serviço | `regularMarketPrice` e `regularMarketPreviousClose` presentes nos 23. Os campos `regularMarketChange` e `regularMarketChangePercent` não reproduzem essa conta |
| SMA20 e SMA50 diários | Verificado na janela de 3 meses | 64 closes diários em HGLG11 e SNAG11. SMA50 produz o valor corrente e poucos pontos anteriores |
| RSI14 diário | Verificado | A mesma série passa das 15 sessões |
| EMA9 e EMA21 diários | Verificado, com histórico curto | Dá para calcular o valor corrente. Uma EMA21 semeada em 64 pregões não reproduz uma EMA semeada em vários anos |
| Volume atípico contra a média diária | Verificado, com pregão incompleto | Volume na cotação e nos 64 candles. Antes do fechamento o volume do dia é parcial e parece baixo contra um dia cheio |
| Candle intradiário e movimento dentro do intervalo de 30 minutos | Não suportado no gratuito | `INVALID_INTERVAL`: o plano só aceita `1d` |
| Cruzamento que aparece e volta antes da próxima coleta | Não detectável por snapshot | Consequência da amostragem. Não foram feitos dois polls; a auditoria não podia ficar consultando em ciclo |

Um nível que continua violado até a coleta seguinte é visto na coleta seguinte. Com frescor de poucos minutos e poll de 30 minutos, o atraso de notificação fica em torno de um intervalo de coleta mais a idade da cotação: nesta amostra, cerca de 30 a 34 minutos se o preço permanecer além do limite. Isso cabe na meta de 30–60 minutos.

Um estouro que dura dez minutos e reverte antes da coleta seguinte não gera alerta. Candle de 15 minutos resolveria parte disso e está no Startup, não no gratuito. Poll de 60 minutos empurra o caso persistente para perto de 65 minutos e estoura o teto de 60 minutos desta amostra. Por isso a cadência recomendada é 30 minutos, não 60.

Se a FAQ de ~30 minutos de atraso voltar a valer num pregão futuro, o mesmo poll de 30 minutos fica no limite da meta (atraso do dado mais intervalo da coleta). Repetir a medida de idade é o teste de acompanhamento mais importante.

## Endpoints para o Java

Cliente sugerido: Spring `RestClient` (ou `WebClient` com no máximo uma chamada em voo). Base `https://brapi.dev/api`. Header `Authorization: Bearer`. Token só em variável de ambiente. Timeout de cliente de 5 segundos cobre a pior latência vista (619 ms) com folga. Não colocar o token na query string: a própria documentação de autenticação desaconselha `?token=` porque a chave vaza em log e histórico.

| Uso | Método e URL |
| --- | --- |
| Cotação de um ativo | `GET /api/v2/stocks/quote?symbols={ticker}` |
| Backfill e refresh diário | `GET /api/v2/stocks/historical?symbols={ticker}&range=3mo&interval=1d&sortOrder=asc` |
| Tipo do instrumento, no cadastro | `GET /api/v2/tickers?search={ticker}` |

Não chamar, no gratuito: mais de um ticker em `symbols`, `interval` diferente de `1d`, `range` fora de `1d`/`5d`/`1mo`/`3mo`, `includeRaw=true`, `/api/v2/fii/*` e `/api/v2` de fundos. Travar a conta inteira em uma requisição por vez por causa de `x-brapi-concurrency-limit: 1`. Em 429, esperar `Retry-After` e não paralelizar de novo. Não houve 429 nesta sessão, então esse caminho não foi exercitado.

Ler `x-brapi-stale` e recalcular a variação no serviço. Tratar o candle do dia como parcial. Usar `adjustedClose` nas médias e no RSI. Guardar `regularMarketTime` cru e normalizado em UTC. O `date` histórico fica em Unix segundos e em data de America/Sao_Paulo.

## Pedidos executados

| # | Pedido | HTTP | Debitou 15.000? |
| --- | --- | --- | --- |
| 1 | `GET /api/v2/stocks/quote?symbols=ITUB4` | 200 | Não (sandbox 20/60 s) |
| 2 | `GET /api/v2/stocks/quote?symbols=ITUB4,WEGE3` | 400 `QUOTES_PER_REQUEST_EXCEEDED` | Sim |
| 3–10 | Cotação individual de WEGE3, BPAC11, EGIE3, TAEE11, SBSP3, SAPR11, TIMS3, CMIG4 | 200 | Sim |
| 11–23 | Cotação individual dos 13 FIIs | 200 | Sim |
| 24 | Cotação de SNAG11 | 200 | Sim (remaining 14977) |
| 25 | Histórico ITUB4 `3mo` `1d` | 200 | Não (sandbox) |
| 26 | Histórico HGLG11 `3mo` `1d` | 200 | Sim |
| 27 | Histórico SNAG11 `3mo` `1d` | 200 | Sim (remaining 14975) |
| 28 | Histórico ITUB4 `6mo` `1d` | 200 | Não (sandbox) |
| 29 | Histórico ITUB4 `1y` `1d` | 200 | Não (sandbox) |
| 30 | Histórico ITUB4 `5d` `15m` | 200 | Não (sandbox) |
| 31 | Histórico HGLG11 `5d` `15m` | 400 `INVALID_INTERVAL` | Não |
| 32 | Histórico SNAG11 `5d` `15m` | 400 `INVALID_INTERVAL` | Não |
| 33 | Histórico ITUB4 `3mo` `1d` `includeRaw=true` | 200 | Não (sandbox) |
| 34 | `GET /api/v2/fii/historical?symbols=HGLG11` | 200 | Não (sandbox) |
| 35 | `GET /api/quote/ITUB4` | 200 | Não (sandbox) |
| 36 | `GET /api/v2/tickers?subType=fii&limit=2000` | 200 | Não (limite público 20/60 s) |
| 37 | `GET /api/v2/tickers?subType=unit&limit=2000` | 200 | Não |
| 38 | `GET /api/v2/tickers?subType=stock&limit=2000` | 200 | Não |
| 39 | `GET /api/v2/tickers?search=SNAG11` | 200 | Não |
| 40 | Histórico WEGE3 `1y` `1d` | 400 `INVALID_RANGE` | Não (remaining seguiu 14975) |

Total de HTTP: **40**. Debitadas na cota do ciclo: **25**. Remaining final: **14.975** de 15.000.

O 400 de “mais de um ticker” debitou. Os 400 de range e de intervalo, nesta sessão, não debitaram. Não dá para tratar todo 400 como gratuito.

## Riscos em aberto

- Uma única tarde de pregão. O frescor de 30 s publicado no header, e o de até ~3,5 minutos medido, pode não se repetir na abertura, no fechamento ou com a bolsa fechada. A FAQ ainda fala em ~30 minutos.
- `x-brapi-stale: 1` apareceu em VRTA11 (cotação mais velha) e em KNHY11 (idade parecida com a dos demais). O contrato desse header não está explicado na documentação lida.
- `regularMarketChange` e `regularMarketChangePercent` estão inconsistentes com preço e fechamento anterior. Pode ser defeito momentâneo do feed.
- `includeRaw` e `/api/v2/fii/historical` para um FII que não seja MXRF11/HGLG11 não foram medidos na cota. A documentação os coloca no Pro; o único 200 veio do sandbox.
- Não houve 429, então `Retry-After` e o comportamento com a cota esgotada não foram vistos.
- O candle de hoje muda durante o pregão. Indicador que inclui o dia corrente precisa ser recalculado no fechamento.
- Cruzamento curto entre duas coletas continua invisível no gratuito.

Testes seguintes, se houver outra janela de chamadas: uma cotação da carteira depois do fechamento; `includeRaw` em WEGE3 (espera-se recusa de plano); `/api/v2/fii/historical?symbols=VRTA11` (espera-se recusa de plano). Não vale repetir intradiário: o erro de HGLG11 e SNAG11 já lista o único intervalo do gratuito.
