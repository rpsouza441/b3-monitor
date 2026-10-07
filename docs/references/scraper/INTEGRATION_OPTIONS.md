# Opções de integração

Pergunta prática: um aplicativo novo, Java/Spring Boot, vai acompanhar 23 ativos da B3 com preço intradiário atrasado da Brapi Free. O que deste scraper pode entrar nesse aplicativo sem estragar o sinal.

Veredito: **KEEP SEPARATE**.

O scraper serve snapshot diário raspado de página. O monitor precisa de cotação atrasada, repetida no mesmo pregão, com dono e cota próprios na Brapi. São relógios, licenças e modos de falha diferentes. Encurtar o TTL do scraper não transforma HTML em intradiário.

## Comparação

| | A. API como está | B. Adapter só leitura | C. Extrair biblioteca | D. Fundir os projetos |
| --- | --- | --- | --- | --- |
| Acoplamento | HTTP, mas todo GET vencido raspa | Baixo se ler réplica ou um endpoint que não raspe; hoje esse endpoint não existe | Alto: Playwright, seletores CSS, DTOs do Investidor10 | Máximo: um processo, um browser, um Postgres |
| Complexidade | Baixa para enriquecimento eventual | Média: falta modo `cachedOnly` e o carimbo de ação está errado na rota unificada | Alta, e o código de página quebra com o layout | Alta, dois SLOs no mesmo deploy |
| Estabilidade do monitor | Ruim se o poll passar de 24 h ou se o cache esfriar: fila de 1 browser, 503 | Boa para fundamento já gravado; inútil para intradiário | O monitor passaria a depender de seletores | Poll de 23 nomes compete com o browser |
| Deploy | Já está no ar (`:8088`, health UP) | Novo contrato pequeno, ou leitura SQL fora da API | Dois artefatos para publicar | Um release arrasta scraper e alertas |
| Fonte da verdade | Investidor10 para o snapshot; Brapi só classifica ticker novo | Continua a página, só muda o acesso | Continua a página | Página e Brapi misturadas no mesmo preço |
| Manutenção | O monitor não herda seletores | O adapter quebra se o JSON mudar | Cada mudança de CSS vira release do monitor | Pior dos dois |

### A — manter o scraper e usar a API

Aceitável só como enriquecimento opcional, fora do caminho do alerta de preço. O cliente que o `projecao-carteira` já tem (`tools/update_local_fundamentals.py`, `tools/update_fii_local_api.py`) faz no máximo dois GETs por ticker e grava arquivo. Isso não é polling.

Não serve para 23 cotações no pregão:

- resposta de ação não é intradiária
- depois de 24 h o próximo GET raspa
- não há candles para indicador técnico
- `dataAtualizacao` de ação na rota unificada é a hora do HTTP, não a da coleta

### B — adapter somente leitura de histórico e fundamento

É o único reuso que vale a pena, e mesmo assim estreito.

O que dá para ler, com cache ainda válido ou direto no Postgres, sem browser:

- último fundamento de ação e o “Atual” de FII
- até ~12 meses de dividendo de FII, sujeitos a substituição
- dividendos acumulados de BDR
- JSON bruto da última coleta

O que não dá para ler: vela, preço ajustado, intradiário, carteira do fundo, as-of contábil.

Para não disparar scraping, o adapter não pode chamar o GET público quando a linha tiver 1 dia ou mais. Hoje a API não oferece essa leitura. Ou se acrescenta um modo explícito de só cache (esforço pequeno no scraper), ou se lê o banco com contrato interno. Ler o banco acopla o monitor ao schema e às credenciais. Preferir um GET novo que nunca chame `scrape`.

Mesmo com esse modo, fundamento raspado não deve gerar compra/venda. Zero no lugar de ausente e valor antigo com carimbo novo produzem múltiplo falso.

### C — biblioteca compartilhada

Os pedaços genéricos (`IndicadorParser`, enums `TipoAtivo`, cliente `BrapiHttpClient`) são pequenos. O miolo reutilizável seria o cliente Brapi, e ele hoje só pede `/quote/{ticker}` para classificar, com token e retry próprios.

Levar Playwright, `ScraperExecution` e os scrapers de CSS para o monitor importa anti-bot, fila de um thread e HTML frágil. Não ajuda a Brapi Free.

### D — um projeto só

Pior opção. O monitor passaria a dividir o browser, o pool Hikari (10 conexões) e o circuit breaker com raspagem de página. Um layout quebrado no Investidor10 abriria o breaker e derrubaria classificação e, se alguém misturar os fluxos, a cotação. Migrações Flyway desse serviço passariam a ser migrações do monitor. Credenciais de banco e token já estão no mesmo YAML.

## Quem deve ser dono de cada dado

| Dado | Dono | Por quê |
| --- | --- | --- |
| Fundamento oficial (DFP/ITR, informe de FII) | `projecao-carteira` / CVM, offline | Já tem as-of, versão e escala. O scraper não tem período contábil |
| Snapshot conveniente de tela (DY, P/VP “agora”) | Scraper, isolado | Útil para cruzar, não para alerta. Consumo eventual, cache de 1 dia |
| Cotação intradiária atrasada | Aplicativo novo + Brapi Free | Único fluxo desenhado para isso. Cota, atraso e símbolo ficam nesse serviço |
| Candles diários | Aplicativo novo, se a Brapi Free entregar a série dentro da cota; fechamento bruto oficial pode continuar no COTAHIST do `projecao-carteira` | O scraper não armazena vela. COTAHIST é bruto, não ajustado, e o cache local pode não ter todos os anos |
| Corporate actions / ajuste | Nenhum dos dois hoje | Scraper não modela evento. `projecao-carteira` tem `config/corporate_actions.yaml` e proventos de extrato, não um fator de ajuste de preço. Não promover isso a motor de alerta sem uma fonte explícita |
| Indicadores técnicos | Aplicativo novo, calculados das próprias velas | Não existem no scraper. Calcular em cima de `preco_atual` diário gera sinal atrasado 1 dia e sem OHLC |
| Watchlist e regras de alerta | Aplicativo novo | Não existem no scraper nem como rota |

O `projecao-carteira` continua dono da análise de carteira (posição, provento realizado, valuation as-of). Ele não deve passar a ser o barramento de cotação.

## Mudanças, se alguém ainda quiser reuso pontual

Ordem por valor para o monitor, não por vontade de refatorar o scraper.

| # | Mudança | Esforço | Risco | Valor para os 23 ativos |
| --- | --- | --- | --- | --- |
| 1 | Não integrar preço. O aplicativo novo fala com a Brapi Free e guarda as próprias velas e alertas | Baixo | Baixo | Resolve o caso de uso |
| 2 | Não reduzir `Duration.ofDays(1)` para “fazer intradiário” | Nenhum (não fazer) | Alto se feito: 503, bloqueio da origem, zeros gravados com mais frequência | Nenhum |
| 3 | Se um dia houver cruzamento de fundamento: endpoint só cache, sem `scrape`, devolvendo o `dataAtualizacao` persistido também para ação | Baixo no scraper | Médio: ainda é página de terceiros e indicador 0 | Baixo para alerta; médio para diagnóstico |
| 4 | Tratar ausente como nulo, não como zero, antes de qualquer consumidor automático | Médio (`AcaoScraperMapper`, `IndicadorParser.parseBigdecimal`) | Médio: muda contrato de quem já lê 0 | Alto para não mentir; não cria cotação |
| 5 | Separar coluna de preço e coluna de fundamento, com idades diferentes | Alto | Alto | Não substitui Brapi |
| 6 | Extrair biblioteca ou fundir repositórios | Alto | Alto | Negativo |

## Recomendação

**KEEP SEPARATE**

O scraper permanece um serviço de snapshot, com validade de um dia por ticker, origem em página e um browser serial. O monitor nasce ao lado, dono da watchlist, da cota Brapi Free, das velas e das regras. Fundamento oficial continua no fluxo CVM/`projecao-carteira`. Um adapter somente leitura do scraper é opcional e posterior, e só depois de existir leitura que não raspe e de o zero-por-ausência estar corrigido. Não é pré-requisito do monitor.

Reuso direto da API atual para o alerta de preço: não.
