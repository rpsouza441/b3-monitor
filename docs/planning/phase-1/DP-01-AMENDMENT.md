# DP-01 — Identity Gate Separation (APPLIED as amendment, 2026-10-06)

**Status:** APPLIED by autonomous-execution authorization (task §2). This is an **amendment** that supersedes the
over-strict 01-02-03 disposition reading; it does **not** erase prior findings. Original audit results and the
`EVIDENCE_PENDING` history in `SUBSET-SELECTION.md` Rev 3 are retained as historical evidence.

## What changed (and why it is not a contract rewrite)

The approved artifacts (CAT-01 "datable", CONTRACTS "eligibility is per use", ADR-010 "no invented date") already
support identity acceptance without a historical interval. The block lived only in the **01-02-03 plan-task
disposition rule**. DP-01 corrects that rule by splitting one state into three independent states and evaluating
conflicts at field/use granularity. Protected source bytes (EVIDENCE/CATALOG/CSV/BASELINE/REQUIREMENTS/
CONTRACTS/ADRS) are **unchanged**; this amendment and the derived Phase-1 artifacts carry the correction.

## Three independent acceptance states (now in force for Phase-1 identity)

1. **CURRENT_IDENTITY_VERIFIED** — official primary evidence of the instrument the ticker denotes now
   (legal name + class + ticker↔instrument), with source provenance and an observation/reference date.
   **Does NOT require** `valid_from`/`valid_to`. A non-essential field left `UNKNOWN` (e.g. a CNPJ not needed to
   denote the instrument) does not block.
2. **HISTORICAL_IDENTITY_VERIFIED** — a demonstrated ticker→instrument mapping over a dated interval. **Fail
   closed** where not demonstrable; never invent dates; never infer from today's observation. Unknown stays
   `UNKNOWN`.
3. **OPERATIONAL_ACTIVATION_AUTHORIZED** — explicit human scope approval for prospective live monitoring.
   Independent of history unless a rule needs history. **All 23 remain `NOT_AUTHORIZED`.**

Conflict rule: a conflict affects **only** the relevant field, instrument or use — not the whole row.

## Evidence tiers (unchanged grades + one added provider tier)

`OFFICIAL_VERIFIED` > `OFFICIAL_PARTIAL` > `INTERNAL_API_REPORTED` (the user's Java scraper classification endpoint)
≈ `PROVIDER_REPORTED` (Brapi) > `USER_REPORTED` > `UNKNOWN`.
**Neither the internal API nor Brapi is official CVM/B3 evidence.** Classification is never derived from a ticker
suffix.

## Candidate dispositions under DP-01 (assessment only — no activation)

Internal classification API (`http://192.168.22.245:8088/api/v1/ticker/{t}/classificacao`, read-only, 1 call each,
2026-10-06) returned, as `INTERNAL_API_REPORTED`:

| ticker | internal API `tipo` | Brapi (PROVIDER) | official (dossier) | CURRENT_IDENTITY | HISTORICAL | OPERATIONAL |
|--------|--------------------|------------------|--------------------|------------------|------------|-------------|
| WEGE3  | ACAO_ON | stock/stock | WEG S.A., ON — OFFICIAL_VERIFIED | **VERIFIED** (CNPJ UNKNOWN, non-essential) | UNKNOWN (fail-closed) | NO |
| BPAC11 | UNIT | stock/unit | Banco BTG Pactual S.A., Unit — OFFICIAL_VERIFIED | **VERIFIED** (CNPJ suffix pending, field-scoped) | UNKNOWN (fail-closed) | NO |
| VRTA11 | FII | fund/fii | Fator Verità FII, CNPJ 11.664.201/0001-00 — OFFICIAL_VERIFIED | **VERIFIED** (admin/gestor roles are not a contradiction) | UNKNOWN (fail-closed) | NO |
| SNAG11 | **FII** | **fund/fi-agro** | SUNO AGRO FIAGRO IMOBILIÁRIO — OFFICIAL_PARTIAL | **PARTIAL** | UNKNOWN (fail-closed) | NO |

### Recorded conflicts (preserved, not resolved by inference)
- **SNAG11 class divergence:** internal API says `FII`; Brapi says `fi-agro`; issuer-official says `FIAGRO
  Imobiliário`. **Preserved as a conflict.** SNAG11 stays `OFFICIAL_PARTIAL` current-identity; FIAGRO is the
  issuer-stated class but is **not** promoted to VERIFIED without the CVM-filed Regulamento. Not inferred from
  the `11` suffix.
- **BPAC11 CNPJ suffix** and **VRTA11 admin/gestor** conflicts remain field-scoped (do not block current identity).
- **KNHY11** was **not** queried and remains `QUARANTINED_CLASSIFICATION_PENDING` (ADR-010, Q-15), independent.

## Net effect

WEGE3, BPAC11, VRTA11 are `CURRENT_IDENTITY_VERIFIED`; SNAG11 is current-identity `PARTIAL`. **No** asset is
`OPERATIONAL_ACTIVATION_AUTHORIZED`; **no** `valid_from`/`valid_to` invented (all `UNKNOWN`); KNHY11 quarantine
intact. This unblocks Phase 2+ build work (which needs current identity, not history) without any live activation.

<rollback>Supersede with a newer amendment citing primary CVM documents; never erase prior EVIDENCE_PENDING history or registered source hashes.</rollback>
