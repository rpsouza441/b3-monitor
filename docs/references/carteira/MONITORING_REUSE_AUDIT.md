# Auditoria de reúso para monitoramento de preços

Somente leitura. HEAD `da62ec7`. Nenhum código de cálculo, dado, roadmap ou serviço externo foi alterado.

O projeto auditado é um analisador local de carteira em Python (CLI, Excel e HTML offline). O serviço desejado é outro produto: Java/Spring Boot, sempre ligado, para preço de ações, units, FIIs e FIAGRO na B3, com regra e alerta. A sobreposição de domínio não cria uma fronteira de integração pronta.

## 1. Estado encontrado

| Fonte | O que diz | Como tratar |
| --- | --- | --- |
| `README.md` | Fases 1–4 concluídas; XIRR/TWR fora; app 100% local | Desatualizado quanto a retorno. Ainda vale a política de privacidade e o significado da valorização |
| `CHECKPOINT.md` | Pausado em `32cb02a`; posição B3 pronta; preço B3 bloqueado por `price_date` | Handoff útil e atrasado em relação ao git |
| `docs/PORTFOLIO_PERFORMANCE_AND_BENCHMARK_PRESENTATION_AUDIT.md` | Windowed MWR e TWR não promovidos; XIRR por ativo 25/25 bloqueado | Verdadeiro em `afa50af`. Superado por `51e35e9`, `4519ba6`, `da62ec7` |
| Código `src/*_status.py` | Vários contratos de prontidão, alguns congelados de propósito | Fonte de verdade, lendo o flag e não o parágrafo antigo do mesmo arquivo |
| Testes | 98 módulos em `tests/`. Fixtures sintéticas (`tests/conftest.py`) | Ver seção 6 |

Não há `ROADMAP.md`, `REQUIREMENTS.md`, `STATE.md`, schema SQL, `pyproject.toml` nem servidor HTTP. `src/` é offline. Rede fica em `tools/update_*.py` (CDI, COTAHIST anual, CVM, Tesouro, API LAN de fundamentos).

Git: `main` está 8 commits à frente de `origin/main`. O último é `feat(twr): INVESTED_SECURITIES_ACTUAL_CASH_TWR`. Worktree com apenas `.kiro/` não rastreado.

## 2. Universo dos 23 ativos

O repositório não configura essa lista. `config/portfolio_roles.yaml` tem `asset_roles: {}`. A cobertura de cotação e de fundamento de cada ticker depende de caches gitignored (`data/reference/b3_cotahist/`, CVM, API local) e de arquivos privados em `data/input/`. Esta auditoria não abriu esses arquivos e não afirma que os 23 estão na carteira nem que têm série contínua.

O que o código consegue representar:

| Ticker | Forma | Representação estrutural | Evidência específica no repo |
| --- | --- | --- | --- |
| WEGE3, ITUB4, EGIE3, SBSP3, TIMS3, CMIG4 | Ação | Ticker válido; classe `ACAO` se o texto B3 for de companhia | WEGE3, ITUB4 e CMIG4 aparecem em teste sintético de bloco de risco. EGIE3 e SBSP3 aparecem como caso de fronteira de split no teste de TWR. TIMS3 não aparece |
| BPAC11, TAEE11, SAPR11 | Unit | Ticker válido. Classe canônica continua `ACAO`, não FII. `UNIT` existe só no snapshot de fundamentos | Docstring de `asset_classification.py`. Teste `test_normalize_unit` usa SAPR11 sintético. TAEE11 só no comentário |
| HGLG11, VRTA11, BTLG11, RBVA11, HGBS11, GARE11, ALZR11, MXRF11, BTHF11, GGRC11, XPLG11, KNCR11 | FII | Ticker válido; classe `FII` pelo texto do produto | Vários aparecem em testes sintéticos (HGLG11, HGBS11, GARE11, ALZR11, KNCR11, MXRF11, BTHF11). VRTA11, BTLG11, RBVA11, GGRC11 e XPLG11 não têm ocorrência própria |
| SNAG11, KNHY11 | FIAGRO | Ticker válido. Classe canônica `FII`. Classe econômica `FIAGRO` se a API/CVM disser isso | SNAG11 tem teste de rota FIAGRO e de bloco `BLOCK_FIAGRO`. KNHY11 não aparece |

Todos os 23 casam o validador `src/normalization.py` (`[A-Z]{4}\d{1,2}`). Isso é capacidade de nome, não prova de dado.

Contexto de carteira que existiria só com o ledger privado do usuário:

- BTHF11 tem regra pública de incorporação BCFF11→BTHF11 (`config/corporate_actions.yaml`).
- `position_history_status.py` cita subscrição de GARE e MXRF como prova da camada por data.
- O handoff fala em 25 posições (10 ações, 14 FIIs, 1 BDR). A forma bate com 9 ações/units + 14 FIIs/FIAGRO + um BDR (o único mapeamento explícito é NVDC34), mas o repositório não publica essa lista. Não usar a coincidência de contagem como inventário.

## 3. O que melhora a leitura de um alerta — e o que não deve entrar no alerta

Útil como contexto, fora do caminho quente do preço:

- Classe (ação, unit-como-ação, FII, FIAGRO econômico). Evita tratar BPAC11/TAEE11/SAPR11 como FII e SNAG11/KNHY11 como tijolo.
- Preço RAW as-of, com a data do pregão realmente usada e o lag. O projeto já separa pregão exato de “último pregão até a data”.
- Posição, peso e preço médio, se um dia houver um extrato somente leitura gerado pelo usuário. Isso explica “já tenho” versus “não tenho”. Não existe essa exportação hoje.
- P/VP de FII e Graham de ação, como anotação com o status (`PARTIAL`, `DIAGNOSTIC_ONLY`, `NOT_APPLICABLE`). Complemento de leitura, não limiar.

Inseguro reutilizar como regra de compra/venda:

- Indicador combinado, yield do período e valorização contra preço médio.
- XIRR vitalício, XIRR de janela e TWR actual-cash. São desempenho da conta, na data de pagamento, no sleeve investido.
- Score B&H e `company_score`. Cobertura parcial; shareholder return ausente; não é timing.
- Bazin. Entrada oficial `NOT_READY`.
- Gordon de FII. Implementação proibida.
- Perpetuidade g=0. Referência com recorrência não provada; fair value proibido.
- Contrato de qualidade de FII. `NOT_READY`; não há nota.
- Faixas de `portfolio_targets`. São peso da carteira.
- CDI. Benchmark de renda fixa do fluxo do sleeve, não gatilho de ação.

Não há indicador técnico para reutilizar. Qualquer média, RSI ou banda no serviço novo nasce lá, em cima de uma série cujo tipo esteja escrito (RAW de fechamento, não “ajustado”).

## 4. Integração somente leitura, do jeito que o repositório está

Não há dono de escrita compartilhado porque não há serviço. O dono dos arquivos canônicos é o processo local:

- Posição, quantidade, custo e classe: extratos B3 em `data/input/` (gitignored).
- Preço corrente configurável: snapshot B3, com rollback inteiro para Investidor10 se a metadata de data faltar (`CHECKPOINT.md`). Sem fallback híbrido por ativo.
- Caches públicos: escritos pelos `tools/update_*.py`.
- Saída: `output/resumo.xlsx` e `output/dashboard.html`, também gitignored.

`main.py` lê e calcula. Não expõe consulta. Um Spring Boot não importa `src/`. Não há pacote instalável. Chamar o CLI por processo exigiria os arquivos privados na máquina do monitor e misturaria um lote pausável com um serviço de alerta.

Conclusão desta fase: dá para copiar contratos (formato de ticker, classe, preço RAW, as-of, o que cada retorno significa). Não dá para consumir uma API somente leitura, porque ela não existe. Construí-la seria projeto novo em cima de um batch local cuja documentação de prontidão ainda diverge entre módulos.

## 5. Lacunas de monitoramento

| Capacidade | No repositório | Trabalho novo |
| --- | --- | --- |
| Coleta de mercado para alerta | COTAHIST anual, ZIP oficial, filtro local depois do download. Não é quote por ticker nem intradiário | Coletor do serviço, com fonte e calendário de pregão próprios |
| Agenda de polling | Inexistente. Atualizadores são comando manual | Scheduler do serviço |
| Frescor de cotação | `data_preflight.py` classifica arquivo/cache (`STALE`, `UNKNOWN`, sem idade inventada). Não é SLA de quote | Relógio de cotação do monitor: pregão, atraso máximo, pregão fechado |
| Indicador técnico | Inexistente | Implementar no serviço, declarado sobre preço RAW |
| Motor de regras | Inexistente. Há classificação contábil e faixa de peso | Motor de alerta, separado de valuation |
| Limiar de compra/venda | Inexistente. Simulador e alvos recusam recomendação | Limiares do serviço, sem reaproveitar peso nem Graham |
| Fila de notificação | Inexistente | Nova |
| Histórico de alerta | Inexistente | Novo |
| WAHA | Inexistente. Nenhuma integração WhatsApp | Nova |
| Dashboard de monitoramento | HTML de carteira, offline, gerado no lote | Tela nova. O dashboard atual não é esse produto |

O que não é lacuna, e não deve ser reescrito para “completar” o monitor: solver de XIRR, capitalização de CDI, parser COTAHIST, reconstrução de quantidade, Graham, custo médio realizado. O monitor de preço não precisa dessas contas para disparar alerta de cotação. Se um dia precisar de desempenho da carteira, o caminho honesto é ler um número já rotulado por este projeto, não recalcular.

## 6. Testes executados

Comando da suíte, no virtualenv do projeto:

`.venv\Scripts\python.exe -m pytest -q --tb=line`

Resultado: **1321 passed, 39 warnings, 638 errors**, em 649 s. Exit code 1.
Os erros amostrados são `PermissionError: [WinError 5] Acesso negado` em
`C:\Users\Rodrigo\AppData\Local\Temp\pytest-of-Rodrigo`, na criação do diretório
temporário do pytest (`ERROR at setup`). Não houve falha de asserção no resumo.
O Python do sistema (`py -3`) não tem pytest; o venv tem.

Segunda execução, com `--basetemp .audit-tmp`, só nos módulos de preço COTAHIST,
CDI, corporate action, classe, quantidade por data, TWR actual-cash, MWR de
janela, valuation, posição canônica, status de total-return, status de TWR de
carteira, XIRR por ativo e `money.py`: **274 passed, 38 warnings**, em 576 s.
Exit code 0. Os warnings são estilo ausente do openpyxl e `PermissionError` ao
gravar `.pytest_cache` (o resultado dos testes não dependeu desse cache).
O bloqueio da suíte cheia continua sendo o ACL do temp padrão do Windows.

Histórico interno do projeto, não reexecutado aqui: a auditoria de apresentação
registrou 1815 passed em `afa50af`, e commits posteriores citam contagens da
ordem de 1854 testes Python e 17 testes JavaScript do extrator Investidor10.
A suíte desta sessão não reproduziu “zero erros” por causa do diretório temporário.

## 7. Leitura curta para quem for desenhar o serviço

Reutilize o vocabulário e as proibições. Não reutilize o processo.

O monitor pode dizer: fechamento RAW, data do pregão, lag, classe do instrumento.
O monitor não deve dizer: retorno total, preço ajustado, barato por Graham,
qualidade de FII, nem “a carteira rende X” a partir de uma média de preço.
