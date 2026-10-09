# CAT-01 — Per-Asset Documentary Validation Matrix

**Revision:** 1 (Plan 01-02, Task 01-02-01 controlled execution)
**Execution date:** 2026-10-06, America/Sao_Paulo
**Owner requirement:** CAT-01 only
**Review status:** DOCUMENTARY_REVIEW_PENDING
**Task 01-02-01:** completed — 23-row documentary matrix prepared from already-audited local evidence.
**Scope:** Reuse the ten-source baseline, the 23-row catalog and COBERTURA.csv. Prepare a per-asset documentary inventory. This is **not** a new provider/financial audit, an official-identity determination, a candidate selection, or an activation.

## Authority and limits

Governing authority is the current
[CONTRACTS — Asset catalog and collection](../../contracts/CONTRACTS.md#asset-catalog-and-collection),
[ADR-010 — Explicit instrument types and KNHY11 quarantine](../../architecture/ADRS.md#adr-010--explicit-instrument-types-and-knhy11-quarantine),
the verified [EVIDENCE-BASELINE](EVIDENCE-BASELINE.md) (`C186F0A63F9D9834887218F7F2CF048E331B3D44FCC60F9D39071E34B6809C17`),
and the current user authorization for Task 01-02-01 only.

Boundaries carried from the baseline, unchanged:

- **No asset is selected, validated or approved by this file.** `selected=false` on all 23 rows. The subset is `SELECTION_PENDING` (see [SUBSET-SELECTION.md](SUBSET-SELECTION.md)).
- **All 23 rows carry `activation_status=NOT_AUTHORIZED`.** Documentary presence is not activation.
- **KNHY11 remains `QUARANTINED_CLASSIFICATION_PENDING`.** The user statement "knhy11 é fii de papel" is `USER_REPORTED` only; it does not supply official evidence, resolve Q-15 or release the quarantine. ADR-010 governs.
- **The four assets suggested earlier are NOT presumed approved.** No candidate list is inferred from this inventory.
- No ISIN/CNPJ/legal-validity interval, rename or ticker-reuse history is fabricated. Unknown stays `UNKNOWN`.
- An `observed_as_of` point is a bounded observation, **not** an open-ended valid interval and **not** authorization for any current or historical live lookup.
- `asset_id` is a **proposed internal local identifier**, not proof of issuer identity.

### Status vocabulary (this file)

- `documentary_status`:
  - `INVENTORIED` — row present with provider/audit-labeled identity; per-asset independent evidence evaluation is deferred to Task 01-02-03 (not run here).
  - `QUARANTINED_CLASSIFICATION_PENDING` — KNHY11 only; official dated identity/category absent.
- `activation_status`: `NOT_AUTHORIZED` for every row (no other value is used in Phase 1).
- `selected`: `false` for every row (selection is the 01-02-02 human checkpoint, not run here).
- `category_evidence`: provider/audit label only (`AUDIT_REPORTED`), never official.

## 23-row per-asset matrix

Source of truth for identity/class fields: [COBERTURA.csv](../../references/brapi/COBERTURA.csv) (B2) and
[ASSET-CATALOG.md](../ASSET-CATALOG.md). Row order follows the prior catalog. `observed_as_of` is the CSV `preco_time_utc`
(a bounded audit observation, not a validity interval). Evidence digest references the registered B2 hash
`310F3E51C476BB07843827302B075E5AF398607524085098C18A21E555A9FD95`.

| # | asset_id (proposed internal) | ticker (requested=returned) | market | currency | source_type (Brapi assetType/subType) | category_evidence (AUDIT_REPORTED) | observed_as_of (UTC) | valid_from | valid_to | evidence_path / section | evidence_digest (B2) | selected | documentary_status | activation_status |
|---|------------------------------|-----------------------------|--------|----------|---------------------------------------|------------------------------------|----------------------|------------|----------|-------------------------|----------------------|----------|--------------------|-------------------|
| 1 | ASSET-WEGE3 | WEGE3 | B3 | BRL | stock / stock | Ação | 2026-10-05T17:17:30Z | UNKNOWN | UNKNOWN | COBERTURA.csv row 1; ASSET-CATALOG line 1 | 310F3E51… | false | INVENTORIED | NOT_AUTHORIZED |
| 2 | ASSET-BPAC11 | BPAC11 | B3 | BRL | stock / unit | Unit | 2026-10-05T17:17:30Z | UNKNOWN | UNKNOWN | COBERTURA.csv row 2; ASSET-CATALOG line 2 | 310F3E51… | false | INVENTORIED | NOT_AUTHORIZED |
| 3 | ASSET-ITUB4 | ITUB4 | B3 | BRL | stock / stock | Ação | 2026-10-05T17:17:30Z | UNKNOWN | UNKNOWN | COBERTURA.csv row 3; ASSET-CATALOG line 3 | 310F3E51… | false | INVENTORIED | NOT_AUTHORIZED |
| 4 | ASSET-EGIE3 | EGIE3 | B3 | BRL | stock / stock | Ação | 2026-10-05T17:18:30Z | UNKNOWN | UNKNOWN | COBERTURA.csv row 4; ASSET-CATALOG line 4 | 310F3E51… | false | INVENTORIED | NOT_AUTHORIZED |
| 5 | ASSET-TAEE11 | TAEE11 | B3 | BRL | stock / unit | Unit | 2026-10-05T17:17:30Z | UNKNOWN | UNKNOWN | COBERTURA.csv row 5; ASSET-CATALOG line 5 | 310F3E51… | false | INVENTORIED | NOT_AUTHORIZED |
| 6 | ASSET-SBSP3 | SBSP3 | B3 | BRL | stock / stock | Ação | 2026-10-05T17:17:30Z | UNKNOWN | UNKNOWN | COBERTURA.csv row 6; ASSET-CATALOG line 6 | 310F3E51… | false | INVENTORIED | NOT_AUTHORIZED |
| 7 | ASSET-SAPR11 | SAPR11 | B3 | BRL | stock / unit | Unit | 2026-10-05T17:17:30Z | UNKNOWN | UNKNOWN | COBERTURA.csv row 7; ASSET-CATALOG line 7 | 310F3E51… | false | INVENTORIED | NOT_AUTHORIZED |
| 8 | ASSET-TIMS3 | TIMS3 | B3 | BRL | stock / stock | Ação | 2026-10-05T17:18:30Z | UNKNOWN | UNKNOWN | COBERTURA.csv row 8; ASSET-CATALOG line 8 | 310F3E51… | false | INVENTORIED | NOT_AUTHORIZED |
| 9 | ASSET-CMIG4 | CMIG4 | B3 | BRL | stock / stock | Ação | 2026-10-05T17:17:30Z | UNKNOWN | UNKNOWN | COBERTURA.csv row 9; ASSET-CATALOG line 9 | 310F3E51… | false | INVENTORIED | NOT_AUTHORIZED |
| 10 | ASSET-HGLG11 | HGLG11 | B3 | BRL | fund / fii | FII | 2026-10-05T17:17:30Z | UNKNOWN | UNKNOWN | COBERTURA.csv row 10; ASSET-CATALOG line 10 | 310F3E51… | false | INVENTORIED | NOT_AUTHORIZED |
| 11 | ASSET-VRTA11 | VRTA11 | B3 | BRL | fund / fii | FII | 2026-10-05T17:15:30Z | UNKNOWN | UNKNOWN | COBERTURA.csv row 11; ASSET-CATALOG line 11; stale header reported (B1) | 310F3E51… | false | INVENTORIED | NOT_AUTHORIZED |
| 12 | ASSET-BTLG11 | BTLG11 | B3 | BRL | fund / fii | FII | 2026-10-05T17:17:30Z | UNKNOWN | UNKNOWN | COBERTURA.csv row 12; ASSET-CATALOG line 12 | 310F3E51… | false | INVENTORIED | NOT_AUTHORIZED |
| 13 | ASSET-RBVA11 | RBVA11 | B3 | BRL | fund / fii | FII | 2026-10-05T17:18:30Z | UNKNOWN | UNKNOWN | COBERTURA.csv row 13; ASSET-CATALOG line 13 | 310F3E51… | false | INVENTORIED | NOT_AUTHORIZED |
| 14 | ASSET-HGBS11 | HGBS11 | B3 | BRL | fund / fii | FII | 2026-10-05T17:18:30Z | UNKNOWN | UNKNOWN | COBERTURA.csv row 14; ASSET-CATALOG line 14 | 310F3E51… | false | INVENTORIED | NOT_AUTHORIZED |
| 15 | ASSET-GARE11 | GARE11 | B3 | BRL | fund / fii | FII | 2026-10-05T17:17:30Z | UNKNOWN | UNKNOWN | COBERTURA.csv row 15; ASSET-CATALOG line 15 | 310F3E51… | false | INVENTORIED | NOT_AUTHORIZED |
| 16 | ASSET-ALZR11 | ALZR11 | B3 | BRL | fund / fii | FII | 2026-10-05T17:18:30Z | UNKNOWN | UNKNOWN | COBERTURA.csv row 16; ASSET-CATALOG line 16 | 310F3E51… | false | INVENTORIED | NOT_AUTHORIZED |
| 17 | ASSET-MXRF11 | MXRF11 | B3 | BRL | fund / fii | FII | 2026-10-05T17:17:30Z | UNKNOWN | UNKNOWN | COBERTURA.csv row 17; ASSET-CATALOG line 17 | 310F3E51… | false | INVENTORIED | NOT_AUTHORIZED |
| 18 | ASSET-BTHF11 | BTHF11 | B3 | BRL | fund / fii | FII | 2026-10-05T17:18:30Z | UNKNOWN | UNKNOWN | COBERTURA.csv row 18; ASSET-CATALOG line 18 | 310F3E51… | false | INVENTORIED | NOT_AUTHORIZED |
| 19 | ASSET-GGRC11 | GGRC11 | B3 | BRL | fund / fii | FII | 2026-10-05T17:18:30Z | UNKNOWN | UNKNOWN | COBERTURA.csv row 19; ASSET-CATALOG line 19 | 310F3E51… | false | INVENTORIED | NOT_AUTHORIZED |
| 20 | ASSET-XPLG11 | XPLG11 | B3 | BRL | fund / fii | FII | 2026-10-05T17:17:30Z | UNKNOWN | UNKNOWN | COBERTURA.csv row 20; ASSET-CATALOG line 20 | 310F3E51… | false | INVENTORIED | NOT_AUTHORIZED |
| 21 | ASSET-KNCR11 | KNCR11 | B3 | BRL | fund / fii | FII | 2026-10-05T17:17:30Z | UNKNOWN | UNKNOWN | COBERTURA.csv row 21; ASSET-CATALOG line 21 | 310F3E51… | false | INVENTORIED | NOT_AUTHORIZED |
| 22 | ASSET-KNHY11 | KNHY11 | B3 | BRL | fund / fii | **CONFLICT**: provider FII vs. Python-report FIAGRO grouping; user FII/paper USER_REPORTED | 2026-10-05T17:17:30Z | UNKNOWN | UNKNOWN | COBERTURA.csv row 22; ASSET-CATALOG §KNHY11; ADR-010; stale header reported (B1) | 310F3E51… | false | QUARANTINED_CLASSIFICATION_PENDING | NOT_AUTHORIZED |
| 23 | ASSET-SNAG11 | SNAG11 | B3 | BRL | fund / fi-agro | FIAGRO | 2026-10-05T17:18:30Z | UNKNOWN | UNKNOWN | COBERTURA.csv row 23; ASSET-CATALOG line 23 | 310F3E51… | false | INVENTORIED | NOT_AUTHORIZED |

**Totals (documentary, provider/audit labels only):** 6 Ação, 3 Unit, 13 FII (one, KNHY11, quarantined), 1 FIAGRO = 23 unique rows.
These are provider/audit classifications, **not** official categories. Requested=returned for all 23; no duplicate; no suffix inference.

## Per-asset notes and preserved blockers

- **UNKNOWN validity on every row:** no `valid_from`/`valid_to` is derivable from the audit snapshot. `observed_as_of` is the audit timestamp only. No legal validity interval exists in local evidence.
- **KNHY11 (row 22):** `QUARANTINED_CLASSIFICATION_PENDING`. Provider returns `fund/fii` named "Kinea High Yield CRI Fundo De Investimento Imobiliario - FII"; the Python reuse report groups it with SNAG11 as FIAGRO while itself stating KNHY11 is absent from its code/tests and that economic class requires API/CVM. No official fund/CVM/B3 document is in the package. Per ADR-010, FII is provisional, automatic FIAGRO is rejected, and Q-15 blocks activation of this instrument's classification. User "fii de papel" statement is `USER_REPORTED` and does not lift the quarantine. The other 22 rows are reviewed independently and are **not** blocked by KNHY11.
- **VRTA11 (row 11), KNHY11 (row 22):** a stale price-time header was reported in the audit (RESULTADO/B1); this is a separate data-freshness degradation requiring explicit policy, not a classification or identity fact.
- **No history/fundamentals proof:** the audit did not establish COTAHIST history or fundamentals for the 23; the Python report inspected no private data or caches.
- **marketCap null** is noted in the CSV for several rows; it is an audit-field observation only, with no bearing on identity or activation.

## Verification performed (Task 01-02-01)

LOCAL_VERIFIED documentary checks, all exit 0; see the SUMMARY for exact command output:

- COBERTURA.csv parsed to 23 data rows; 23 unique requested and 23 unique returned tickers; 0 requested≠returned mismatches.
- This matrix contains exactly 23 unique ticker rows; set-equal to the CSV ticker set and to the prior catalog ticker set (both symmetric differences empty).
- Pattern presence: `QUARANTINED_CLASSIFICATION_PENDING`, `NOT_AUTHORIZED` (all 23), `USER_REPORTED`, `valid_from`, `valid_to`, `selected` all present.
- Protected inputs (EVIDENCE.md, ASSET-CATALOG.md, COBERTURA.csv, EVIDENCE-BASELINE.md, REQUIREMENTS.md, CONTRACTS.md, ADRS.md) SHA-256 unchanged before/after.
- `query validate.consistency` → `passed=true, errors=[]` (7 expected Phase 2–8 missing-directory warnings).

Pattern presence in a PLAN or matrix is a smoke check only; it does not establish official identity, dated validity, selection or human approval.

## Boundaries (this execution)

Task 01-02-01 only. **Not run:** 01-02-02 (owner subset selection), 01-02-03 (per-selected-asset evidence evaluation), any other plan. No requirement checked off, no GOV-01 acceptance, no Phase 1 closure, no commit, no auto-advance, no application code, no external/LAN/API/credential/sibling access. KNHY11 quarantine intact; the four earlier-suggested assets are **not** approved.

<rollback>Supersede this derived matrix with a reviewed revision and rationale; never edit source originals or registered hashes, and never erase a recorded blocker.</rollback>
