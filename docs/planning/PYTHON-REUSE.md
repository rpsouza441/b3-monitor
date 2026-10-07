# Registro de reaproveitamento do projecao-carteira

Base: [FINANCIAL_COMPONENTS](../references/carteira/FINANCIAL_COMPONENTS.md), §§1–16, [MONITORING_REUSE_AUDIT](../references/carteira/MONITORING_REUSE_AUDIT.md), §§3–6, [INTEGRATION_RECOMMENDATION](../references/carteira/INTEGRATION_RECOMMENDATION.md), §§4–5; HEAD da62ec7. Estados são AUDIT_REPORTED; nenhum módulo upstream foi inspecionado aqui. Prontidão do cálculo, dados e integração são dimensões separadas.

Nenhum exportador JSON/API está pronto. “Decisão v1” separa A/FIN-01 (consumidor Java versionado/testado com fixtures sintéticos) de B/FIN-06 (produtor/exportador Python real, condicionado a autorização separada). CONSUMER_VERIFIED_SYNTHETIC não é INTEGRATION_VERIFIED_REAL. FIN-06 permanece PRODUCER_ABSENT / BLOCKED_EXTERNAL_AUTHORIZATION e impede concluir reutilização financeira real/fase 7 completa; não bloqueia o MVP de preço. Java não porta fórmulas nem chama CLI.

| Componente | Prontidão relatada | Decisão v1 / uso | Fonte |
|------------|--------------------|-----------------|-------|
| Ticker/classes | Implementado/testado; sem flag READY global afirmado | Reusar especificação, não cálculo; mapear ACAO/FII para Unit/FIAGRO explicitamente | P2 §§2–3 |
| COTAHIST RAW as-of | Anos/schema READY; security PARTIAL | HIS-04: close bruto, trade_date, cutoff/lag, PREULT/100, 010, arquivo/revisão/digest, cobertura; não anunciar OHLCV completo | P2 §5/§15 |
| Preço ajustado/fatores | NOT_READY | Excluir série ajustada; FATCOT não é fator de evento | P2 §§5/8 |
| Fundamentos CVM ação/as-of/escala | Implementado; coverage não universal; FUTURE_DATA se posterior ao preço | Contexto público candidato: valor, unidade/escala já aplicada, reference_date, fonte/revisão, status; sem dependência de custódia | P2 §4/§15 |
| Graham | Entrada/saída PARTIAL; ação, não FII/BDR | Contexto candidato: valor/insumos públicos as-of, moeda, versão, limitações; não preço-alvo | P2 §4 |
| P/VP FII | Snapshot READY, histórico PARTIAL, VP defasado | Contexto candidato: razão, preço/data/base, VP/data, origem, lag; sem ranking/target | P2 §4 |
| Score B&H compute_score | PARTIAL, teto de cobertura 85%, shareholder return ausente | Diagnóstico candidato: 0–100, rubrica/versão, coverage; sem qualidade completa | P2 §4 |
| company_score | Implementado, contrato distinto; prontidão global não especificada | Desabilitado até rubrica/readiness próprias; não alias de B&H | P2 §4 |
| Bazin | Entrada NOT_READY, saída DIAGNOSTIC_ONLY; sem DPA oficial | Só estado/limitação, sem valor utilizável inicial | P2 §4/§6 |
| Perpetuidade FII g=0 | Referência implementada, recorrência não provada; flag global não declarado | Fora da lista numérica inicial; sem READY/fair value por inferência | P2 §4 |
| Gordon FII | IMPLEMENTATION_ALLOWED=False | Excluído; não solicitar implementação | P2 §4/§16 |
| Qualidade common-core FII | NOT_READY, taxonomia sem nota | Só estado/limitação; sem score fictício | P2 §4/§15 |
| Clientes/snapshots scraper | Sem as-of confiável, ausência→zero/valor antigo | FIN-05 desabilitado no HTTP atual; futuro artefato exige contrato próprio | S1 cache 4–7; P2 §4 |
| Posição/quantidade/PM/peso/valorização | Implementado em escopos estreitos; preço B3 depende de metadata; incorporação com resíduo | Excluir dados pessoais; valorização vs PM não é retorno de janela | P2 §§1/7/11 |
| Proventos pessoais/yield/combinado | Pagamento; PARTIAL/DIAGNOSTIC_ONLY conforme contrato | Excluir; caixa pessoal não é DPA, combinado não é rentabilidade | P2 §§6/11 |
| XIRR vitalício sleeve | Implementado, Actual/365 | Excluir por privacidade/escopo; não duplicar | P2 §11 |
| XIRR por ativo | READY só com cadeia completa/raiz, bloqueio por ticker | Excluir; “por ativo” ainda depende de ledger pessoal | P2 §11 |
| XIRR janela MTD…3Y/since-valid-history | READY nas janelas dos flags posteriores | Excluir; escopo de fluxo pessoal, não trading | P2 §11/§16 |
| TWR actual-cash sleeve | READY YTD/1Y/janela curta; inception NOT_READY por BTHF11 | Excluir; pagamento≠ex-date; RAW_CLOSE_NOT_ADJUSTED_PRICE | P2 §§8/11 |
| TWR conta cheia/contínuo/direito ex-date | NOT_READY; is_twr_implementable=False conta cheia | Excluir; promoção actual-cash não promove esses contratos | P2 §8 |
| CDI composto/casado a fluxos/FIFO IR | Implementado | Fora do namespace asset-only; benchmark público exige outro escopo | P2 §12 |
| Resultado realizado/econômico | Realizado implementado, econômico PARTIAL | Excluir custo/caixa pessoais; realizado não é lucro tributável | P2 §11 |
| Alvos de peso/overlap/simulador | Planejamento implementado; papéis não configurados | Excluir; peso não é limiar de preço | P2 §13 |
| Indicadores técnicos | Ausentes | Proposta Java v1 SMA20/50, RSI14, EMA9/21 e volume diário validado; volatilidade v2 proposta/FUT-05. Nada técnico Python para exportar | P2 §9; P1 §5 |

## Lista inicial precisa de exportação proposta

Allowlist numérica inicial proposta, exatamente três resultados: `equity.graham` (BRL por security, somente ação com unidade de LPA/VPA compatível), `fii.price_to_book` (razão adimensional, não percentual) e `equity.bh_score` (pontos 0–100, cobertura separada). Insumos públicos necessários e suas fontes/datas/escala pertencem à linhagem desses resultados, sem abrir um namespace arbitrário de outros cálculos. Amostras sanitizadas e acordo do produtor devem confirmar a definição/unidade/security de cada chave; coverage não é automaticamente 23/23. Outros fundamentos numéricos exigem extensão explícita do registry, não entram por conveniência do DTO. `bazin` e `fii.common_core_quality` podem trazer só estado/limitação com valor nulo. Ausência de produtor/evidência por ativo mantém indisponível. Valores PARTIAL permanecem PARTIAL e podem ser exibidos qualificados.

Histórico separado: apenas fechamento COTAHIST RAW e datas/cobertura/linhagem auditadas. 245/010/PREULT/100 não demonstra que o produtor expõe open/high/low/volume. HIS-04 admite close-only para SMA/RSI, nunca OHLCV fabricado. FATCOT é lote. Fallback Python para Investidor10 não pode ser relabelado COTAHIST; origem efetiva explícita, sem mistura silenciosa.

Readiness, quality, coverage, reference_date, generation_time, units, source, calculation_version e local assurance são independentes. Geração nova não torna READY vencido fresco. O stale de fundamentos está desligado no produtor (`stale_threshold_days=None`); consumidor exige política por métrica, sem herdar 45min de cotação.

FIN-04 propõe contexto fora das regras v1, sujeito a aprovação humana explícita. Auditorias não sustentam Graham/scores/retorno da conta como preço-alvo. Alvo inicial é valor manual em BRL revisado pelo operador. Ampliação exige contrato por cálculo/prontidão/uso.

## Testes e documentação divergente

P1 §6 relata 1321 passed/638 errors de setup na suíte cheia e 274 passed no recorte com basetemp local. Não prova suíte completa limpa. P2 §16/P3 §5 relatam flags posteriores às negativas de README/CHECKPOINT/docstrings antigos. Preservar flags e escopos relatados sem promover preço ajustado/TWR total/qualidade FII. Fidelidade do exportador e validação por ativo seguem pendentes, sem acesso upstream.

## Entregas A/B e prova de integração

A/FIN-01–03: schema/registry versionados, testes de privacidade/unidades/datas/readiness/limites/idempotência/ativação atômica no Java. Fixtures artificiais demonstram o comportamento do consumidor e mantêm a origem SYNTHETIC explícita. Não demonstram execução de compute_score/Graham/PVP, nem a existência de artefato de cálculo real.

B/FIN-06: somente após autorização separada para alterações no projecao-carteira, um produtor invoca cálculos canônicos com insumos públicos permitidos e independentes de carteira. Não ler custódia, transações, contas, PM, fluxos ou arquivos privados, nem sanitizar dados privados depois de acessá-los. Registrar versão do produtor/cálculo/schema, insumos/unidades/datas/escala/security, flags, cobertura e limitações reais. Goldens sanitizados independentes e evidência de fidelidade entre saída canônica e artefato exportado, seguidos da validação Java, habilitam INTEGRATION_VERIFIED_REAL para os ativos/métricas comprovados, não universalmente.

Autorização não é prova de funcionamento; cálculo disponível não é exportador disponível; arquivo novo não altera as-of/readiness. Nenhuma dessas entregas converte Graham em alvo, P/VP em ranking ou B&H em recomendação automática. FIN-04/contexto somente segue pendente de decisão humana. A fase 7 inteira/reutilização real em v1 ficam bloqueadas até B ou revisão formal de escopo; A pode ser entregue isoladamente com rótulo correto.
