# Initial Candidate Subset Selection

**Revision:** 3 (Plan 01-02, Task 01-02-03 — per-selected-asset evidence evaluation executed)
**Initialized:** 2026-10-06, America/Sao_Paulo (Revision 1, Task 01-02-01)
**Selection recorded:** 2026-10-06, America/Sao_Paulo (Revision 2, checkpoint 01-02-02)
**Evidence evaluated:** 2026-10-06, America/Sao_Paulo (Revision 3, Task 01-02-03 — local evidence only, no Internet)
**Owner requirement:** CAT-01 (candidate selection gate)
**Status:** `CANDIDATE_SELECTED` + `SUBSET_EVALUATED` (0 validated, 4 evidence-pending)
**Scope:** `CANDIDATE_SELECTION_ONLY` → evaluation is documentary-only; **no** activation, **no** approval

## Current state

- **subset_status:** `CANDIDATE_SELECTED`
- **selection_option:** `name-subset`
- **selected_count:** 4
- **nonselected_count:** 19
- **decided_by:** owner (Rodrigo), explicit authorization of checkpoint 01-02-02
- **decision_date:** 2026-10-06, America/Sao_Paulo
- **activation_status (every row, all 23):** `NOT_AUTHORIZED` — unchanged by this selection
- **identity_status (all selected members):** `OFFICIAL_IDENTITY_UNVERIFIED` — selection is **not** official identity proof

## Candidate members (named by owner)

Each member is a unique catalog ticker present in [ASSET-VALIDATION.md](ASSET-VALIDATION.md). None is KNHY11.
Selection scope is `CANDIDATE_SELECTION_ONLY`: it marks these rows for the Task 01-02-03 per-asset documentary
evidence evaluation (not run here) and nothing more.

| # | ticker | asset_id (proposed internal) | matrix row | provider/audit label (AUDIT_REPORTED) | selected | 01-02-03 disposition | activation_status |
|---|--------|------------------------------|-----------|---------------------------------------|----------|----------------------|-------------------|
| 1 | WEGE3  | ASSET-WEGE3  | 1  | Ação   | true | EVIDENCE_PENDING (no dated mapping; CNPJ UNKNOWN) | NOT_AUTHORIZED |
| 2 | BPAC11 | ASSET-BPAC11 | 2  | Unit   | true | EVIDENCE_PENDING (CNPJ-suffix conflict unresolved) | NOT_AUTHORIZED |
| 3 | VRTA11 | ASSET-VRTA11 | 11 | FII    | true | EVIDENCE_PENDING (admin/gestor conflict; no dated mapping) | NOT_AUTHORIZED |
| 4 | SNAG11 | ASSET-SNAG11 | 23 | FIAGRO | true | EVIDENCE_PENDING (name-variant conflict; not CVM-primary) | NOT_AUTHORIZED |

**selected_members:** `[WEGE3, BPAC11, VRTA11, SNAG11]` — 4 unique, nonempty, all catalog tickers, KNHY11 excluded.
The provider/audit label is the already-inventoried classification from the matrix; it is reproduced here for
convenience only and remains `AUDIT_REPORTED`, never official.

## What this selection does and does NOT mean

- **DOES:** records an explicit, owner-decided, nonempty, unique candidate subset for documentary piloting;
  scopes which rows Task 01-02-03 will evaluate for identity evidence.
- **DOES NOT:** approve identity (`identity_status=OFFICIAL_IDENTITY_UNVERIFIED` on all four), authorize any
  activation (`activation_status=NOT_AUTHORIZED` on all 23), complete CAT-01, accept GOV-01, close Phase 1, or
  presume the four earlier-suggested assets. The 19 non-selected rows and KNHY11 are unaffected.

## Rules binding this selection (carried forward, all satisfied)

Carried from the plan and [ASSET-VALIDATION.md](ASSET-VALIDATION.md):

1. ✅ Membership is a **nonempty, unique subset** of the 23 catalog tickers (4 distinct members).
2. ✅ **KNHY11 is excluded** while `QUARANTINED_CLASSIFICATION_PENDING` (ADR-010; Q-15 open). The user
   "fii de papel" statement stays `USER_REPORTED` and did not select it.
3. ✅ The **four assets suggested earlier are NOT presumed approved** — the recorded members are the owner's
   explicit named list for this checkpoint, not an inferred default.
4. ✅ No duplicates, no unknown tickers, list is nonempty.
5. ✅ `CANDIDATE_SELECTED` ≠ approved — distinct fields. Selection did **not** approve identity, complete
   CAT-01, close Phase 1, or authorize live activation (`activation_status` stays `NOT_AUTHORIZED`).
6. ✅ An explicit owner decision was recorded (actor + date); this file is no longer `SELECTION_PENDING`.

## Human decision executed (checkpoint 01-02-02)

> **Which nonempty list of catalog assets should undergo initial documentary validation, with KNHY11
> excluded while official evidence is absent?**
>
> **Resolution — option `name-subset`.** Owner named: WEGE3, BPAC11, VRTA11, SNAG11.
> Each named row still needs independent evidence (Task 01-02-03, **not run here**) and later explicit scoped
> approval (Plan 01-04). Candidate-only scope confirmed.

## Identity verifications required before Task 01-02-03 can run

The following are **not performed by this selection** and remain prerequisites for 01-02-03 per the plan:

- **Official instrument identity** for each of the four tickers — a repository-local, dated identity document
  (issuer / B3 / CVM record) establishing the official instrument behind each ticker. Provider `assetType`/
  `subType` labels (`AUDIT_REPORTED`) are **not** sufficient.
- **Category confirmation** against an official source for VRTA11 (FII) and SNAG11 (FIAGRO) — the provider label
  must be corroborated; FIAGRO must not be auto-assumed (ADR-010 posture carried forward).
- **Dated validity interval** (`valid_from`/`valid_to`) — every matrix row is `UNKNOWN`; 01-02-03 must source an
  official dated identity, not the `observed_as_of` audit timestamp.
- **Ticker-identity binding** — confirmation that the requested ticker maps to the official instrument with no
  rename/reuse ambiguity in the covered window.
- **VRTA11 data-freshness note** — a stale price-time header (B1) was reported for VRTA11; this is a separate
  freshness concern that 01-02-03 should record but which does not block identity evaluation.

All of the above require **repository-local audited evidence only** — no external/LAN/API/credential/sibling
access, no deploy, no commit, no application code.

---

# Task 01-02-03 — Per-selected-asset evidence evaluation (executed 2026-10-06)

**Execution mode:** local evidence only. **No** Internet/LAN/credential/sibling access this task; no source-protected
document altered; `ASSET-VALIDATION.md` left byte-unchanged (dispositions recorded here, in the derived
selection/validation artifact, to avoid touching the registered matrix hash). No 01-03/01-04, no GOV-01/CAT-01
closure, no Phase-1 closure, no commit, no auto-advance.

**Evidence inputs traced:** [OFFICIAL-IDENTITY-SOURCES.md](OFFICIAL-IDENTITY-SOURCES.md) (Rev 1, SHA
`40698D88…1EC37`), [ASSET-VALIDATION.md](ASSET-VALIDATION.md) (SHA `9916507C…E38F57B`),
[EVIDENCE-BASELINE.md](EVIDENCE-BASELINE.md), ADR-010, and the prior audit (COBERTURA.csv B2 digest
`310F3E51…`). These are the only artifacts consulted.

## Disposition rule (from 01-02-03 plan contract)

A selected row is marked **`DOCUMENTARY_SUBSET_VALIDATED`** only when ALL hold: (a) attributable, **unique**
identity/class/security-unit mapping; (b) a **dated** mapping (an official effective date — not `observed_as_of`,
not the consultation date); (c) **no unresolved conflict**; and (d) a stated evidence scope. Otherwise the row is
**`EVIDENCE_PENDING`** with the exact missing artifact named. Field-level evidence grades are **preserved** from
the dossier and **not** auto-promoted. `observed_as_of` is a bounded observation, never a valid interval, and
authorizes no live lookup. KNHY11 quarantine is independent — a selected row passing or failing does not touch it.

## Per-asset, per-field results

### WEGE3 — row disposition: `EVIDENCE_PENDING`
| field | value | evidence grade (preserved) | note |
|---|---|---|---|
| official ticker | WEGE3 | `OFFICIAL_VERIFIED` | CVM/RAD IPE excerpt "WEG S.A. (B3: WEGE3 / OTC: WEGZY)" |
| legal name / issuer | WEG S.A. | `OFFICIAL_VERIFIED` | same |
| classification | Ação ON | `OFFICIAL_VERIFIED` (segment PARTIAL) | not inferred from suffix |
| CNPJ | **UNKNOWN** | `UNKNOWN` | kept UNKNOWN per rule — no primary CNPJ record held locally |
| ticker↔instrument | WEGE3 = single ON line of WEG S.A. | `OFFICIAL_VERIFIED` | unique mapping |
| source | CVM/RAD IPE URLs (dossier §1) | — | URL present but body not parsed → not upgraded on URL alone |
| valid_from / valid_to | **UNKNOWN / UNKNOWN** | `UNKNOWN` | no official effective date; not inferred from today/doc date |

**Blocker:** no **dated** official identity mapping (criterion b) and CNPJ UNKNOWN. Needed artifact: a local CVM FCA/cadastro record with CNPJ and a dated identity. Conflict: none.

### BPAC11 — row disposition: `EVIDENCE_PENDING`
| field | value | evidence grade (preserved) | note |
|---|---|---|---|
| official ticker | BPAC11 | `OFFICIAL_VERIFIED` | issuer RI + CVM |
| legal name / issuer | Banco BTG Pactual S.A. | `OFFICIAL_VERIFIED` | CVM-filed IPE |
| classification | Unit (1 ON + 2 PNA) | `OFFICIAL_VERIFIED` | issuer RI composition |
| CNPJ | root `30.306.294`, **suffix UNRESOLVED** | `OFFICIAL_PARTIAL` (root) / `UNKNOWN` (suffix) | **conflict preserved**: `/0001-45` vs `/0001-50` vs `/0002-26`; suffix **not** chosen by inference |
| ticker↔instrument | BPAC11 unit = 1 ON + 2 PNA of the Banco | `OFFICIAL_VERIFIED` | unique composition |
| source | BTG RI + CVM/RAD URLs (dossier §2) | — | not upgraded on URL alone |
| valid_from / valid_to | **UNKNOWN / UNKNOWN** | `UNKNOWN` | 2017 migration is a corporate-event date, not an identity validity interval |

**Blocker:** **unresolved CNPJ-suffix conflict** (criterion c fails). Needed artifact: a single primary CVM FCA / B3 issuer record pinning the matriz suffix.

### VRTA11 — row disposition: `EVIDENCE_PENDING`
| field | value | evidence grade (preserved) | note |
|---|---|---|---|
| official ticker | VRTA11 | `OFFICIAL_VERIFIED` | B3/FNET + CVM AGO |
| legal name | Fator Verità Fundo de Investimento Imobiliário – FII | `OFFICIAL_VERIFIED` | CVM-filed AGO |
| CNPJ | `11.664.201/0001-00` | `OFFICIAL_VERIFIED` | FNET Informe Anual + AGO agree |
| classification | FII (fundo de papel strategy) | `OFFICIAL_VERIFIED` | not inferred from suffix |
| administrator vs. gestor | **UNRESOLVED** | `OFFICIAL_PARTIAL` | **conflict preserved**: "Banco Fator" (administrator) vs "Far – Fator Administradora de Recursos" (gestor) |
| disambiguation | distinct from VRTM11 (`51.870.412/0001-13`) | `OFFICIAL_PARTIAL` | **preserved** — must not conflate |
| ticker↔instrument | VRTA11 = cota do Fator Verità FII | `OFFICIAL_VERIFIED` | unique mapping |
| valid_from / valid_to | **UNKNOWN / UNKNOWN** | `UNKNOWN` | inception cited 2010/2011 but no official effective interval; not inferred |

**Blocker:** **unresolved administrator/gestor role conflict** (criterion c) and no dated identity mapping (criterion b). Needed artifact: CVM registration / regulamento stating administrator, gestor and a dated identity. Note: core identity (name+CNPJ+class) is strong; only the role attribution and dated mapping block validation.

### SNAG11 — row disposition: `EVIDENCE_PENDING`
| field | value | evidence grade (preserved) | note |
|---|---|---|---|
| official ticker | SNAG11 | `OFFICIAL_VERIFIED` | issuer "Dados oficiais" sheet |
| legal name | SUNO AGRO FIAGRO IMOBILIÁRIO | `OFFICIAL_PARTIAL` | **name-variant conflict preserved**: vs "SUNO AGRO – FIAGRO RESPONSABILIDADE LIMITADA" (registry) |
| CNPJ | `28.152.777/0001-90` | `OFFICIAL_PARTIAL` | issuer sheet + registry; not CVM-primary |
| classification | FIAGRO Imobiliário | `OFFICIAL_PARTIAL` | issuer-stated; not inferred from suffix; distinct from KNHY11's unresolved class |
| administrator / custodiante | Singulare Corretora de TVM S.A. | `OFFICIAL_PARTIAL` | issuer sheet |
| gestor | Suno Gestora de Recursos Ltda. | `OFFICIAL_PARTIAL` | issuer sheet |
| source | Suno issuer sheet (links to CVM/B3 Regulamento) | — | **not** upgraded on URL/link presence alone |
| valid_from / valid_to | **UNKNOWN / UNKNOWN** | `UNKNOWN` | Regulamento dated 24/Fev/2026 exists but no identity validity interval asserted; not inferred |

**Blocker:** all core fields `OFFICIAL_PARTIAL` (not CVM-primary) and a **name-variant conflict** (criterion c). Needed artifact: the CVM-filed Regulamento to confirm exact legal name/CNPJ/administrator/class.

## Counts and set relations (verified)

- **catalog:** 23 · **selected:** 4 · **validated (`DOCUMENTARY_SUBSET_VALIDATED`):** 0 · **evidence-pending:** 4
- `validated (∅) ⊆ selected ({WEGE3,BPAC11,VRTA11,SNAG11}) ⊆ catalog (23)` ✅
- `selected_count (4) + nonselected_count (19) = 23` ✅
- **KNHY11:** `QUARANTINED_CLASSIFICATION_PENDING` — untouched; not in selected; quarantine independent of the four results ✅
- **activation_status:** `NOT_AUTHORIZED` on all 23 ✅

## Historical validity status

**No** `valid_from`/`valid_to` was established for any of the four. All remain `UNKNOWN`. Neither the consultation
date (2026-10-06) nor any document date was used as a validity start. No current-source freshness claim is made;
the VRTA11 stale-header (B1) note is a data-freshness observation only and is **not** an identity fact.

## Operational-authorization blockers (all four)

Every selected row is `EVIDENCE_PENDING`; **none** is `DOCUMENTARY_SUBSET_VALIDATED`. Therefore:
- No row qualifies to be forwarded to 01-04 as "validated"; 01-04 scoped human approval has **nothing validated**
  to approve yet (per plan `failure_modes`: "If no selected asset validates, no downstream subset gate can be
  approved").
- All activation remains `NOT_AUTHORIZED`; CAT-01 and Phase 1 stay open; GOV-01 unaccepted.
- The exact missing artifact per asset is named above — each is a **local primary CVM/B3 document** not currently
  in the repository. Acquiring them is a *separate* authorization (as the identity-acquisition task was); this
  task does **not** fetch them.

<rollback>Supersede this recorded selection with a reviewed revision and rationale; revoking a selection does not erase inventoried identities, alter source originals, or change registered hashes. KNHY11 quarantine and all 23 `NOT_AUTHORIZED` activations persist regardless.</rollback>
