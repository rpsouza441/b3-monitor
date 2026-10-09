# Phase 1 — Reused Evidence and Planning Guidance

**Date:** 2026-10-06
**Status:** REUSED_EXISTING_RECONCILIATION; not a new global audit
**Scope:** GOV-01, GOV-02, CAT-01, CAT-02
**Authority:** 01-CONTEXT.md, current CONTRACTS/ADRs, EVIDENCE and ASSET-CATALOG.

## Existing findings needed by this phase

| Planning item | Existing source | Consequence |
|---------------|-----------------|-------------|
| Ten originals/hash inventory already reconciled | docs/references/EVIDENCE.md E-01 and source table | Cite existing sections/hashes; preserve originals, do not recalculate financial conclusions |
| 23 identities/provider taxonomy | E-05, B2, B4, docs/planning/ASSET-CATALOG.md | Inventory all23; count6 stock/3 unit/13 FII/1 FIAGRO is provider evidence, not full activation approval |
| KNHY11 FII versus unsupported Python FIAGRO | ADR-010, P1§2, B2row22/B4; Q-15 | Independent quarantine until official local evidence; user FII/paper statement is not official |
| Broader Python classes are not instrument identity | P2§§2–3 and current catalog | Unit/FIAGRO mapping explicit; no suffix inference, fake ISIN/CNPJ or inferred validFrom |
| Event/identity evidence incomplete | E-10, Q-02/06, CAT-02 | Dated mapping/correction policy and isolated synthetic rename/delist/reuse cases required |
| Audits not upstream verification | EVIDENCE evidence levels and current validation record | Distinguish documentary/local/report/proposal; no runtime/activation claim |

No new technical research, original report full reread, financial formula audit, API probes, sibling access or Internet search was performed. All other source capabilities remain in their owning phases.

## Phase design

Four small plans with genuine documentary dependencies:01-01 anchors source evidence;01-02 validates catalog/selected subset;01-03 records identity invariants and synthetic cases;01-04 integrates both outputs and presents human gates. Plans02 and03 can be the same wave because their output files differ;03 uses generic isolated synthetic identities and existing catalog rather than a selected-subset result. Plan04 needs all three outputs.

No application implementation, migration, schema push, AI/UI contract or external setup in this phase. Output file paths are new planned documents under docs/planning/phase-1; planning must not create them as if execution had occurred.

## Validation Architecture

Use installed GSD read-only plan/frontmatter/decision/consistency/health commands for planning structure. Future phase execution uses local SHA-256 versus existing evidence inventory, exact23-identity and selected-subset set checks, explicit status/evidence/date/approval fields, manual independent identity-case tracing and repository-local document assertions. Do not install a test framework or create app tests for documentary work.

Every task has its own verification and expected result. Human selection/official proof/sign-off are intentionally manual, with blocking checkpoints. Plan acceptance is not phase acceptance: plans can be structurally valid while official evidence and subset decisions remain open. Full CAT-01/Phase1 cannot close from a partial subset. No runtime CAT-02 assurance follows solely from paper cases; later persistence integration must implement and reverify the same invariants under its own authorization.
