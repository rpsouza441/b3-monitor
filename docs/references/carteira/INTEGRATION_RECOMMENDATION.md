# Recomendação de integração — monitoramento × carteira

Auditoria somente leitura do repositório `projecao-carteira` em `da62ec7`.
O serviço alvo é um processo Java/Spring Boot, separado, para acompanhar preço
de ações, units, FIIs e FIAGRO. Esta nota compara quatro desenhos. Sobreposição
de assunto não é motivo para acoplar.

## 1. Fatos que condicionam a escolha

- O carteira-analyzer é um lote local. Entrada `main.py`. Saída Excel/HTML. `src/` não abre rede.
- Não existe API, pacote instalável nem banco compartilhado.
- Dados de custódia ficam em `data/input/`, de propósito fora do Git (`README.md`, `.gitignore`).
- Vários retornos estão implementados e testados, com contratos estreitos (sleeve, caixa na data de pagamento, preço RAW). Outros estão congelados em `NOT_READY` (preço ajustado, TWR de conta cheia, ledger de caixa, Gordon, qualidade de FII, Bazin).
- Documentos e até docstrings ficaram para trás dos flags (`CHECKPOINT.md`, `README.md`, topo de `invested_windowed_mwr_status.py`, auditoria de apresentação em `afa50af`).
- O handoff ainda marca o projeto como pausado, com a data do fechamento B3 por metadata como pendência operacional.
- Indicador técnico, polling, fila, WAHA e limiar de compra/venda não existem aqui.

Um monitor de cotação que importe esses retornos para “enriquecer o sinal” herda o risco de chamar de alerta o que o próprio código se recusa a chamar de retorno.

## 2. Alternativas

Escala usada abaixo: favorável, neutro ou desfavorável para o serviço de monitoramento.

### A. Serviço de monitoramento independente

O Spring Boot coleta cotação, calcula os indicadores dele, avalia regra e notifica. Não chama este repositório em runtime. Pode copiar, como especificação, o formato de ticker, a regra “sufixo 11 não é FII”, a distinção unit versus FII, a rota FIAGRO e a definição de fechamento RAW as-of.

| Critério | Avaliação | Por quê |
| --- | --- | --- |
| Acoplamento | Favorável | Nenhum deploy, schema ou flag de prontidão compartilhado |
| Estabilidade de runtime | Favorável | O monitor não depende de um lote pausável nem de arquivo privado ausente |
| Maturidade do que seria integrado | Neutro | Não integra código incompleto; também não ganha o que já está pronto |
| Correção financeira | Favorável | O alerta fica em cima de preço declarado. Não herda XIRR, TWR nem Graham |
| Dono do dado | Favorável | Cotação e histórico de alerta nascem no monitor. Custódia continua neste projeto |
| Deploy | Favorável | Um serviço, uma agenda. O analyzer segue sendo comando local |
| Cobertura de teste | Neutro | Os testes daqui não protegem o monitor. O monitor testa o próprio indicador |
| Manutenção | Favorável | Dois ciclos de vida. A divergência de contratos do analyzer não vaza para o alerta |

Custo consciente: o coletor de mercado é trabalho novo. O COTAHIST deste repo é arquivo anual, não feed. Duplicar o parser fixed-width só vale se o monitor também arquivar COTAHIST. Para cotação do dia, a fonte será outra, e o parser atual não economiza esse caminho.

### B. Monitor consumindo uma API somente leitura deste projeto

Exigiria construir a API. Ela não está no código.

| Critério | Avaliação | Por quê |
| --- | --- | --- |
| Acoplamento | Desfavorável | O monitor passa a depender do lote, do `price_date`, dos caches e do significado de cada flag |
| Estabilidade de runtime | Desfavorável | Sem metadata, o preço canônico volta inteiro ao Investidor10. Um alerta em cima disso muda de fonte em silêncio de configuração |
| Maturidade | Desfavorável | CLI local, documentação de estado divergente, projeto marcado como pausado no handoff |
| Correção financeira | Desfavorável | A API teria de esconder meia dúzia de “retornos” que não são preço. Um campo a mais no JSON vira sinal errado |
| Dono do dado | Desfavorável | Para contextualizar posição, a API lê arquivo privado. Isso fura o desenho “sem rede e sem upload” do README, mesmo em localhost, no momento em que o monitor é outro processo com outra política de log |
| Deploy | Desfavorável | Dois processos, contrato versionado, e o analyzer não tem servidor para operar |
| Cobertura de teste | Neutro | Os testes financeiros existem; não existe teste de API |
| Manutenção | Desfavorável | Cada promoção de readiness (como a do TWR actual-cash) vira mudança de contrato do monitor |

Leitura pontual, gerada pelo usuário, de um JSON de posição já rotulado, pode existir no futuro como arquivo que ele copia. Isso não é a alternativa B. B é o monitor depender deste processo para funcionar.

### C. Biblioteca financeira compartilhada

Os cálculos corretos estão em módulos Python acoplados a movimento B3, corporate action desta conta e arquivos locais. Não há fronteira de biblioteca.

| Critério | Avaliação | Por quê |
| --- | --- | --- |
| Acoplamento | Desfavorável | Extrair `returns.py`, `position_history.py` e `b3_cotahist.py` arrasta custódia, caixa e evento |
| Estabilidade de runtime | Neutro | Biblioteca não derruba o monitor se for pura; o problema é o que ela calcula |
| Maturidade | Desfavorável | Flags contraditórios entre módulos vizinhos. Congelar isso numa lib publica a contradição |
| Correção financeira | Desfavorável | O que está correto é específico de sleeve e de data de pagamento. O que o monitor precisa (indicador sobre fechamento) não está na lib |
| Dono do dado | Neutro | Código compartilhado não deveria carregar `data/input`, mas hoje o cálculo assume esse ledger |
| Deploy | Desfavorável | Python de um lado, Java do outro. Ponte JNI, processo ou reescrita duplica a lib na prática |
| Cobertura de teste | Favorável no Python | A suíte protege o Python. Não protege um port Java |
| Manutenção | Desfavorável | Dois consumidores com perguntas diferentes (desempenho da conta versus alerta de preço) |

Uma nota de especificação curta (RAW versus ajustado, as-of, classe) cumpre o que uma lib daria ao monitor, sem o custo de extração.

### D. Fundir o monitor neste repositório

| Critério | Avaliação | Por quê |
| --- | --- | --- |
| Acoplamento | Desfavorável | Alerta, fila e WAHA passariam a viver ao lado da posição canônica |
| Estabilidade de runtime | Desfavorável | Polling e WhatsApp no mesmo processo que reconcilia custódia. Falha de rede no lugar que o projeto manteve offline |
| Maturidade | Desfavorável | O produto atual é lote de análise. O handoff pede para não abrir outra fonte de cotação nem mudar fórmula |
| Correção financeira | Desfavorável | O código proíbe recomendação em valuation, simulador, qualidade de FII e overlap. Um motor de compra/venda no mesmo binário apaga essa fronteira |
| Dono do dado | Desfavorável | Histórico de alerta e segredo de WAHA ficariam no repositório da custódia |
| Deploy | Desfavorável | O analyzer não é serviço. Fundir obriga a transformá-lo em um |
| Cobertura de teste | Neutro | Dá para testar no mesmo pytest; o tipo de teste (lote determinístico) não cobre fila e canal |
| Manutenção | Desfavorável | Ciclo de pregão diário mistura com auditoria de CVM e de custo médio |

`CHECKPOINT.md` lista, entre o que não fazer ao retomar: inventar `price_date`, adicionar API ou outra fonte de cotação, mudar fórmula. Fundir o monitor faz exatamente a abertura de fonte e de runtime que o handoff adia.

## 3. Esforço por componente

Julgamento de engenharia para um desenvolvedor que já conhece B3, assumindo a alternativa A e uma única fonte de cotação diária de fechamento. Não é medição de velocidade deste repositório.

| Componente | Reaproveitamento | Esforço novo |
| --- | --- | --- |
| Identidade dos 23 tickers e classe (ação, unit, FII, FIAGRO) | Especificação de `normalization.py`, `asset_classification.py`, `fii_local_api_reconcile.py` | 2–4 dias para tabela explícita dos 23, com classe conferida na fonte do monitor. Não inferir pelo sufixo |
| Coleta de fechamento | Contrato RAW / as-of / sem forward-fill. O download anual COTAHIST não serve de feed | 2–4 semanas (cliente, calendário, retentativa, armazenamento) |
| Frescor | Política “idade desconhecida não vira zero” de `data_preflight.py` | 1 semana |
| Agenda | Nenhuma | 3–5 dias |
| Indicadores sobre fechamento RAW | Nenhum cálculo reutilizável | 1–2 semanas para um conjunto pequeno, com teste de janela e de pregão faltante |
| Motor de regras e limiares | Nenhum. Não partir de `portfolio_targets.py` nem de `valuation.py` | 1–2 semanas |
| Fila e histórico de alerta | Nenhum | 1–1,5 semana |
| WAHA | Nenhum | cerca de 1 semana, mais o tempo de homologação do canal |
| Dashboard do monitor | O HTML atual é outro produto | 2–3 semanas para uma tela operacional |
| Contexto de posição (peso, quantidade, PM) | Existe no analyzer, sem API | Fora da primeira entrega. Se entrar depois, arquivo gerado pelo usuário, somente leitura |
| Valuation como anotação | Graham `PARTIAL`, P/VP de FII com lag, Bazin não pronto, qualidade de FII não pronta | Fora da primeira entrega |
| Preço ajustado / total return para o gráfico do alerta | Explicitamente `NOT_READY` aqui | Projeto à parte. Não estimar como reúso |

Ordem de grandeza da primeira entrega independente (coleta diária, frescor, poucos indicadores, regra, fila, WAHA, tela simples): **cerca de 8–14 semanas**. Entrar por B, C ou D acrescenta a construção da fronteira e o risco de semântica, sem encurtar o coletor.

## 4. Decisão

**INDEPENDENT**

O monitor nasce separado. Leva por escrito três regras deste repositório: ticker conservador, classe que não usa o sufixo, fechamento RAW com data do pregão e sem preencher o futuro. Não chama `main.py`, não lê `data/input/` e não reimplementa XIRR, CDI casado a fluxo, TWR, Graham nem custo médio.

Uma exportação somente leitura de posição, feita pelo próprio analyzer e lida pelo monitor como contexto, fica possível depois que o alerta de preço estiver estável. Ela não é pré-requisito e não existe hoje.

## 5. Encerramento

### 1. Capacidades que devem ser reutilizadas

Como especificação, não como runtime:

- Gramática de ticker e extração pelo primeiro `" - "` (`src/normalization.py`).
- Classe canônica ACAO/FII/BDR, com unit tratado como ação e FIAGRO com rota econômica própria (`src/asset_classification.py`, `src/fii_local_api_reconcile.py`).
- Fechamento bruto, as-of `trade_date <= cutoff`, sem forward-fill e sem preço zero/negativo/duplicado (`src/b3_cotahist.py`).
- Separação entre posição corrente, posição zerada e direito/recibo (`src/canonical_portfolio.py`).
- Quantização monetária em `Decimal` se o monitor exibir real (`src/money.py`).
- O hábito de publicar status ao lado do número (`READY` / `PARTIAL` / `DIAGNOSTIC_ONLY` / `NOT_READY`), em vez de transformar ausência em zero.

### 2. Cálculos que não devem ser duplicados

Se o assunto for desempenho ou valuation, o produtor canônico já está aqui e um segundo implementação vai divergir:

- Solver XIRR Actual/365 (`src/returns.py`) e o XIRR do sleeve (`src/cdi_benchmark.py`, `src/per_asset_xirr.py`, `src/invested_windowed_mwr.py`).
- Capitalização composta do CDI (`src/cdi.py`) e o cenário FIFO após IR (`src/cdi_after_ir.py`).
- TWR do sleeve com caixa na data de pagamento (`src/invested_actual_cash_twr.py`).
- Custo médio realizado (`src/realized.py`) e quantidade por data (`src/position_history.py`).
- Graham e a escala CVM aplicada uma vez na ingestão (`src/valuation.py`, `src/cvm_materialization.py`).
- Sinais de fluxo de caixa do sleeve (`src/cashflows.py`), inclusive “provento no extrato de movimentação não entra de novo”.

O monitor de preço não precisa de uma segunda cópia desses cálculos. Também não precisa deles para alertar cotação.

### 3. Dívida técnica e risco de correção

- `README.md` e `CHECKPOINT.md` não descrevem o HEAD atual. O README nega XIRR/TWR que o código calcula sob outro nome e outro escopo.
- `invested_windowed_mwr_status.py` abre dizendo que a janela não está pronta e termina com `windowed_mwr_ready() == True`.
- `portfolio_twr_status.py` ainda fala em buraco de cache em 2024/2025; `b3_price_history_status.py` marca a continuidade anual como resolvida. O TWR contínuo segue `NOT_READY` pelos outros bloqueios.
- A auditoria de apresentação (`afa50af`) está superada pelos três commits de performance seguintes.
- Preço ajustado oficial: `NOT_READY`. Usar `FATCOT` como fator de evento está errado.
- TWR actual-cash reconhece renda no pagamento, não na ex-data, e neutraliza split por fronteira de quantidade. O caminho diário não é série ajustada.
- Incorporação dobrada com resíduo 24/25 (`FOLDED_RESIDUAL_MISMATCH`). Inception do TWR bloqueada pelo gap de preço do BTHF11.
- Ledger de caixa da conta: impossível (sem saldo inicial e sem depósito/saque).
- Bulk de FII não é as-of versionado (`historical_asof_ready: false`).
- Bazin sem DPA oficial. Qualidade de FII sem nota. Gordon bloqueado. Dois scores de ação (`valuation.compute_score` e `company_score.py`).
- Subscrição exercida mapeada na camada por data e ainda não mapeada na reconstrução legada.
- Preço B3 de runtime depende de metadata que o handoff diz ausente; o rollback é o arquivo inteiro para o Investidor10.
- Suíte cheia desta sessão: 1321 passed e 638 errors de setup (`PermissionError` no temp do Windows). Recorte financeiro com `--basetemp` local: 274 passed, 0 failed (`test_b3_cotahist`, `test_cdi`, `test_corporate_actions`, `test_asset_classification`, `test_position_history`, `test_invested_actual_cash_twr`, `test_invested_windowed_mwr`, `test_fundamentals_valuation`, `test_canonical_portfolio`, `test_total_return_event_status`, `test_portfolio_twr_status`, `test_per_asset_xirr_and_bthf_closure`, `test_money`). Isso não reproduz o “0 errors” histórico da suíte inteira.

### 4. Capacidades de monitoramento ausentes

Coleta de cotação para alerta, agenda, SLA de frescor de quote, indicadores técnicos, motor de regras, limiar de compra e venda, fila, histórico de alerta, WAHA e dashboard operacional. O dashboard HTML e o preflight de arquivos não ocupam esses lugares.

### 5. Arquitetura recomendada

Serviço independente (alternativa A). Especificação emprestada: ticker, classe, fechamento RAW as-of. Sem API, sem biblioteca compartilhada e sem fusão neste repositório.

### 6. Esforço

Primeira entrega do monitor, sem valuation e sem posição: cerca de 8–14 semanas. Detalhe por componente na seção 3. Preço ajustado e total return não entram nessa conta.

### 7. Decisão

**INDEPENDENT**
