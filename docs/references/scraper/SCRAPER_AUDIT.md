# Auditoria do scraper financeiro

Data da leitura: 2026-10-05. Modo somente leitura. Nenhum TTL, cache, migração ou dado de produção foi alterado.

## Escopo real

O workspace `projecao-carteira` **não é** o scraper. É um analisador local de carteira (Python) que já consome a API como fonte secundária. O serviço em `http://192.168.22.245:8088` é o projeto `C:\ws\ticker-scraper` (Spring Boot). O OpenAPI ao vivo (`GET /v3/api-docs`, 200, 25 169 bytes) coincide com os controllers desse código.

Fontes cruzadas:

- código em `C:\ws\ticker-scraper\tickerscraper`
- contrato ao vivo `/v3/api-docs` e `GET /actuator/health` (não dispara scraping de ticker)
- cliente já existente em `src/local_fundamentals.py`, `src/fii_local_api.py`, `tools/update_local_fundamentals.py`, `tools/update_fii_local_api.py`

Não foram chamados `GET /api/v1/ticker/{ticker}` nem as rotas `/acao`, `/fii`, `/etf`, `/bdr`. Essas rotas raspam a origem quando o registro está ausente ou vencido.

## Stack verificada

| Item | Evidência |
| --- | --- |
| Entrada | `tickerscraper` Spring Boot, `spring.application.name: tickerscraper` |
| Java / Boot | README: Java 21, Spring Boot 3.5.3, WebFlux, Spring Data JPA |
| Banco | PostgreSQL, Flyway `classpath:db/migration`, `ddl-auto` default `none` |
| Cache de classificação | Caffeine, `CacheConfig.tickerClassificationCache` |
| Cache de dados | PostgreSQL, idade máxima fixa em código |
| Scraping | Playwright (caminho ativo), Selenium como fallback de ação |
| Origem HTML | `https://investidor10.com.br/{acoes\|fiis\|etfs\|bdrs}/` |
| Classificação ambígua | `BrapiHttpClient.getQuote` → `GET {base}/quote/{ticker}` |
| Scheduler | nenhum `@Scheduled` encontrado; refresh só no GET |
| Saúde ao vivo | `actuator/health` = UP; circuit breaker `scraper` CLOSED; Postgres UP; processo em `/app/.` (container) |

`application.yml` contém senha de banco e token Brapi em texto. Os valores **não** são reproduzidos aqui. Devem ser rotacionados e tirados do arquivo versionado. O cliente Brapi mascara o token no log (`token=***`).

## Contrato HTTP real

Servidor declarado no OpenAPI: `http://192.168.22.245:8088`.

Todas as rotas abaixo são GET. Respostas de erro documentadas: 400 ticker inválido, 404 não encontrado na origem, 422 estrutura incompatível, 429 limite na origem, 500 interno, 503 scraper ocupado, 504 tempo esgotado.

| Rota | Controller / método | Corpo 200 |
| --- | --- | --- |
| `/api/v1/ticker/{ticker}` | `TickerController.obterDadosTicker` | `AtivoResponseDTO` |
| `/api/v1/ticker/{ticker}/classificacao` | `TickerController.classificarTicker` | `ClassificacaoResponse` (`ticker`, `tipo`) |
| `/acao/get-{ticker}` | `AcaoController.get` | `AcaoResponseDTO` |
| `/acao/get-{ticker}/raw` | `AcaoController.getRawData` | `AcaoRawDataResponse` |
| `/fii/get-{ticker}` | `FiiController.get` | `FiiResponseDTO` |
| `/fii/get-{ticker}/raw` | `FiiController.getRaw` | `FiiRawDataResponse` |
| `/etf/get-{ticker}` | `EtfController` | `EtfResponseDTO` |
| `/etf/get-{ticker}/raw` | `EtfController` | `EtfRawDataResponse` |
| `/bdr/get-{ticker}` | `BdrController` | `BdrResponseDTO` |
| `/bdr/get-{ticker}/raw` | `BdrController` | `BdrRawDataResponse` |

Não há rota de histórico OHLCV, de cotação intradiária, de lista de watchlist, de alerta, nem de leitura “só cache”. Timeout do controller: 28 s (`Duration.ofSeconds(28)`).

`AtivoResponseDTO` declara campos de pregão (`regularMarketOpen`, high/low, volume, previous close, etc.), mas `TickerUseCaseService.delegarParaUseCaseEspecifico` **não** chama `AtivoResponseDTO.fromBrapi`. Esses campos só seriam preenchidos por um factory que o fluxo unificado não usa. No caminho real, ação/FII/ETF/BDR devolvem o snapshot raspado.

## Fluxo de uma consulta

`TickerUseCaseService.obterAtivo`:

1. `classificarTicker`
2. cache Caffeine (`TickerClassificationCacheService`)
3. se ausente, `TickerDatabaseStrategy.verificarTickerNoBanco`
4. se o ticker ainda não existe no banco, `BrapiHttpClient.getQuote` + `BrapiResponseClassifier`
5. delega ao use case do tipo (`AcaoUseCaseService`, `FiiUseCaseService`, `EtfUseCaseService`, `BdrUseCaseService`)

`AbstractTickerUseCaseService.getTickerData`:

- lê o registro por ticker
- se existe e `isCacheValid` → devolve o banco, sem browser
- senão → `scrape` e `persistFromRaw` (upsert)

`getRawInfrastructureData` segue a mesma regra. Com cache válido, devolve o JSON já gravado. Vencido, raspa de novo e substitui o JSON.

Idade máxima, hardcoded no construtor de cada use case: `Duration.ofDays(1)`.

- Ação: `AcaoUseCaseService.isCacheValid` compara `dataAtualizacao` (`LocalDateTime`) com `LocalDateTime.now()`.
- FII e ETF: o mesmo padrão com `LocalDateTime`.
- BDR: `Duration.between(ts, Instant.now())`.

A comparação é estrita (`compareTo(maxAge) < 0`). Com exatamente 24 h o registro já é inválido.

O carimbo é a hora da coleta, não a data do pregão:

- `AcaoScraperMapper.toDomain` e `FiiScraperMapper.toDomain` fazem `LocalDateTime.now()`
- `EtfScraperMapper` idem
- BDR mapeia `updatedAt` da raspagem

Não há alinhamento a dia civil nem a sessão da B3.

## As nove perguntas de cache

1. **TTL global ou por recurso?** Dois mecanismos independentes. A classificação em memória é global na JVM: `ticker.classification.cache.ttl-hours: 1`, `max-size: 1000`, `expireAfterWrite` (`CacheConfig`). O snapshot financeiro é por ticker, na linha do Postgres, com teto de 1 dia fixo no Java. Preço e fundamentos da mesma linha compartilham esse teto. Não há TTL por campo.

2. **Expira em relação a quê?** À coleta (`dataAtualizacao` / `Instant` do BDR). Não ao calendário e não ao pregão. `LocalDateTime.now()` usa o fuso da JVM do container.

3. **Preço e fundamentos podem ter frescor separado?** Não. Uma linha, um carimbo, um `isCacheValid`. Encurtar o TTL refresca os dois e dispara browser para os dois.

4. **Dá para servir histórico sem raspagem nova?** O último snapshot e o último JSON bruto, sim, enquanto a idade for menor que 1 dia. Não existe série diária para “servir histórico”. Depois de 1 dia, o GET não devolve o dado velho: ele raspa. Não há query `cachedOnly`.

5. **Falha da origem?** `AbstractTickerUseCaseService` não faz stale-while-revalidate. Cache vencido + falha de scrape propaga erro (429/503/504/500 conforme `GlobalExceptionHandler`). O circuit breaker `scraper` (janela 10, limiar 50 %, aberto 30 s) protege o browser; não devolve a linha antiga. Ação tem fallback Playwright → Selenium (`AcaoPlaywrightScraperAdapter.fallbackToSelenium`). Indicador de ação ausente vira `BigDecimal.ZERO` (`AcaoScraperMapper.getIndicatorValueAsBigDecimal` e `IndicadorParser.parseBigdecimal`). No FII, indicador ausente fica `null` e o update ignora nulos (`FiiPersistenceMapper.updateEntity`, `NullValuePropertyMappingStrategy.IGNORE`), então o valor antigo permanece com carimbo novo.

6. **Refreshes sobrepostos?** Sim. Não há single-flight por ticker. Duas requisições que encontram o cache vencido enfileiram duas raspagens. O browser é um único thread (`ScraperExecution.OWNER`, fila 8). A 9ª tarefa recebe `RejectedExecutionException` → HTTP 503 `SCRAPER_BUSY`, `Retry-After: 5`. Orçamento interno: 25 s (`ScraperExecution.BUDGET`).

7. **Carimbo e procedência?** Parcial. `dados_brutos_json` guarda o DTO da última raspagem. Raw DTO tem `source`, `scrapingTimestamp`, `processingStatus` (`SUCCESS`, `PARTIAL`, `FAILED`, `CACHED`, `FALLBACK`). No endpoint unificado de **ação**, `AtivoResponseDTO.fromAcao` substitui `dataAtualizacao` por `LocalDateTime.now()` na hora da resposta. O cliente não vê a idade real do preço. FII/ETF/BDR repassam o carimbo persistido. Não há período contábil. O consumidor em `src/local_fundamentals.py` (`asof_provenance_ready`) já trata isso como as-of impossível.

8. **Polling intradiário de 23 ativos?** Com cache quente, os GETs repetidos em menos de 24 h não raspam. Não entregam preço intradiário: devolvem o snapshot do dia. Se o TTL cair para minutos, 23 páginas Playwright numa fila de 1 thread estouram o orçamento (cada uma até 25 s) e passam a receber 503. A origem é o HTML do Investidor10, com exceções de anti-bot e rate limit já modeladas (`AntiBotDetectedException`, `RateLimitExceededException`). Brapi, nesse serviço, só entra na classificação de ticker ainda inexistente no banco, com retry (3 tentativas). Não é o caminho de cotação do monitor.

9. **Histórico velho versus preço velho?** Não distingue. Dividendos de FII são apagados e reinseridos (`FiiRepositoryAdapter.saveReplacingDividends`). Dividendos de BDR são mesclados (`BdrRepositoryAdapter.save` → `savePreservingDividendHistory`). Preço, indicadores e JSON de ação/FII/ETF são o último upsert. Não há tabela de velas.

## Efeito de mudar o TTL

Não foi alterado. O valor de 1 dia está no código (`Duration.ofDays(1)`), não na propriedade `ttl-hours` (essa vale só para classificação).

| Mudança | Efeito |
| --- | --- |
| Aumentar a idade do snapshot | Menos browser, preço ainda mais defasado para alerta. Fundamentos continuam no mesmo relógio. |
| Reduzir para minutos | Cada poll de 23 tickers vira fila de Playwright. 503, timeout 504, risco de bloqueio no Investidor10. Não cria vela intradiária: só regrava o mesmo snapshot. |
| Mexer só em `ttl-hours` | Altera classificação em memória (1 h). Não altera frescor de preço. |
| TTL por campo | Não existe. Exigiria modelo novo (preço separado de fundamento) e deixaria de ser “reuso”. |

Regressões adicionais se o TTL cair: mais upserts com indicador `0` no lugar de ausente; FII perde meses de dividendo que a página não reenvie; carimbo de ação na API unificada continua mentindo “agora”.

## Schema (Flyway)

Migrações em `src/main/resources/db/migration`:

| Versão | Conteúdo |
| --- | --- |
| V1 | `acao` — um registro por ticker, `preco_atual NUMERIC(15,4)`, indicadores `NUMERIC(12,4)`, `dados_brutos_json JSONB`, `data_atualizacao TIMESTAMP WITHOUT TIME ZONE` |
| V2 | `fundo_imobiliario` + `fii_dividendo` (`mes DATE`, `valor NUMERIC(19,4)`, unique fundo+mês). `data_atualizacao TIMESTAMPTZ`. Cotação do FII é `NUMERIC(19,2)` |
| V3 | remove dividendos de FII duplicados e recria a unique |
| V4 | coluna `tipo_ativo` e classificação dos registros |
| V5 | `etf` |
| V6 | `bdr` + dividendos; demonstrativos de um ano (`dre_year`, `bp_year`, `fc_year`) |
| V7–V9 | junção em ativo financeiro, rename, JSON de auditoria do BDR |
| V10 | precisão numérica do BDR |

Não há tabela de candle, de corporate action, de carteira de imóveis ou de revisão histórica de preço.

Fuso: ação sem time zone; FII em `TIMESTAMPTZ` mapeado como `LocalDateTime` com `@UpdateTimestamp` (`FundoImobiliarioEntity`). BDR usa `Instant`. Três relógios diferentes para a mesma regra de 1 dia.

## Qualidade que invalida alerta

- Ausência vira zero em ação (`parseBigdecimal` e `getIndicatorValueAsBigDecimal`). P/L, ROE, DY ou LPA `0` passam pelo upsert (zero não é `null`, então o `IGNORE` de nulos não protege).
- FII vencido no detalhe e fresco no carimbo: nulo não sobrescreve, mas `dataAtualizacao` é “agora”.
- Percentual não vira fração no parser geral. `"79,97%"` permanece 79.97, não 0.7997. `parsePercentualParaDecimal` divide por 100, mas o mapper de indicadores não o usa. Escala `NUMERIC(5,2)` no DY/vacância do FII.
- Sufixo `M`/`B`/`T` multiplica por milhão/bilhão (`processarComSufixoEscala`). Texto mal raspado muda a ordem de grandeza.
- FII: `saveReplacingDividends` dá `clear()` nos dividendos antes de inserir a janela nova. Mês que sumir da página deixa de existir. Não é revisão auditável; é substituição.
- BDR preserva meses antigos e atualiza valor se mudou. Isso é histórico de provento, não de preço, e não cobre ação.
- Sem preço ajustado, sem desdobramento, sem ticker antigo. Um agrupamento muda `preco_atual` e os múltiplos no mesmo registro.
- `fromAcao` publica a hora da resposta como `dataAtualizacao`. Um alerta “preço fresco” na API unificada não prova coleta fresca.
- Cotação de FII com 2 casas; ação com 4. Não há tipo monetário único.
- CNPJ de FII trafega como texto da página (o consumidor já observou máscara). Ação não tem CNPJ no schema.
- Redistribuição: a página raspada é de terceiros (Investidor10), não feed licenciado da B3. Brapi aqui é classificador, com token próprio e termos da brapi.dev. Nenhum dos dois é base segura para revenda ou para sinal automático de compra/venda.

## O que o `projecao-carteira` já faz com isso

`src/local_fundamentals.py` documenta a API como `PLAYWRIGHT_SCRAPER`, snapshot atual, sem as-of. Normaliza um subconjunto de `dadosAcao` (preço, LPA, VPA, P/L, P/VP, DY, payout, ROE, ROIC, margens, alavancagem, CAGR). `src/fii_local_api.py` normaliza `dadosFii` e o raw `/fii/get-{ticker}/raw` (`indicadorHistorico`), ordena dividendos `MM/YYYY` porque a lista pode vir fora de ordem, e só marca FIAGRO se `segmento == "Fiagros"`. O cache local desse consumidor é arquivo JSON por ticker, sem TTL de um dia: a validade de um dia é da API, não desse arquivo.

Preço diário oficial neste repositório é outro componente: `src/b3_cotahist.py` (fechamento bruto COTAHIST, sem ajuste). Não vem do scraper.

## Testes executados

Em `C:\ws\ticker-scraper\tickerscraper`, 2026-10-05, só classes sem subir o contexto Spring (o `application.yml` aponta o Postgres da LAN e liga o Flyway):

| Classe | Resultado |
| --- | --- |
| `AcaoUseCaseServiceTest` | 9 ok |
| `FiiUseCaseServiceTest` | 9 ok |
| `TickerUseCaseServiceCacheDbTest` | 6 ok |
| `IndicadorParserTest` | 6 ok |
| `TipoAtivoTest` | 78 ok (inclui nested) |
| `BrapiResponseClassifierTest` | 6 ok |
| `DividendHistoryPreservationTest` | 2 ok (modelo em memória; não é o `clear()` do FII) |
| `DividendConversionRegressionTest` | 3 ok |
| `ScraperLifecycleTest` | 5 ok |
| `ClassificationIsolationRegressionTest` | 1 ok |

Não executados de propósito:

- `TickerscraperApplicationTests` e `TickerControllerIntegrationTest`: `@SpringBootTest` carregaria datasource e Flyway do ambiente real.
- Testes com nome Brapi de use case (`TickerUseCaseServiceBrapiAcaoTest` e irmãos): não reexecutados. O relatório Surefire de 2026-09-19 mostra `NoClassDefFoundError` de classpath, não uma falha de negócio reproduzida agora.
- Nenhuma suíte Playwright contra a rede, nenhum `mvn test` completo, nenhum GET de ticker.

`GET /actuator/health` foi lido. Não grava cache de ativo.
