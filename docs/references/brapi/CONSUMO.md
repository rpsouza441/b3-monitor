# Consumo da cota Brapi Free

Números de plano medidos nesta conta em 5 de outubro de 2026, não só os da FAQ.

## Cota real

| Item | Valor |
| --- | --- |
| Limite do ciclo | 15.000 requisições |
| Janela | `billing-cycle`, não o mês-calendário |
| Fim deste ciclo | 3 de novembro de 2026, 18:07:54 BRT |
| Remaining antes da auditoria | 15.000 (a primeira chamada debitada saiu com 14.999) |
| Remaining depois de 40 HTTP | 14.975 |
| Débitos desta auditoria | 25 |
| Simultâneas | 1 (`x-brapi-concurrency-limit`) |
| Tickers por chamada de cotação | 1 (HTTP 400 ao enviar 2) |
| Histórico no gratuito | `range` em `1d`, `5d`, `1mo`, `3mo`; `interval` somente `1d` |
| Frescor publicado no header | `x-plan-data-freshness: 30s` |
| Frescor medido nas 23 cotações | 27 s a 209 s; a maioria em ~30 s ou ~90 s |

O início do ciclo não veio na API. Só o instante final. Até 3 de novembro de 2026 há cerca de 29 dias corridos. O desenho de 20, 22 e 23 pregões abaixo é para um ciclo cheio. Neste ciclo, do dia 6 de outubro até o reset, o número de pregões é menor e depende do calendário da B3 (2 de novembro é Finados).

ITUB4 na rota de ações e a lista `/api/v2/tickers` caíram num limitador de 20 pedidos por 60 segundos e não mexeram na cota de 15.000. Não contar sandbox como se fosse crédito infinito de produção: o serviço real vai consultar WEGE3, os FIIs e SNAG11, e esses debitam.

## Fórmula

Pregão modelado com 8 horas. Poll só dentro do pregão.

```
coletas_por_dia = 8 * 60 / intervalo_minutos
cotações_no_ciclo = tickers * coletas_por_dia * pregões
```

No gratuito cada ticker é uma requisição. Trinta minutos dão 16 coletas por dia. Sessenta minutos dão 8.

Uma ida sequencial aos 23 nomes, com a latência vista (em geral ~0,2 s, pior caso 0,6 s), leva cerca de 5 a 15 segundos. Cabe folgada no intervalo de 30 minutos, desde que haja uma única chamada em voo.

## Só cotações

Percentual sobre 15.000.

| Tickers | Intervalo | Pregões | Requisições | % da cota |
| ---: | ---: | ---: | ---: | ---: |
| 23 | 30 min | 20 | 7.360 | 49,1% |
| 23 | 30 min | 22 | 8.096 | 54,0% |
| 23 | 30 min | 23 | 8.464 | 56,4% |
| 23 | 60 min | 20 | 3.680 | 24,5% |
| 23 | 60 min | 22 | 4.048 | 27,0% |
| 23 | 60 min | 23 | 4.232 | 28,2% |
| 30 | 30 min | 20 | 9.600 | 64,0% |
| 30 | 30 min | 22 | 10.560 | 70,4% |
| 30 | 30 min | 23 | 11.040 | 73,6% |
| 30 | 60 min | 20 | 4.800 | 32,0% |
| 30 | 60 min | 22 | 5.280 | 35,2% |
| 30 | 60 min | 23 | 5.520 | 36,8% |
| 40 | 30 min | 20 | 12.800 | 85,3% |
| 40 | 30 min | 22 | 14.080 | 93,9% |
| 40 | 30 min | 23 | 14.720 | 98,1% |
| 40 | 60 min | 20 | 6.400 | 42,7% |
| 40 | 60 min | 22 | 7.040 | 46,9% |
| 40 | 60 min | 23 | 7.360 | 49,1% |
| 50 | 30 min | 20 | 16.000 | 106,7% |
| 50 | 30 min | 22 | 17.600 | 117,3% |
| 50 | 30 min | 23 | 18.400 | 122,7% |
| 50 | 60 min | 20 | 8.000 | 53,3% |
| 50 | 60 min | 22 | 8.800 | 58,7% |
| 50 | 60 min | 23 | 9.200 | 61,3% |

Cinquenta tickers a cada 30 minutos estouram a cota só com cotação. Quarenta tickers a cada 30 minutos em 23 pregões deixam 280 pedidos (1,9%) para backfill, retentativa e reserva. Não é uma margem operável.

## Backfill

Um `GET /api/v2/stocks/historical?range=3mo&interval=1d` por ticker devolveu a janela inteira (64 pontos) numa resposta. Backfill inicial da carteira de 23: **23 requisições**, uma vez.

Não há paginação a percorrer nesse recorte. Não pedir `1y`, `6mo` nem `max`: WEGE3 com `1y` voltou 400.

Repetir o histórico em toda coleta de 30 minutos dobraria o custo (mais 8.464 no cenário de 23×23) e passaria de 15.000 junto com a cotação. O histórico diário não muda a cada 30 minutos, exceto o candle do dia, que a própria cotação já traz (preço, máxima, mínima, volume).

Refresh recomendado: **uma** chamada histórica por ticker por pregão, depois do fechamento, para gravar o candle oficial e o `adjustedClose`. Em 23 pregões são mais 529 requisições. Se a cota apertar, esse refresh pode cair para uma vez por semana (cerca de 23 × 4 = 92) porque o alerta intradiário usa a cotação, não o histórico.

## Falhas e retentativas

Nesta sessão:

- 400 `QUOTES_PER_REQUEST_EXCEEDED` debitou 1.
- 400 `INVALID_RANGE` e `INVALID_INTERVAL` não debitaram.
- Não houve timeout, 429, 500 nem 503.

Orçamento de retentativa: no máximo uma repetição, só para timeout, 429, 500 e 503, esperando `Retry-After` quando existir. Não repetir 400 de parâmetro. Reserva de trabalho de **5%** sobre cotações e refresh, para essas falhas. Não transformar retentativa em segundo poll.

## Reserva de 20–30%

| Reserva intocada | Teto utilizável |
| --- | ---: |
| 20% | 12.000 |
| 30% | 10.500 |

A reserva de 30% é a linha usada na recomendação. Ela cobre um pregão extra, um bug de loop e a diferença entre o calendário suposto e o reset real.

## Estratégia segura para os 23 ativos

Poll de cotação a cada **30 minutos**, somente nas 8 horas de pregão, um ticker por chamada, uma chamada por vez.

| Peça | Conta em 23 pregões |
| --- | ---: |
| Cotações, 16 coletas × 23 tickers × 23 pregões | 8.464 |
| Refresh diário do histórico de 3 meses | 529 |
| Backfill inicial, uma vez | 23 |
| Retentativa de 5% sobre cotação e refresh | 450 |
| **Total** | **9.466** |
| Teto com 30% de reserva | 10.500 |
| Folga | 1.034 |

Os cenários de 20 e 22 pregões ficam mais folgados (cerca de 8.235 e 9.055 no mesmo desenho).

Por que 30 minutos e não 60: a idade medida da cotação foi de no máximo 3,5 minutos. Um nível que permanece violado é percebido na coleta seguinte, com atraso perto de 30–34 minutos, dentro da meta de 30–60 minutos. Poll de 60 minutos leva esse caso para perto de 65 minutos. A cota de 60 minutos sobra, mas o atraso não cabe na meta com a mesma folga.

Por que não encurtar para menos de 30 minutos: a grade de `regularMarketTime` desta sessão foi de 30 segundos e o header de frescor diz `30s`, então um poll de 1 minuto ainda veria preço novo. O custo de 23 tickers a cada 5 minutos, 8 horas, 23 pregões, é 8.464 × 6 = 50.784, muito acima de 15.000. A meta de atraso não exige isso. O que o poll curto não compra, no gratuito, é o caminho do preço entre duas coletas: candle intradiário está fora do plano.

Trinta tickers a cada 30 minutos em 23 pregões são 11.040 só de cotação, acima do teto de 10.500. Não cabe na reserva de 30% junto com histórico. Quarenta nomes só caberiam se o poll fosse de 60 minutos, e aí o atraso de notificação fica pior do que a meta.

Não consultar de madrugada nem no fim de semana. Oito horas contínuas a mais por dia triplicam 8.464 e estouram o ciclo.

Implementação Java: um agendador dispara a cada 30 minutos dentro da janela; uma fila percorre os 23 símbolos; o cliente HTTP não abre a segunda chamada antes da primeira terminar; persiste `ratelimit-remaining` e `ratelimit-reset`; para de disparar se o remaining previsto da próxima rodada invadir a reserva de 30%. No ciclo corrente o remaining já está em 14.975 e o reset é 3 de novembro de 2026, 18:07 BRT, então a primeira versão deve contar pregões até essa data, não até o dia 31.
