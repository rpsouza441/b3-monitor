# Inventário de dados do scraper

Leitura de código e do OpenAPI em 2026-10-05. Nenhum ticker foi consultado, para não disparar Playwright nem Brapi. Onde a profundidade depende do HTML do dia, o status fica `UNKNOWN` ou `PARTIAL`, não `VERIFIED`.

Legenda: **VERIFIED** = contrato e persistência conferidos no código e no OpenAPI. **PARTIAL** = o campo existe, mas não cobre o uso (série, as-of, ajuste ou identidade). **ABSENT** = não há modelo, rota nem coluna. **UNKNOWN** = o código aceita o dado, mas a cobertura real não foi medida nesta sessão.

## Preço

| Capacidade | Status | Onde está | Limite |
| --- | --- | --- | --- |
| Último preço conhecido de ação | VERIFIED | `acao.preco_atual`; `AcaoResponseDTO.precoAtual`; preenchido de `infoCards.cotacao` | Um número por ticker. Idade até 1 dia. Na rota unificada o carimbo visto pelo cliente é a hora da resposta (`AtivoResponseDTO.fromAcao`) |
| Último preço de FII | VERIFIED | `fundo_imobiliario.cotacao` `NUMERIC(19,2)`; `FiiResponseDTO.cotacao` | 2 casas. Mesma linha dos fundamentos |
| Último preço de ETF / BDR | VERIFIED | `EtfResponseDTO.valorAtual`; `BdrResponseDTO.precoAtual` | Snapshot, não vela |
| Preço intradiário atrasado | ABSENT | `fromBrapi` existe e não é chamado por `TickerUseCaseService` | `regularMarket*` fica de fora do fluxo real |
| OHLC do dia (open, high, low, close, volume) | ABSENT | sem colunas e sem rota | Volume de FII no DTO unificado é constante `0L` |
| Série diária / candles | ABSENT | sem tabela além do upsert por ticker | |
| Preço ajustado por provento ou desdobramento | ABSENT | sem fator, sem evento | COTAHIST ajustado também não está neste serviço |
| Preço oficial de fechamento B3 | ABSENT neste serviço | no outro repositório: `src/b3_cotahist.py` (`RAW_CLOSE`, `PREULT/100`) | Fora do scraper; anos em cache podem ser incompletos |

## Histórico além de três meses

| Capacidade | Status | Onde está | Limite |
| --- | --- | --- | --- |
| Preço diário > 3 meses | ABSENT | — | O banco guarda só o último preço |
| Indicadores anuais de FII dentro do JSON bruto | PARTIAL | `FiiIndicadorHistoricoDTO` no `dados_brutos_json` e em `/fii/get-{ticker}/raw` | `FiiScraperMapper.pickAtual` persiste só a linha `year == "Atual"`. Anos anteriores, se a página os mandar, ficam no JSON da última coleta e são substituídos na seguinte. Quantos anos a origem manda não foi medido (sem GET) |
| Dividendos mensais de FII | PARTIAL | `fii_dividendo` (`mes`, `valor`); `FiiResponseDTO.dividendos[]` | Comentário do adapter: janela de 12 meses. `saveReplacingDividends` apaga a coleção e grava a lista nova. Não acumula além do que a página devolveu na última raspagem. Ordem da API não é garantida (o consumidor Python já trata isso) |
| Dividendos de BDR | PARTIAL | `BdrRepositoryAdapter.savePreservingDividendHistory` | Acumula meses ausentes na página nova e atualiza valor se mudou. Não é preço. Não vale para ação |
| Dividendos de ação | ABSENT | tabela `acao` não tem filha de provento | DY/payout são um número atual |
| Demonstrações de BDR | PARTIAL | `dre_year`, `bp_year`, `fc_year` e valores do último ano em `V6` | Um exercício, em USD, não uma série |
| CAGR 5 anos de ação | PARTIAL | `cagr_receitas_cinco_anos`, `cagr_lucros_cinco_anos` | Um escalar atual, não a série de receitas/lucros |

## Fundamentos e identidade

| Capacidade | Status | Onde está | Limite |
| --- | --- | --- | --- |
| Múltiplos e rentabilidade de ação (P/L, P/VP, DY, payout, margens, ROE, ROIC, ROA, alavancagem, LPA, VPA, liquidez) | VERIFIED como snapshot | `AcaoScraperMapper` lê a tabela `#table-indicators` | Sem data-base contábil. Ausência vira 0. Não usar como as-of |
| Balanço resumido de ação (PL, dívida, ativos, valor de mercado, valor da firma) | VERIFIED como snapshot | `infoDetailed` → colunas de `acao` | Mesmas ressalvas. Escala “milhão/bilhão” depende do texto HTML |
| Identidade de ação (nome, setor, segmento, listagem, tag along, free float) | VERIFIED | `AcaoResponseDTO` | Sem CNPJ |
| Identidade de FII (nome, razão social, CNPJ, mandato, segmento, tipo de fundo, gestão, público, prazo) | VERIFIED como texto da página | `FiiResponseDTO` / `infoSobre` | CNPJ pode vir mascarado. Sem chave estável além do ticker e de `internal_id` do site |
| Indicadores de FII (P/VP, DY, VP, VP/cota, vacância, cotistas, cotas, liquidez, valor de mercado) | VERIFIED como “Atual” | `pickAtual` | Colunas numéricas curtas (`NUMERIC(5,2)` em DY, vacância, variação 12 m). Nulo não apaga o valor antigo |
| Carteira / imóveis / CRIs do FII | ABSENT | sem tabela | |
| FIAGRO como tipo próprio | ABSENT | `TipoAtivo` não tem FIAGRO | |
| FIAGRO via segmento declarado | PARTIAL | campo `segmento` (`"Fiagros"` no contrato do consumidor `src/fii_local_api.py`) | Só se a página preencher `segmento`. Não há cobertura dedicada nem prova nova nesta sessão |
| ETF (nome, valor, DY, variação 12 m e 60 m, capitalização) | PARTIAL | `EtfResponseDTO` | Superfície menor que ação/FII. Variação 60 m é um número, não série |
| Classificação do papel (ON, PN, UNIT, FII, ETF, BDR, recibo) | VERIFIED | `TipoAtivo`, heurística + banco + Brapi | `ETF_BDR` não tem use case (`UnsupportedOperationException`) |
| Procedência | PARTIAL | JSON bruto + `dataAtualizacao` | Ver falha de carimbo da ação na rota unificada. Sem fonte B3/CVM |

## O que esta API não é

Não há corporate action, fator de ajuste, grupo de cotação, ISIN persistido, mudança de ticker, watchlist, regra de alerta, indicador técnico calculado, nem agenda de refresh. O “histórico” do README e do raw é indicador/dividendo embutido no último scrape, não uma série de mercado.

O histórico de preço reutilizável para candles diários, se for oficial e bruto, está no `projecao-carteira` (`src/b3_cotahist.py`), com buracos de ano conforme o cache local e sem ajuste. Isso é outro sistema, outro contrato e outra licença (arquivo público da B3), não um endpoint deste scraper.
