# Independent planning design review

## Current author reconciliation — 2026-10-05

The independent review below is historical and accurately describes its then-missing sources/old phases. It was not rerun by an independent agent this session. Current author read all ten original reports/23 rows and revised [divergence review](../../docs/architecture/DESIGN-REVIEW.md), [evidence](../../docs/references/EVIDENCE.md), [registry](../../docs/planning/PYTHON-REUSE.md), contracts/ADRs/requirements/roadmap/state. Missing-file/list blockers closed; official KNHY11, producer/export, calendar/policies and WAHA gates remain. Current policy is 30% reserve/two Brapi attempts, Phase 4 first safe price+WAHA, Phase 5 technicals/6 commands/7 context. Current correction2026-10-06 preserves all52 original IDs and adds5 (57v1 Pending); Phase5A percentage/AND-OR,5B SMA/RSI/EMA/volume, Phase7A synthetic consumer versus separately authorized7B real producer. Default CROSSING/LEVEL unselected; volatility proposedv2 pending human scope approval. See the fresh inline [critical review](../../docs/planning/CRITICAL-REVIEW.md). Historical independent reviewer content below is retained verbatim. See [review packet](../../docs/planning/RECONCILIATION.md) and [current validation](../../docs/planning/PLANNING-VALIDATION.md).

## Historical independent review and follow-up (not current evidence status)

Date: 2026-10-05, America/Sao_Paulo. Scope: documentation only. Reviewed PROJECT, SPEC, REQUIREMENTS, OPEN-QUESTIONS, GSD-WORKFLOW, ARCHITECTURE, ADRS, DESIGN-REVIEW, CONTRACTS, SECURITY, TESTING-STRATEGY and both research notes. ROADMAP was being authored independently and is outside this review's coverage.

**Initial verdict: REQUIRES DOCUMENT RECONCILIATION; NOT FINALIZED OR IMPLEMENTATION-READY.** The follow-up disposition below supersedes the internal-document finding statuses while preserving this initial review. No source-specific finding is independently verified here. The parent review reports the ten exact source paths remain absent despite the user's copy notification; source evidence and the CSV remain blockers, not evidence of contradictions among the actual audits. No external services, other repositories, credentials, runtime code, tests or commits were accessed or produced in this review.

Severity: HIGH would threaten the evidence or operational boundary; MEDIUM creates ambiguous requirements or contracts; LOW is editorial. Findings below are MEDIUM, OPEN at initial review. Existing evidence blockers remain HIGH activation dependencies rather than defects that can be resolved by drafting.

## Findings and minimum fixes

| ID | Severity / status | Evidence | Implication | Minimum fix |
|---|---|---|---|---|
| REV-01 | MEDIUM / OPEN | INTEGRATIONS.md, Decisions item 5 and "Optional scraper contract: deferred", excludes scraper from the first milestone; REQUIREMENTS FIN-05 and ADR-007 include an optional verified cache-only v1 contract | The user's latest complementary-scraper priority has different scope in research and specification | Update research to optional v1 contract/adapter, disabled unless an existing non-refreshing interface is proven. Keep scraper changes and refresh/TTL work excluded |
| REV-02 | MEDIUM / OPEN | INTEGRATIONS.md, WAHA webhook command boundary, specifies read-only commands and dashboard-only rule changes; SPEC workflow item 7, REQUIREMENTS CMD-03 and CONTRACTS allow pause/resume of an existing authorized rule | Implementers may omit planned commands or authorize a broader mutation surface | Use the current bounded command set consistently: status, quote, pause/resume-existing-rule; separate transport auth, principal/action authorization and ownership. Add synthetic authorized/unauthorized pause/resume acceptance cases |
| REV-03 | MEDIUM / OPEN | MARKET-DATA.md quota table limits requests to one retry; SPEC defaults allow up to three total attempts per operation | Retry sizing and admission behavior have two provisional defaults | Pick one declared draft cap or explicitly distinguish adapter-specific lower caps under a common maximum. Count initial attempt plus each retry, and reference the same policy revision from capacity analysis |
| REV-04 | MEDIUM / OPEN | REQUIREMENTS NOT-02 says missing confirmation is UNKNOWN; INTEGRATIONS state table and fixture NOT-02 preserve ACCEPTED/unconfirmed after verified handoff; CONTRACTS distinguishes ACCEPTED from UNKNOWN_OUTCOME | Absence of a receipt could erase known submission evidence or invoke inappropriate fallback | Preserve ACCEPTED as submission evidence; use an independent unconfirmed delivery status or a defined timeout transition without losing that evidence. Reserve UNKNOWN_OUTCOME for ambiguous send execution/status, and retain the default no-blind-retry/fallback gate |
| REV-05 | MEDIUM / OPEN | INTEGRATIONS import step 6 requires explicit producer revision/supersedes relation to replace equal-date results; its envelope/metric field tables do not declare that relation | The importer must either reject corrections indefinitely or invent an undeclared field despite strict schema allowlists | Add optional explicit revision/supersedes fields with scope, referential checks and cycle/conflict rules, or state that equal-date replacement remains operator-reviewed and unsupported until a later contract revision |
| REV-06 | MEDIUM / OPEN | CONTRACTS COTAHIST paragraph describes dataset/price fields but financial section supplies the import lifecycle; HIS-04 says "specify" historical reuse while FIN-03 only owns financial snapshot idempotency | The first milestone's prioritized official-history path lacks an explicit owning requirement for validated historical bundle activation, duplicate/conflict handling and correction lifecycle | State that HIS-04 includes the independent history-import consumer contract and acceptance scenarios. Define approved producer/export identity, digest/conflict policy, bounded validation, atomic activation/quarantine and immutable corrections, reusing import infrastructure without conflating financial-context eligibility with technical-history eligibility |
| REV-07 | MEDIUM / OPEN | CONTRACTS indicator section says daily SMA crossing needs two eligible outputs; SPEC initial rules include sampled quote crossing a daily SMA | A sampled comparison can use two distinct quote/SMA input pairs with the same completed-day SMA; requiring two different SMA dates imposes a different behavior | Distinguish daily-close/SMA crossing (two daily outputs) from sampled quote/daily-SMA crossing (two coherent eligible sampled pairs, possibly the same SMA result). Define equality, daily-SMA revision changes and maximum daily-input age at the phase gate |

Research also calls Python/B3 history a future decision in its blocker list. Align that wording with the prioritized provisional COTAHIST v1 history path; actual producer layout, availability and readiness must remain unresolved. `FULL` in Q-07 and `READY` in proposed snapshot enums must not be treated as equivalents without the audited mapping; the existing fail-closed mapping gate correctly prevents that inference.

## Required six assumption challenges

| Challenge | Status | Review evidence / remaining gate |
|---|---|---|
| One-session Brapi delay proves permanent freshness | COVERED, evidence unresolved | Source timestamps, ingestion/evaluation ages, versioned policy, changed-contract suppression. No SLA claim; reconcile RESULTADO and SCHEMA |
| Raw and adjusted prices combine safely | COVERED, evidence unresolved | Separate series/method/revisions, overlap/action compatibility, no raw continuity assumption across splits. Reconcile Brapi/COTAHIST/Python mappings |
| Historical portfolio valuation is an alert signal | COVERED | All imported context excluded from v1 rule operands, asset-only privacy boundary, producer ownership |
| Scraper has OHLCV history | COVERED, scope wording needs REV-01 | No scraper quote/history promotion, cache-only non-refreshing contract, disable unsupported interface |
| WAHA acceptance means recipient delivery | COVERED, state wording needs REV-04 | Outbox logical dedup, explicit external uncertainty, verified confirmation mapping, controlled fallback and post-send crash cases |
| Every fundamental calculation is ready | COVERED, inventory unresolved | Preserve per-component states, independent age/quality/coverage/assurance, no parent-to-child promotion. Actual component registry cannot be filled without FINANCIAL_COMPONENTS |

## Boundaries checked

Readiness promotion is prevented explicitly, including fresh-but-diagnostic and READY-but-stale cases. Producer assertions and local assurance are separate. Price source/basis/method/date/corporate-action compatibility is fail closed, and transport receipt never creates market freshness. No audited exporter, endpoint, COTAHIST layout, exact asset list or WAHA capability is represented as independently verified.

Quota accounting covers all attempts, shared consumers, queued reset boundaries, conservative crash consumption, and the 12,000 routine/3,000 reserve proposal. Restore requires independently preserved consumption high-water data and collector shutdown; insufficient reconciliation conservatively exhausts remaining cycle allowance. Delivery intents restored from a backup stay quarantined when external execution is uncertain. These controls are coherent proposals requiring future synthetic proof, not executed guarantees.

Project independence is preserved by asset-only independent exports, no shared databases/process invocation/source mounts, and optional scraper cache access. COTAHIST via Python is a proposed boundary, not an existing exporter claim. Any producer change needs separate authorization in that project. The monitor can run with those applications unavailable, while historical/context readiness remains visibly constrained.

The 9,016 and 12,144 request scenarios are conditional arithmetic and clearly labeled illustrative, not verified B3 schedules or provider limits. No claim of completed source reconciliation, validated functionality, successful runtime tests or finalized architecture was found in the main drafts. Installed-GSD inspection statements are parent-recorded workflow evidence and were not independently re-executed here.

## Human-review disposition

Resolve the internal wording findings before treating this as a consistent review packet. Preserve all audit-dependent ADRs as PROPOSED and Phase 1 incomplete until all ten documents and all 23 CSV rows are actually read and reconciled. Record real cross-audit divergences with path/section, confidence, impact and resolving ADR; do not manufacture divergences from missing evidence. Human review of these drafts must not authorize implementation or live integration by implication.

## Follow-up disposition after document corrections

Rechecked on 2026-10-05 using the relocated supporting files under `docs/planning/` (SPEC, OPEN-QUESTIONS and GSD-WORKFLOW), current REQUIREMENTS, CONTRACTS, corrected research and the relevant ROADMAP phase 4/5/7 sections. This is a bounded correction recheck, not a new source audit or full independent roadmap verification.

**Current verdict: original seven findings RESOLVED AS DOCUMENTATION; proposed packet suitable for human review with source-evidence blockers retained.** This does not finalize architecture, verify capabilities, complete Phase 1 or authorize implementation.

| ID | Follow-up status | Correction checked |
|---|---|---|
| REV-01 | RESOLVED AS DOCUMENTATION | Integration scope/decision/section now includes complementary scraper fundamentals in provisional v1; unproven cache-only interface remains disabled and legacy changes remain excluded |
| REV-02 | RESOLVED AS DOCUMENTATION | Pause/resume of an existing owned rule now has explicit transport/principal/action/replay gates, transactional mutation/reply handling and authorized/unauthorized/race fixtures; broader mutations stay outside command scope |
| REV-03 | RESOLVED AS DOCUMENTATION | Market research now states three total attempts (initial plus at most two retries), further reduced by safety, budget and deadline gates, consistent with SPEC |
| REV-04 | RESOLVED AS DOCUMENTATION | NOT-02, canonical CONTRACTS and ROADMAP retain transport ACCEPTED separately from delivery UNKNOWN/CONFIRMED; ambiguous execution is UNKNOWN_OUTCOME and absence of confirmation alone does not justify retry/fallback |
| REV-05 | RESOLVED AS DOCUMENTATION | Proposed envelope revision/supersedes_export_id and metric_id/revision/supersedes_metric_id fields now declare identity/compatible scope/cycle/conflict checks, subject to registered-schema agreement; unknown producer support remains unresolved |
| REV-06 | RESOLVED AS DOCUMENTATION | HIS-04, CONTRACTS and ROADMAP phase 4 now own bounded historical whole-bundle staging, producer/export identity/digest, identical-versus-conflicting imports, atomic activation, immutable corrections and rollback; FIN-03 stays financial-only |
| REV-07 | RESOLVED AS DOCUMENTATION | Canonical indicator contract and market research distinguish two daily outputs from two sampled quote/SMA pairs, potentially sharing one completed-day SMA; age/basis/sample-gap/equality and threshold-revision baseline policies are explicit gates |

Two LOW wording items were noted at that recheck. Parent-author follow-up resolved both after the reviewer returned: the integration command paragraph now makes rule state part of `status`, without another command; its delivery table explicitly aliases `DELIVERED` to independent delivery CONFIRMED while retaining transport ACCEPTED and distinguishing unknown send execution. These final wording edits are author-checked, not an additional independent source audit. No documentation finding remains open; source-evidence blockers are unchanged.

The six required assumption challenges remain covered. Independently verified source contents, actual component readiness, 23-asset identity/coverage, Brapi charging/time mappings, COTAHIST layout/ingestion readiness, scraper cache semantics and WAHA auth/delivery capabilities remain unresolved. The parent reports exact checks still find all ten sources absent. Retain proposed ADR status, fail-closed activation gates and the human-review stop.
