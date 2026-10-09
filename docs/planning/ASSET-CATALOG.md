# Catálogo documental dos 23 ativos

Proposta reconciliada para revisão humana; não é cadastro ativado. Fonte: [CSV completo](../references/brapi/COBERTURA.csv), [tipos Brapi](../references/brapi/SCHEMA.md) e [relatório Python](../references/carteira/MONITORING_REUSE_AUDIT.md), §2. Todas as 23 linhas foram lidas: tickers únicos, solicitado=retornado, changed=false, HTTP 200, BRL, dados_validos=true, sem erro. A flag do CSV não substitui elegibilidade.

| Linha¹ | Ticker | Classe proposta | assetType / subType Brapi | Cotação auditada | Histórico diário medido no Free |
|--------|--------|-----------------|--------------------------|------------------|---------------------------------|
| 1 | WEGE3 | Ação | stock / stock | Sim | Não; recusa de 1y não prova cobertura 3mo |
| 2 | BPAC11 | Unit | stock / unit | Sim | Não |
| 3 | ITUB4 | Ação | stock / stock | Sim, sandbox | 64 pontos 3mo sandbox; não prova direito Free |
| 4 | EGIE3 | Ação | stock / stock | Sim | Não |
| 5 | TAEE11 | Unit | stock / unit | Sim | Não |
| 6 | SBSP3 | Ação | stock / stock | Sim | Não |
| 7 | SAPR11 | Unit | stock / unit | Sim | Não |
| 8 | TIMS3 | Ação | stock / stock | Sim | Não |
| 9 | CMIG4 | Ação | stock / stock | Sim | Não |
| 10 | HGLG11 | FII | fund / fii | Sim | 64 pontos; dia corrente parcial |
| 11 | VRTA11 | FII | fund / fii | Sim; stale header relatado | Não |
| 12 | BTLG11 | FII | fund / fii | Sim | Não |
| 13 | RBVA11 | FII | fund / fii | Sim | Não |
| 14 | HGBS11 | FII | fund / fii | Sim | Não |
| 15 | GARE11 | FII | fund / fii | Sim | Não |
| 16 | ALZR11 | FII | fund / fii | Sim | Não |
| 17 | MXRF11 | FII | fund / fii | Sim | Não |
| 18 | BTHF11 | FII | fund / fii | Sim | Não |
| 19 | GGRC11 | FII | fund / fii | Sim | Não |
| 20 | XPLG11 | FII | fund / fii | Sim | Não |
| 21 | KNCR11 | FII | fund / fii | Sim | Não |
| 22 | KNHY11 | FII provisório; confirmação oficial pendente | fund / fii | Sim; stale header relatado | Não |
| 23 | SNAG11 | FIAGRO | fund / fi-agro | Sim | 64 pontos; dia corrente parcial |

¹ Linha de dados, sem cabeçalho. Total pela Brapi: 6 ações, 3 units, 13 FIIs, 1 FIAGRO. Não há prova de histórico COTAHIST/fundamentos para os 23; o relatório Python não inspecionou caches nem dados privados.

## KNHY11 e mapeamento

Brapi: fund/fii e nome “Kinea High Yield CRI Fundo De Investimento Imobiliario - FII”. Python §2: agrupado com SNAG11 como FIAGRO, embora declare que KNHY11 não aparece no código/testes e que classe econômica exige API/CVM. O pacote não contém documento oficial do fundo/CVM/B3. Adotar FII provisório, rejeitar FIAGRO automático e manter Q-15 como bloqueio de ativação desse instrumento/classificação. Os demais 22 seguem revisão própria; catálogo completo não está aprovado.

Namespaces distintos: Python ACAO inclui unit; FII inclui FIAGRO imobiliário, com classe econômica separada. Monitor preserva Unit e FIAGRO explicitamente. Importação exige mapeamento por identidade. ON/PN, ISIN/CNPJ, datas de validade, renames e ticker reutilizado exigem evidência adicional, sem inferência do sufixo.

Preços do CSV não são alvos de compra/venda nem dados atuais de produção. Headers stale vêm do RESULTADO, não de coluna CSV: são degradação separada que exige política explícita.

## Ativação independente — proposta crítica 2026-10-06

KNHY11 permanece QUARANTINED_CLASSIFICATION_PENDING até evidência oficial; isso não suspende os demais ativos que tenham identidade/políticas/elegibilidade aprovadas individualmente. Um subconjunto válido nomeado pode satisfazer gates downstream somente com aprovação humana explícita de escopo/dependências. CAT-01/cobertura23/23 e conclusão integral da fase1 permanecem pendentes, sem forçar checkboxes ou avanço GSD. A escolha de subconjunto não resolve a divergência oficial nem autoriza consultas externas.
