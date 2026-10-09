# Phase 1: Evidence Reconciliation and Asset Identity - Context

**Gathered:** 2026-10-06
**Status:** Ready for planning; pending decisions remain explicit human checkpoints
**Workflow:** $gsd-discuss-phase 1; installed GSD 1.2.0, interactive, no chain/auto/commit

<domain>
## Phase Boundary

Plan GOV-01, GOV-02, CAT-01 and CAT-02 only. Reuse reconciled local evidence; prepare reviewable governance, asset-validation and identity-versioning tasks. No global re-audit, phase execution, application code, migration, external service, sibling access, credentials, commit or deployment. Planning completion is not phase completion or activation approval.
</domain>

<decisions>
## Implementation Decisions

### Scope and authority
- **D-01:** Only GOV-01, GOV-02, CAT-01 and CAT-02 may appear as owned requirements in Phase 1 plans; preserve all 57 v1 IDs and existing owners/statuses.
- **D-02:** Current docs/contracts/CONTRACTS.md and docs/architecture/ADRS.md, especially ADR-010, govern. Historical research cannot restore superseded quota, initialization, integration or sequencing policies.
- **D-03:** Reuse the ten already-verified local reports and docs/references/EVIDENCE.md. Use targeted identity/source citations and integrity checks; do not re-run global financial/provider audits, original ZIP validation, upstream tests or external research.

### Catalog and subset gates
- **D-04:** Keep all 23 assets in the documentary inventory. Prepare per-asset validation and a named initial-subset selection/approval checkpoint; no list is selected or approved now. Documentary identity approval, downstream scope approval, full 23-asset acceptance and live activation are separate decisions.
- **D-05:** KNHY11 remains QUARANTINED_CLASSIFICATION_PENDING until repository-local official evidence is validated. User said "knhy11 é fii de papel"; retain FII/paper as USER_REPORTED information, not official/local verification or activation approval. Do not relabel it FIAGRO automatically or block other independently valid assets.
- **D-06:** Use immutable asset identity and dated, versioned provider/ticker mappings; unknown ISIN/CNPJ/validity remain unknown. Plan synthetic rename/delisting/ticker-reuse/correction cases preserving previous observations and evidence, with no automatic corporate-action price transformation.

### Governance and acceptance
- **D-07:** Record human decisions with exact scope, approver, date, artifact revision/digest and rationale. A subset gate cannot check off full CAT-01 or Phase 1; missing official identity or owner decisions stay pending. Broader ADR/policy approvals are inventoried, not silently granted.
- **D-08:** Plans have small tasks, existing evidence pointers, explicit expected acceptance results, failure handling, rollback and closure gates. Validate requirement and D-ID coverage, task/frontmatter structure, dependencies, current GSD consistency and health; do not claim execution acceptance.
- **D-09:** Disable all automatic continuation and commits for this invocation regardless of commit_docs=true. Use installed GSD operations for state/roadmap changes; stop after planned-artifact verification.

### Planning implementation discretion
File names, task grouping and documentary verification commands are planning mechanics delegated by the request. No discretion to choose the subset, approve KNHY11, accept proposed policy defaults or alter requirement scope.
</decisions>

<canonical_refs>
## Canonical References

Paths are repository-root relative. Current authority outranks historical research. No remote material is required or authorized.

### Phase and domain authority
- .planning/PROJECT.md
- .planning/REQUIREMENTS.md — GOV-01/02 and CAT-01/02; remaining IDs for preservation only
- .planning/ROADMAP.md — Phase 1 and approved-subset dependency gate
- .planning/STATE.md
- .planning/config.json — auto_advance and _auto_chain_active remain false
- docs/contracts/CONTRACTS.md — Asset catalog and collection; independent eligibility
- docs/architecture/ADRS.md — ADR-010, ADR-008 and governance consequences
- docs/planning/SPEC.md
- docs/planning/ASSET-CATALOG.md
- docs/planning/OPEN-QUESTIONS.md — Q-02/Q-15/Q-18 and approval inventory only
- docs/planning/RECONCILIATION.md
- docs/planning/CRITICAL-REVIEW.md
- docs/planning/PLANNING-VALIDATION.md

### Existing evidence — preserve originals
- docs/references/EVIDENCE.md — B1–B4, S1–S3, P1–P3 and registered hashes
- docs/references/brapi/RESULTADO.md
- docs/references/brapi/COBERTURA.csv
- docs/references/brapi/CONSUMO.md
- docs/references/brapi/SCHEMA.md
- docs/references/scraper/SCRAPER_AUDIT.md
- docs/references/scraper/DATA_INVENTORY.md
- docs/references/scraper/INTEGRATION_OPTIONS.md
- docs/references/carteira/MONITORING_REUSE_AUDIT.md
- docs/references/carteira/FINANCIAL_COMPONENTS.md
- docs/references/carteira/INTEGRATION_RECOMMENDATION.md
</canonical_refs>

<code_context>
## Existing Assets and Patterns

No application implementation or phase code maps exist. Reusable assets are EVIDENCE.md (cited evidence/hashes/levels), ASSET-CATALOG.md (23-row catalog), OPEN-QUESTIONS.md (dispositions) and canonical GSD root documents. New phase artifacts follow installed context/plan/validation templates; later documentary deliverables must reference these assets rather than replace source reports. No Java, database, service or UI integration is in this phase.
</code_context>

<specifics>
## Discussion Evidence and Pending Choices

The sole additional user reply is "knhy11 é fii de papel". It corroborates the provisional FII interpretation as a user statement; official evidence remains absent. The reply did not name a subset or select a question option. Membership and approval therefore stay OPEN in a future checkpoint under the user's instruction to document human decisions; no option is recorded as approved.
</specifics>

<deferred>
## Outside Phase 1

Quote polling/quota/calendars/actions, rule mode/defaults, notification latency, WAHA/SMTP, indicators, Python exporter, backups/runtime versions and UI belong to their current owning phases. Phase 1 can record their approval status under GOV-02 without choosing or implementing them. Official KNHY11 evidence and named subset approval are in-scope human gates, not deferred features.
</deferred>

*Phase: 01-evidence-reconciliation-and-asset-identity*
*Context gathered: 2026-10-06*
