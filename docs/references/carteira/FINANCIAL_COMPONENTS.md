# Componentes financeiros — evidência do repositório

Auditoria somente leitura em `da62ec7` (`main`, 8 commits à frente de `origin/main`).
Não há `ROADMAP`, `REQUIREMENTS` nem `STATE.md`. O estado operacional declarado está em
`CHECKPOINT.md`, e o README descreve um recorte anterior (fases 1–4). O código
posterior é a fonte desta ficha. Onde um documento contradiz o código, o código vence
e o documento fica marcado como desatualizado.

Não existe banco relacional nem API HTTP. A persistência é arquivo local
(CSV/XLSX/YAML/ZIP), com dados privados em `data/input/` (gitignored).

Vocabulário de prontidão usado pelo próprio projeto: `READY`, `PARTIAL`,
`DIAGNOSTIC_ONLY`, `NOT_READY`. Um módulo `*_status.py` é contrato, não cálculo.

## 1. Posição e custódia

| Estado | Evidência |
| --- | --- |
| Implementado e testado | Posição canônica pela B3; Investidor10 é diagnóstico |
| Parcial | Preço de fechamento B3 depende de metadata explícita; sem ela o runtime volta ao Investidor10 |
| Planejado / pendente | `CHECKPOINT.md` pede `data/input/b3_posicao.meta.yaml` com `price_date` real. O arquivo real não é versionado |

- `src/canonical_portfolio.py` — `CanonicalPortfolio`, `build_canonical_portfolio`, `is_canonical_position_eligible`, `to_position_calc`.
- `src/portfolio.py` — `PositionCalc`, `compute_position`. Valorização = preço atual contra preço médio. O docstring (linhas 17–18) declara que isso não é rentabilidade de 12 meses.
- `src/b3_position_loader.py` — preço de fechamento do snapshot (`PRICE_TYPE=CLOSE`).
- `src/reconstructed_position.py` — reconstrução legada, de propósito conservadora: subscrição exercida continua não mapeada nessa camada (`position_history_status.py`, `LEGACY_SUBSCRICAO_EXERCIDA_UNMAPPED = 2`).
- `src/position_history.py` — quantidade por data, fim do dia, sem look-ahead. Testes: `tests/test_position_history.py`, `tests/test_canonical_portfolio.py`, `tests/test_reconstructed_position.py`.
- Elegibilidade: quantidade positiva só vira posição corrente se houver aquisição genuína, custo de evento societário ou override. Direito/recibo isolado sai como `SUBSCRIPTION_RIGHT` / `AUXILIARY_INSTRUMENT` (`canonical_portfolio.py`, por volta da linha 88).

`CHECKPOINT.md` registra 25/25 posições explicadas (10 ações, 14 FIIs, 1 BDR) e a incorporação BCFF11→BTHF11. Esses números são do handoff; o repositório público não lista os tickers da carteira.

## 2. Identificação de ativo e classe

Implementado e testado.

- `src/normalization.py` — `normalize_ticker`, `extract_ticker_from_produto`. Padrão `[A-Z]{4}\d{1,2}` com sufixo fracionário opcional. Vários tickers no mesmo texto retornam `None` (não escolhe).
- `src/asset_classification.py` — classe canônica só `ACAO`, `FII` ou `BDR`. O sufixo `11` não implica FII. O docstring cita BPAC11, SAPR11 e TAEE11 como ações. FIAGRO-IMOBILIÁRIO entra no balde `FII`. Conflito de texto B3 vira `DESCONHECIDA`.
- `config/asset_classes.yaml` — mapeamento explícito versionado só para lacuna. Conteúdo atual: `NVDC34: BDR`.
- `src/local_fundamentals.py` + `tests/test_local_fundamentals.py::test_normalize_unit` — o snapshot de fundamentos preserva `classificacao == "UNIT"` (exemplo sintético SAPR11). Isso não cria uma classe canônica `UNIT`.
- `src/fii_local_api_reconcile.py` — classe econômica de FII: tijolo, papel, FoF, híbrido, `FIAGRO`. FIAGRO tem rota própria e não é forçado a tijolo/papel.
- Testes: `tests/test_normalization.py`, `tests/test_asset_classification.py`, `tests/test_fii_local_api_reconcile.py`.

Não há watchlist. Ativo que só aparece em proventos fica `SEM_POSICAO_ATUAL` (`README.md`, seção de ativos vendidos; `src/reconciliation.py`).

## 3. Ações, units, FIIs e FIAGRO

| Tipo | Como o projeto representa | Limite |
| --- | --- | --- |
| Ação ON/PN | Classe `ACAO` se o texto B3 tiver marcador de companhia | Sem série ajustada |
| Unit (`…11` de companhia) | Continua `ACAO` na classe canônica; `UNIT` só no snapshot de fundamentos | Não há motor de unit separado |
| FII | Classe `FII` por texto do produto, não pelo sufixo | Qualidade common-core `NOT_READY` |
| FIAGRO | Classe canônica `FII`; classe econômica `FIAGRO`; bloco de risco `BLOCK_FIAGRO` | Não é uma quarta classe canônica |

Bloco de risco: `src/portfolio_risk_blocks.py`, testes em `tests/test_portfolio_risk_blocks.py` (HGLG11, HGBS11, GARE11, KNCR11, SNAG11, ITUB4, CMIG4, WEGE3 como exemplos sintéticos).

## 4. Fundamentos e valuation

| Peça | Estado | Onde |
| --- | --- | --- |
| Carga e as-of de fundamentos de ação | Implementado. Data de referência posterior ao preço vira `FUTURE_DATA` e não é usada | `src/fundamentals.py` `classify_temporal` |
| Escala CVM MIL/MILHAR → reais | Corrigido na ingestão. Registro histórico FQ-UNIT-1 | `src/cvm_materialization.py`, `docs/EQUITY_UNIT_SCALE_CORRECTION.md`, `src/fundamental_quality_status.py` |
| Graham | Fórmula implementada. Entrada e saída `PARTIAL` (cobertura de security) | `src/valuation.py` `compute_graham`. Não se aplica a FII/BDR |
| Bazin | Código existe. Entrada `NOT_READY` (sem DPA oficial). Saída `DIAGNOSTIC_ONLY` | `fundamental_quality_status.py` `BAZIN_INPUT_READINESS` |
| Score B&H em `valuation.py` | Rubrica 0–100 com cobertura. Teto 85% porque shareholder return está ausente | `compute_score`; `BH_SCORE_COVERAGE_READINESS = PARTIAL` |
| Segundo score | `src/company_score.py` `compute_company_score` — outro contrato, também de qualidade fundamental | `config/fundamental_score.yaml` |
| P/VP de FII | Snapshot `READY`, histórico `PARTIAL`, com aviso de defasagem do VP | `src/fii_descriptive_valuation.py` |
| Gordon clássico de FII | Encerrado. `IMPLEMENTATION_ALLOWED = False` | `src/fii_gordon_model_status.py` |
| Perpetuidade g=0 | Implementada como referência, com recorrência não provada. Proíbe fair value e recomendação | `src/fii_zero_growth_perpetuity.py` `model_status` |
| Contrato de qualidade de FII | Taxonomia apenas. `common_core_quality_readiness: NOT_READY` | `config/fii_quality_contract.yaml`, `src/fii_quality_contract.py` |
| API local de fundamentos | Cliente de cache LAN, fora do caminho offline padrão. Config real gitignored | `src/local_fundamentals.py`, `src/fii_local_api.py`, `tools/update_local_fundamentals.py` |

Testes: `tests/test_fundamentals_valuation.py`, `tests/test_cvm_unit_scale.py`, `tests/test_fii_descriptive_valuation.py`, `tests/test_fii_zero_growth_perpetuity.py`, `tests/test_fii_gordon_model_status.py`, `tests/test_fundamental_quality_status.py`, `tests/test_fii_quality_contract.py`.

Esses números descrevem a empresa ou o fundo. Não são gatilho de compra ou venda. `fii_descriptive_valuation.py` (linhas 7–9) proíbe sinal de negociação, ranking e preço-alvo.

## 5. Preço histórico

Implementado no nível da fonte oficial anual. Parcial por ativo. Preço ajustado ausente.

- `src/b3_cotahist.py` — parser COTAHIST 245 caracteres, mercado à vista `010`, `PREULT/100`, `adjustment_status` sempre `RAW`. `FATCOT` é fator de lote, não fator de evento (`total_return_event_status.py`).
- `raw_close_on` exige o pregão exato. `last_raw_close_on_or_before` recua só até `trade_date <= cutoff`. Sem forward-fill oculto, sem preço futuro.
- `src/b3_price_history_status.py` — anos e schema `READY`; cobertura por security `PARTIAL`; `B3_ADJUSTED_PRICE_HISTORY_READINESS = NOT_READY`.
- Download em `tools/update_b3_cotahist.py`: um ZIP por ano em `bvmf.bmfbovespa.com.br`. O cache `data/reference/b3_cotahist/` é gitignored. Não é cotação intradiária nem polling.
- Testes: `tests/test_b3_cotahist.py`, `tests/test_b3_price_history_status.py`.

`portfolio_twr_status.py` ainda descreve janelas 1Y/3Y/5Y como se 2024/2025 estivessem fora do cache. O módulo posterior de COTAHIST declara a continuidade anual resolvida na fonte. As duas frases não podem ser lidas como o mesmo fato. O flag `PORTFOLIO_TWR_READINESS = NOT_READY` permanece de propósito.

## 6. Proventos e renda

Implementado para o caixa pessoal líquido na data de pagamento.

- `src/loaders/b3.py`, `src/income.py` — valor = Valor líquido; categorias DIVIDENDO, JCP, RENDIMENTO, AMORTIZAÇÃO, OUTROS. Período inclusivo.
- `src/total_return_event_status.py` — fonte oficial de evento (ex-date, com-date, bruto, fator) `NOT_READY`. Caixa pessoal `PARTIAL`/`DIAGNOSTIC_ONLY`. BDR `NOT_READY`.
- Yield sobre custo e yield sobre valor atual: `src/analytics.py`. O README exige o denominador no rótulo.
- Zero de proventos colapsado em um único balde `PROV_SEM` é `DIAGNOSTIC_ONLY` (`fundamental_quality_status.py`, `ZERO_PROVENTO_SEMANTICS_READINESS`).
- Testes: `tests/test_income.py`, `tests/test_total_return_event_status.py`.

Provento pessoal não é dividendo por ação do emissor. Bazin não está ligado a esse caixa (`BAZIN_SOURCE` no mesmo status: `personal_proventos_wired_into_fundamental: NO`).

## 7. Eventos societários

Parcial, e específico da conta.

- Único tipo no motor declarativo: `FUND_INCORPORATION` (`src/corporate_actions.py`). Regra versionada: BCFF11→BTHF11, razão 0,80, fração em leilão (`config/corporate_actions.yaml`).
- Quantidade de split, bonificação, subscrição e incorporação: `src/position_history.py`. Status de quantidade de corporate action: `PARTIAL`, com `INCORPORATION_FOLD_STATUS = FOLDED_RESIDUAL_MISMATCH` (24/25 na camada por data).
- A camada legada não recebe o mapeamento de subscrição exercida.
- Preço ajustado continua `NOT_READY`. O TWR de caixa realizado neutraliza split pela fronteira de quantidade, e o próprio contrato diz que isso não equivale a série total-return (`invested_actual_cash_twr_status.py`, caveat `RAW_CLOSE_NOT_ADJUSTED_PRICE`). Teste de fronteira do split sintético SBSP3: `tests/test_invested_actual_cash_twr.py`.
- Testes: `tests/test_corporate_actions.py`, `tests/test_per_asset_xirr_and_bthf_closure.py`.

Direitos `…12/…13/…14` mapeiam para a base `…11` só na camada por data (`base_ticker_of_right`). Não são posição canônica.

## 8. Preço ajustado e retorno total

Três contratos convivem. Promover um não promove os outros.

| Contrato | Flag atual | Calcula? |
| --- | --- | --- |
| Preço ajustado / fator oficial | `B3_ADJUSTED_PRICE_HISTORY_READINESS = NOT_READY` | Não. Arquiteturas A e C rejeitadas em `total_return_event_status.py` |
| TWR de conta cheia e TWR contínuo de preço | `portfolio_twr_status.py`: `is_twr_implementable()` retorna `False`. `PORTFOLIO_TWR_READINESS = NOT_READY` | Não |
| TWR do sleeve, caixa realizado, data de pagamento | `invested_actual_cash_twr_status.py`: `ACTUAL_CASH_TWR_READINESS = READY` para YTD, 1Y e janela curta. Inception `NOT_READY` (gap BTHF11) | Sim, em `src/invested_actual_cash_twr.py` |
| Direito econômico na ex-date | `ECONOMIC_ENTITLEMENT_TWR_READINESS = NOT_READY` | Não |

O caveat `PAYMENT_DATE_NOT_EX_DATE` avisa que o caminho diário pode divergir de uma série ajustada: o preço RAW cai na ex-data e o caixa entra só no pagamento.

## 9. Indicadores técnicos

Ausentes. Busca no código e na documentação não encontrou RSI, MACD, Bandas de Bollinger, IFR, estocástico nem média móvel de preço. A expressão “custo médio móvel” em `src/realized.py` é contabilidade de posição, não indicador gráfico.

`invested_windowed_mwr_status.py` fixa `NO_RISK_ANALYTICS = True` e `NO_DAILY_RETURN_SERIES = True` (volatilidade, Sharpe, Sortino, beta, VaR, drawdown).

## 10. Fluxo de caixa

Implementado para o sleeve de renda variável. Ledger de conta cheia impossível com as fontes atuais.

- `src/cashflows.py` — `CashFlowEvent`, `build_cashflow_events`, `aggregate_daily`. Compra, venda, provento, subscrição, leilão e resgate têm sinal. Transferência interna e corporate action não viram aporte.
- `src/cash_ledger_status.py` — `cash_balance_reconstructable` é não. Bloqueios estruturais: saldo inicial ausente e depósitos/saques banco↔corretora invisíveis. Sem taxa, imposto por negócio nem data de liquidação.
- Testes: `tests/test_cash_ledger_status.py`, `tests/test_cdi_cashflow_benchmark.py`.

## 11. Retornos

| Métrica | Escopo | Estado | Produtor |
| --- | --- | --- | --- |
| Valorização vs preço médio | Posição atual | Implementada. Não é retorno de janela | `portfolio.py` |
| Indicador combinado (valorização + proventos do período) | Nominal | Implementado como acompanhamento | `src/benchmark.py` `combined_over_cost_pct` |
| XIRR vitalício da carteira | Sleeve investido, inception até price date, Actual/365 | Implementado. Solver em `src/returns.py` `xirr` | `src/cdi_benchmark.py` |
| XIRR por ativo, vida inteira | Por ticker, com bloqueio nomeado | Motor em `src/per_asset_xirr.py`. READY só com cadeia completa e raiz | Teste `tests/test_per_asset_xirr_and_bthf_closure.py` |
| XIRR por janela | Sleeve, fronteira de mercado | `invested_windowed_mwr_status.py` marca as janelas MTD…3Y e since-valid-history como `READY`. O docstring do topo do mesmo arquivo ainda diz que `windowed_mwr_ready()` é falso — texto velho | `src/invested_windowed_mwr.py` |
| TWR actual-cash por janela | Sleeve | READY nas janelas curtas citadas acima | `src/invested_actual_cash_twr.py` |
| Resultado realizado | Custo médio móvel, não é lucro tributável | Implementado | `src/realized.py` |
| Resultado econômico por ativo | Compõe realizado, não realizado e renda com guarda de readiness | Parcial | `src/portfolio_asset_economic_result.py` |

`docs/PORTFOLIO_PERFORMANCE_AND_BENCHMARK_PRESENTATION_AUDIT.md` (commit `afa50af`) ainda afirma windowed MWR não pronto, TWR não renderizado e XIRR por ativo 25/25 `NOT_READY`. Os commits `51e35e9`, `4519ba6` e `da62ec7` vieram depois. Esse documento é histórico.

O solver (`returns.py`) recusa série sem troca de sinal e nunca devolve 0% numa falha (`XIRR_NO_SIGN_CHANGE`, `XIRR_NO_BRACKET`, `XIRR_EMPTY`).

## 12. CDI

Implementado, com rótulos de escopo que a auditoria de apresentação exigiu.

- Série diária BCB SGS 12 em `data/reference/cdi.csv`. Acumulado composto inclusivo: `src/cdi.py` `accumulate`.
- CDI casado aos fluxos do sleeve (shadow account) e excesso em pontos percentuais: `src/cdi_benchmark.py`.
- CDI após IR só no cenário FIFO explícito: `src/cdi_after_ir.py`.
- Atualização isolada: `tools/update_cdi.py`. Teste de isolamento: `tests/test_update_cdi_isolation.py`. O `main.py` permanece offline.
- Fora de escopo declarado no README: Selic, IPCA, IBOV, IDIV, IFIX, BDRX.

CDI mede renda fixa. Não é sinal de timing de ação ou FII.

## 13. Alvos, sobreposição e simulador

Implementados como planejamento de carteira, não como alerta de preço.

- `src/portfolio_targets.py` — faixas de peso. `config/portfolio_targets.yaml`.
- `config/portfolio_roles.yaml` — papéis vazios no repositório (`asset_roles: {}`). Papel ausente = `NOT_CONFIGURED`.
- `src/portfolio_overlap.py`, `src/portfolio_consolidation.py`, `src/portfolio_new_capital_matrix.py`.
- `src/purchase_simulation.py` — what-if de preço médio. Não persiste e não recomenda.
- Orquestração sem recálculo: `src/portfolio_analysis_pipeline.py`.

## 14. Relatório e dashboard

Implementados como apresentação local do lote.

- Excel: `src/report_excel.py`, `src/report_model.py`. HTML offline: `src/dashboard_html.py`, `src/dashboard_model.py`. Plotly inline, sem rede.
- A camada de apresentação não deve recalcular métrica. Há auditoria de rótulo em `src/performance_presentation_audit.py`.
- Saúde de arquivo: `src/data_preflight.py`, `docs/DATA_FRESHNESS_VALIDATE_DATA.md`. Status `READY` ali significa checagem estrutural/data, não cobertura de todo ticker nem autorização de fórmula.
- Entrada: `main.py` (CLI). Não há servidor.

## 15. Checagem semântica

### Preço de mercado e preço ajustado

O fechamento COTAHIST e o snapshot B3 são preço observado (`RAW` / `CLOSE`). Série ajustada por provento ou por split retroativo não existe (`adjustment_status = ADJ_RAW` em todo ponto). Split é neutralizado por quantidade numa fronteira de subperíodo, com ressalva explícita de que o intervalo entre ex-data e postagem não é uma série ajustada.

### Retorno de preço e retorno total

Valorização contra preço médio é retorno de preço não realizado da posição, medido do custo, não da janela. O TWR actual-cash soma caixa líquido na data de pagamento ao preço RAW. Ele não é retorno econômico de ex-date nem retorno total ajustado. O indicador combinado soma valorização nominal e proventos do período; `benchmark.py` e o README proíbem chamá-lo de rentabilidade.

### Posição real e watchlist

Há posição corrente, posição zerada, instrumento auxiliar (direito/recibo) e ativo só com provento (`SEM_POSICAO_ATUAL`). Não há lista de observação sem custódia.

### Desempenho da carteira e sinal de investimento

XIRR, TWR de janela, CDI e valorização medem desempenho ou acompanhamento. Scores, P/VP, perpetuidade g=0 e faixas de peso não emitem compra/venda. O simulador é hipótese de quantidade.

### Fundamento e timing

Graham, Bazin, score B&H, P/VP e composição CVM são fundamento ou valuation descritivo. Não há indicador de timing. Usar Graham `PARTIAL` ou Bazin `DIAGNOSTIC_ONLY` como limiar de alerta rebaixaria o contrato de prontidão do projeto.

### Observação histórica e dado futuro

Guardas existentes e testadas:

- Preço: `last_raw_close_on_or_before` ignora `trade_date > cutoff`.
- Fundamento de ação: `reference_date > price_date` → `FUTURE_DATA`.
- Quantidade: evento do dia seguinte fica fora do estoque do dia (`tests/test_position_history.py`).
- TWR de janela: invariante `NO_LOOKAHEAD` declarado e coberto em `tests/test_invested_actual_cash_twr.py`.
- FII CVM: bulk guarda a versão final da competência (`historical_asof_ready: false` em `config/fii_quality_contract.yaml`). Reapresentação pode alterar o valor “conhecido” no passado se alguém tratar o bulk como as-of histórico. O projeto marca isso `PARTIAL` e não promove o score.

### Registro válido e artefato de evento

Preço zero, negativo ou duplicado não vira fechamento (`PRICE_INVALID_ZERO`, `PRICE_INVALID_NEGATIVE`, `PRICE_DUPLICATE_AMBIGUOUS`). Direito/recibo não entra na posição canônica. Fração de incorporação vai a leilão, não a quantidade corrente. `FATCOT` não é fator de ajuste. Provento no extrato de movimentações é ignorado para não duplicar o caixa (`EVENTO_PROVENTO_IGNORADO` no fluxo de caixa).

### Alinhamento de datas que permanece incorreto se for reusado às cegas

- Pagamento versus ex-date no TWR actual-cash.
- Postagem de split/bonificação um ou dois pregões depois da ex-data, com preço RAW já refletindo o evento.
- Incorporação BTHF11: quantidade desde 2024-12-13 e listagem de preço em 2024-12-16; inception do TWR fica `NOT_READY`.
- Resíduo 24/25 na dobra da incorporação na camada por data.
- Uma data só no extrato B3 (sem liquidação D+2) no ledger de caixa, que por isso permanece `NOT_READY`.
- Idade de fundamento: o limiar de stale está desligado (`stale_threshold_days=None`); a idade fica visível e o dado não vira `STALE` sozinho.

## 16. O que está depreciado ou só documentado

- `fundamentals.mark_stale_if_needed` — alias com nome deprecado de `classify_temporal`.
- Investidor10 como autoridade de posição — substituído pela B3; loader, CSV e extrator JS permanecem para diagnóstico (`CHECKPOINT.md`).
- Gordon clássico — bloqueado de propósito, não é código morto esquecido.
- Cabeçalho de `invested_windowed_mwr_status.py` e a auditoria de apresentação de `afa50af` — texto anterior às promoções de janela.
- `README.md` (por volta das linhas 380–388) ainda diz que XIRR, TWR e retorno total de 12 meses não são calculados. Isso vale para o indicador combinado da fase 2. Não vale para `returns.py`, `cdi_benchmark.py`, `per_asset_xirr.py`, `invested_windowed_mwr.py` e `invested_actual_cash_twr.py`.
- `CHECKPOINT.md` aponta `HEAD=32cb02a` e `PROJECT_STATE=PAUSED`. O HEAD auditado é `da62ec7`.
