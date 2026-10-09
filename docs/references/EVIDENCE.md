# Authorized evidence register

Reconciled: 2026-10-05, America/Sao_Paulo. All ten supplied files were extracted into this repository and read in full, including the CSV header and all 23 data rows. Original reports are preserved byte-for-byte. Their recommendations are evidence to assess, not instructions authorizing access to upstream repositories, credentials or services.

## Evidence levels and limits

- LOCAL_VERIFIED: file existence, byte integrity, parsed CSV values or arithmetic reproduced locally.
- AUDIT_REPORTED: findings or tests stated in a supplied report; upstream code, raw HTTP evidence and tests were not independently inspected/repeated here.
- PROPOSED: monitor design/policy awaiting human approval.
- UNKNOWN: not established by these documents. READY is a producer readiness label, not local verification or signal eligibility.

ZIP SHA-256: `46693CFCBBBEEAE1EA58124A3FA6AD7654ACE3701C4B786B91EADDBCB32EF43C`. Ten entries matched the expected paths; extraction rejected traversal/unexpected paths and existing-file overwrites. The original ZIP was not modified. Byte lengths/hashes below identify the imports; final validation compares ZIP entry contents to extracted files.

## Source inventory

All files: PRESENT, FULLY READ. No named author is supplied. Brapi and scraper explicitly report 2026-10-05; Python identifies HEAD `da62ec7` but no explicit audit date. Receipt date must not substitute for an absent audit date.

| ID / file | Bytes | SHA-256 | Inspected scope |
|-----------|------:|---------|-----------------|
| B1 [RESULTADO.md](brapi/RESULTADO.md) | 20941 | `B24B77D0D38904291BEDE37AB1F1577D65335BF57032CA81484C3D5B9CE62D1D` | All sections: plan/sandbox, 23 quotes, variation, history, 40 requests, risks |
| B2 [COBERTURA.csv](brapi/COBERTURA.csv) | 9229 | `310F3E51C476BB07843827302B075E5AF398607524085098C18A21E555A9FD95` | Header and all 23 rows: identity, class, times, prices, variations, nulls/errors |
| B3 [CONSUMO.md](brapi/CONSUMO.md) | 7326 | `6EB7FE7F3362F8B63D701A4C9FCD1403B51813AECEAC14B7A9BFBB352602B1FD` | All scenarios, reset, backfill, retry, 30% reserve |
| B4 [SCHEMA.md](brapi/SCHEMA.md) | 9547 | `15E2DA912933635AD0AEF31311F5762068806BEA3E8F9F8D12E457D852D8D76A` | All envelopes/fields, types, history, errors, headers, time semantics |
| S1 [SCRAPER_AUDIT.md](scraper/SCRAPER_AUDIT.md) | 14844 | `506AAA1EE45D9F4316C94FFAA214AFD1877049CA238BC3CEF70377A1F6CCA54C` | All routes/cache questions, mapper quality, schema, consumers, tests |
| S2 [DATA_INVENTORY.md](scraper/DATA_INVENTORY.md) | 6175 | `34155597E9D1EA9A253E8B915B7B3A899DE617163AF869F2E54DAEFDE3BB8362` | All capability/status tables and absent capabilities |
| S3 [INTEGRATION_OPTIONS.md](scraper/INTEGRATION_OPTIONS.md) | 7740 | `6B4C4316119969C132C96A7C2B792ADCB19D5CBDFBAE6699DEB98EDB61BEE120` | All alternatives, ownership, changes, KEEP SEPARATE |
| P1 [MONITORING_REUSE_AUDIT.md](carteira/MONITORING_REUSE_AUDIT.md) | 10375 | `417C10EBA9D7FA06B0027808C3082E53C4976C5868A97AA196C03C50E55172C5` | Sections 1–7, all asset groupings, exclusions, test limitations |
| P2 [FINANCIAL_COMPONENTS.md](carteira/FINANCIAL_COMPONENTS.md) | 20469 | `DD9D61F4D113AF6D3536B0E8FC36C288C9A45BDEF1A54F2602A03F0DCF5BC03F` | Sections 1–16, all readiness, formulas, RAW history, dates, deprecated text |
| P3 [INTEGRATION_RECOMMENDATION.md](carteira/INTEGRATION_RECOMMENDATION.md) | 15003 | `A0CDDFDB42FD6A7B8AA2880BA8C03A8AA7280ECDF6037408639577D7C3247FB2` | Sections 1–5, alternatives A–D, effort, seven closing points, INDEPENDENT |

## Reconciled claims

| ID | Claim | Level / disposition | Section evidence and limits |
|----|-------|---------------------|-----------------------------|
| E-01 | Files missing at discovery; now 10/10 present/read | LOCAL_VERIFIED; absence blocker closed | Inventory above; prior review preserves discovery history |
| E-02 | Installed GSD 1.2.0/configuration | Prior LOCAL_VERIFIED record | [Workflow](../planning/GSD-WORKFLOW.md); no install/update this run |
| E-03 | 15,000/billing-cycle, one ticker/call, concurrency one | AUDIT_REPORTED; planning limits | B1 “O que esta conta é”, B3 “Cota real”, B4 “Headers de limite”; no current entitlement check |
| E-04 | Free ranges 1d/5d/1mo/3mo, interval 1d; no intraday | AUDIT_REPORTED | B1 “Janela e intervalo”: measured HGLG11/SNAG11. ITUB4 sandbox does not extend entitlement |
| E-05 | 23 unique quote rows: 6 stock + 3 unit + 13 fii + 1 fi-agro | LOCAL_VERIFIED CSV / AUDIT_REPORTED classifications | B2 all rows, B4 “Tipo”; [catalog](../planning/ASSET-CATALOG.md). KNHY11 official confirmation pending |
| E-06 | 27–209s age, published ~30min vs 30s header, unreliable variations | AUDIT_REPORTED time / LOCAL_VERIFIED arithmetic | B1 “Idade”/“Variação”, B2: 22 absolute mismatches at R$0.02 and 7 internal-percent mismatches at 0.15pp; no delay SLA |
| E-07 | Scraper snapshots, no OHLCV or cache-only HTTP | AUDIT_REPORTED; adapter disabled | S1 routes/cache questions 4/7, S2 price/history, S3 B; GET/raw can refresh after 24h |
| E-08 | Python formulas vary in readiness; no technical indicators/API/exporter | AUDIT_REPORTED | P1 §§3–6, P2 §§4–12/16, P3 §§1/4/5; [registry](../planning/PYTHON-REUSE.md). Producer work outside current scope |
| E-09 | WAHA edition/version/send/auth/receipt/idempotency | UNKNOWN; early activation gate | No supplied report audits WAHA; outbound contract before Phase 4 activation, webhook before Phase 6 |
| E-10 | Full calendar/session/action evidence for 23 assets | UNKNOWN | Historical holiday observation is not a calendar; personal events are not official adjustment feed |
| E-11 | Reset delta and sample endpoint date | AUDIT_REPORTED + LOCAL_VERIFIED arithmetic | B1/B4: 2026-10-05T17:18:58Z + 2,519,336s = 2026-11-03T21:07:54Z (18:07:54 BRT); no hardcoded future reset. Start/shared usage unknown |
| E-12 | Audit capacity scenario | LOCAL_VERIFIED conditional arithmetic; PROPOSED allocation | B3: 8,464 quotes + 529 history + 23 backfill + ceil(5% × 8,993)=450 => 9,466; ceiling 10,500, headroom 1,034. 8h/23 sessions are assumptions |
| E-13 | COTAHIST 245 chars, market 010, PREULT/100, RAW | AUDIT_REPORTED | P2 §5/§15: schema/years READY, security coverage PARTIAL, adjusted NOT_READY. Close support does not prove OHLCV export |
| E-14 | Upstream tests and old readiness documents | AUDIT_REPORTED only | S1 isolated classes; P1 §6: 1321 passed/638 setup errors full run, 274 passed selected run. No local re-execution/whole-suite clean claim; P2/P3 report newer flags |

## Remaining evidence dependencies

Raw `analysis/brapi/evidence/` HTTP bodies, upstream Python source/status modules, private caches and official identity documents are not included. Cited upstream paths are provenance, not local links or authorization to fetch. File integrity does not authenticate authors or verify upstream behavior.

See [divergences](../architecture/DESIGN-REVIEW.md), [review packet](../planning/RECONCILIATION.md) and [questions](../planning/OPEN-QUESTIONS.md). File absence and unknown ticker list are resolved; official identity, operational policies and missing producer/WAHA capabilities remain open.
