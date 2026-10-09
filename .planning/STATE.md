---
gsd_state_version: 1.0
milestone: v1.0
milestone_name: milestone
status: in-progress
stopped_at: "Cycle 19 (2026-10-09). Implementation vertical hardened across cycles 5–19 on Spring Boot 4.1.1 / Java 21, Flyway V1–V15 (unchanged in cycle 19 — no schema change needed); mvn test = 293 passed, 0 failures/errors. Local authorized commits exist on branch checkpoint/cycle7-reviewed (NO push). This is implementation progress, NOT GSD phase acceptance: 0/8 phases closed, all 57 requirements remain Pending, all 23 assets NOT_AUTHORIZED, workers disabled, Q-19/LEVEL unresolved and fail-closed. SEC-01 DONE; UI-03 DONE for the current outbox contract; UI-01/UI-02 PARTIAL (no real producer snapshot exists — synthetic only). Cycle 19 corrected and independently validated the three P1 + one P2 findings from the Cycle-18 external review of the analytics consumer: P1-A commit() made transaction-sound (no longer @Transactional; REQUIRES_NEW insert + fresh re-read so a Postgres unique-violation loser disposes to NO_OP/CONFLICT with no generic 500, no partial persistence, no SUCCESS audit without a row); P1-B request-size cap proven at the REAL endpoint (RANDOM_PORT Tomcat: declared oversize 413 before materialization, chunked deterministic reject, exactly-at-limit accepted, one-over 413, oversize browser form rejected, no payload echo) + bounded-stream unit proof; P1-C numeric bounds now match NUMERIC(24,12) exactly (reject integer-digits>12 incl. 1E12 before persistence; parse floats as BigDecimal so 999999999999.999999999999 is not rounded to a double); P2 legacy V14 NULL-document_digest reimport is fail-closed REJECTED_CONFLICT_LEGACY_NO_DIGEST (HTTP 409), no fabricated digest, no destructive back-fill. PostgreSQL IT = NOT_RUN (Docker absent); runtime PESSIMISTIC_WRITE = NOT_PROVEN; two new analytics ITs compiled, NOT_RUN."
last_updated: "2026-10-09T17:30:00.000Z"
last_activity: "2026-10-09 (cycle 19) — corrected and independently validated the three P1 + one P2 findings from the Cycle-18 external review of the analytics consumer, before any real-producer connection. P1-A: commit() is no longer @Transactional; the insert runs in a REQUIRES_NEW write tx (insert + SUCCESS audit atomic) and a concurrent unique-violation loser re-reads the winner on a fresh REQUIRES_NEW read tx, disposing by full document identity (IDEMPOTENT_NOOP/REJECTED_CONFLICT) — no generic 500, no partial persistence, no SUCCESS audit without a durable row; an integrity error that is NOT the snapshot-id race is re-thrown. P1-B: the request-size cap is proven at the REAL endpoint (RANDOM_PORT Tomcat + JDK HttpClient) — declared oversize 413 before materialization, chunked oversize deterministic reject, exactly-512-KiB accepted by transport, one-over 413, oversize browser form 413, no payload echo — plus a BoundedRequest unit test proving the chunked stream aborts past MAX. P1-C: numeric bounds now match NUMERIC(24,12) exactly (scale in [0,12] AND integer digits <= 12), so 1E12/-1E12 (decimal and scientific) and scale-13 are rejected before persistence; JSON floats are parsed as BigDecimal (USE_BIG_DECIMAL_FOR_FLOATS) so 999999999999.999999999999 keeps every digit instead of rounding to 1E12. P2: a reimport colliding with a legacy V14 NULL-document_digest row is REJECTED_CONFLICT_LEGACY_NO_DIGEST (HTTP 409) — records-checksum equality is not treated as proof of identity; no digest is fabricated and no destructive back-fill is done. mvn test 293->293; Flyway stays V15 (no schema change). Two new PostgreSQL analytics ITs (concurrent service commit, max-numeric exact round-trip) compiled, NOT_RUN (Docker absent). No real projecao-carteira snapshot consumed. Earlier: cycle 19 full-document binding + per-ticker selection + lossless + strict nested schema + body-size limit; cycle 17 HMAC token + strict parser; cycle 16 analytics consumer contract + V14 + importer. No GSD phase approved/closed; no deploy/live."
progress:
  total_phases: 8
  completed_phases: 0
  total_plans: 4
  completed_plans: 0   # EXECUTED 01-01/01-02 ≠ human-CLOSED; no plan formally approved. (was 1: an overcount — execution is not closure)
  percent: 0
---

# Project State

## Project Reference

See [PROJECT.md](PROJECT.md), updated 2026-10-06.
**Core value:** Explain validated market input and explicit uncertainty in every alert.
**Current focus:** Plans 01-01 and 01-02 executed and verified; DP-01 amendment applied; MVP reliability vertical implemented and HARDENED (cycle 7: late-result outbox fencing, fail-closed pre-dispatch eligibility guard, regressive-revision rejection + supersession cancel, billing-cycle quota provenance; Spring Boot 4.1.1) with green unit/slice tests. Awaiting human phase sign-off (GOV-01/CAT-01). 01-04 (human approval) not started.

## Current Position

Phase: 1 of 8 (evidence reconciled; identity gate corrected via DP-01 amendment; human phase sign-off still pending)
Plan: 01-01 and 01-02 (tasks 01-02-01/02/03) executed. Candidate subset = WEGE3/BPAC11/VRTA11/SNAG11; under DP-01, WEGE3/BPAC11/VRTA11 = CURRENT_IDENTITY_VERIFIED, SNAG11 = PARTIAL; all OPERATIONAL_ACTIVATION = NO; KNHY11 quarantined. 01-04 (human approval) not run.
Status: In progress — Phase 1 NOT closed (human gates GOV-01/CAT-01 pending). MVP reliability vertical IMPLEMENTED and hardened across cycles 5–19 (293 tests green, Boot 4.1.1, Flyway V1–V15) — this is implementation, NOT a phase sign-off. All 57 requirements remain Pending; all 23 assets NOT_AUTHORIZED (enforced by an executable per-asset egress gate, not only documentation). The FIN-01 analytics-snapshot consumer is CONSUMER_VERIFIED_SYNTHETIC (synthetic fixtures only; NOT real projecao-carteira integration). Local authorized commits exist on branch checkpoint/cycle7-reviewed (NO push).
Last activity: 2026-10-09 (cycle 19) — see frontmatter last_activity.
Progress: [██░░░░░░░░] phase closure = 0/8 (unchanged); application implementation PRESENT and hardened (MVP reliability vertical, 293 tests green, Boot 4.1.1, Flyway V1–V15, admin surface+UI, analytics consumer contract v1, cycle 19) — implementation ≠ phase acceptance. PostgreSQL IT NOT_RUN (Docker absent); runtime PESSIMISTIC_WRITE NOT_PROVEN.

## Performance Metrics

- Eight phases; 57 v1 IDs (all original 52 retained + 5 new); five explicit v2 IDs including FUT-05 volatility proposal.
- Application implementation PRESENT and HARDENED (cycle 7): Brapi v2 client (literal symbol + separate remapped flag, one-ticker guard, full ratelimit provenance — reset/remaining/limit/window/serverDate — on BOTH 2xx and 429); quote validation; change calc; CROSSING engine with durable in-transaction rule-state (optimistic lock + replay/ordering guard + regressive-revision rejection: a lower incoming revision is STALE_RULE_REVISION with no eval/mutation/fire, a bump cancels superseded PENDING intents while preserving ACCEPTED/UNKNOWN); REAL proxied @Transactional MonitorProcessingService (atomic + rollback-no-orphan proven); atomic PENDING outbox + post-commit claiming dispatcher with claim_generation so a stale late result cannot overwrite a reconciliation (record/suppress require the exact active IN_FLIGHT claim); a fail-closed DispatchEligibilityGuard (+ RuleRegistry port / EmptyRuleRegistry default) rechecks asset authorization, rule known/current/not-paused, revision, expiry and source-age BEFORE any send, transitioning a denied intent to a terminal no-send (SUPPRESSED/EXPIRED/CANCELLED) with no adapter call; outbox persists rule_revision/source_as_of/intent_created_at/expires_at/episode_epoch/suppression_reason; expired IN_FLIGHT leases QUARANTINE to UNKNOWN_OUTCOME (never resent) with a local OutboxReconciliationService (metrics + proof-gated requeue, no HTTP endpoint). Brapi quota admission is durable single-in-flight, FENCED by owner+monotonic token; ratelimit-reset is OBSERVED as a predicted deadline (billing-cycle provenance: accepted only when window=billing-cycle AND limit=hardLimit, anchored on serverDate+delta) and NEVER zeroes the budget — the counter is zeroed only by an elapsed-deadline rollover confirmation; conservative high-water consumed=max(local,hardLimit-remaining), never lowered. Per-asset OperationalAuthorization egress gate (all 23 NOT_AUTHORIZED, SNAG11 PARTIAL, KNHY11 QUARANTINED) checked BEFORE calendar/quota. quote_observation persists provider_remapped + provider_contract. Disabled injectable scheduler with UNKNOWN trading calendar. Unit/slice tests green (293) on Spring Boot 4.1.1, hardened across cycles 5–19 (loopback-only admin surface + UI RBAC + proven real session expiry SEC-01, append-only audit ledger V13, UI-01 asset freshness + analytics context / UI-03 full-lifecycle alert view / UI-02 three-dimension readiness + analytics import history, a fail-closed analytics-snapshot CONSUMER contract v1 with an ADMIN preview→commit importer and V14 provenance tables (cycle-17 hardened: an HMAC-bound preview token over actor/schema/snapshot/content/purpose/expiry, a strict parser rejecting duplicate JSON keys / unknown top-level fields / excess depth / non-finite / out-of-bound numbers, explicit time/as-of invariants, asOf-based current-context selection, and a proof a synthetic import can never become runtime VERIFIED), and PostgreSQL concurrency ITs with a transaction-bound pg_backend_pid()+pg_blocking_pids() lock-wait proof and no swallowed race failures); PostgreSQL/Flyway Testcontainers ITs (V1–V15, two-consumer + quota races + abandon-terminal + real-fence lifecycle concurrency + V14 analytics schema + concurrent same-snapshotId commit, clock-deterministic) written but NOT RUN (Docker absent on host). Spring 4.1 migration probed in a throwaway copy (3.5.6 green; 4.1.0 blocked only by Jackson 2→3). Green tests ≠ runtime/phase acceptance.
- GSD auto-advance/auto-chain disabled; local authorized commits exist on branch checkpoint/cycle7-reviewed (NO push); no deploy, no live polling, no real WAHA send.

## Accumulated Context

### Proposals, not approved defaults

- Phase4 single-leaf absolute-price→state/outbox→WAHA remains small, with OPS/security/recovery.
- Phase5A gated percent/AND-OR independent of history;5B SMA/RSI/EMA9/21/finalized volume.
- Volatility explicitly proposed v2 pending Q-22; requested feature not silently removed.
- CROSSING versus optionally initial LEVEL both defined; default UNSELECTED and initial opt-ins require Q-19 approval.
- Durable initial token/episode/latch/rearm/cooldown survive edits/resume/restart; recovery uncertainty quarantines.
- Phase7A synthetic consumer distinct from7B real producer. FIN-06 blocked separately; synthetic does not complete real integration/full Phase7.
- Graham PARTIAL/PVP snapshot READY-history PARTIAL/B&H PARTIAL remain public context; private data excluded, no automatic recommendation.
- Source-age45min hard guard distinct from5min healthy objective and30min polling; no<=60min event/delivery guarantee.
- Shared WAHA scoped isolation/exact outbound contract, potential Brapi scraper quota sharing, owner backup and stack/library compatibility are gates.
- KNHY11 individual quarantine; named valid subset can proceed only by explicit scope/dependency approval, no23/23/forced Phase1 completion.
- Separate COTAHIST RAW/Brapi close/adjusted series, scraper disabled, audit facts/local checks/proposals distinguished.

### Completed documentary work

- Earlier ten-file/23-row reconciliation and hashes preserved.
- Original52 IDs retained, RUL-06/07 IND-04/05 FIN-06 added; FUT-05 explicitly proposedv2.
- PROJECT/REQUIREMENTS/ROADMAP/ADRs/CONTRACTS/SPEC/questions/reconciliation/STATE and supporting docs updated.
- Fresh [critical review](../docs/planning/CRITICAL-REVIEW.md) and [validation](../docs/planning/PLANNING-VALIDATION.md) record coverage, dependencies and remaining disagreements.

### Pending approvals and blockers

- Q-17 financial context-only;Q-18 phased/subset delivery;Q-19 initial mode;Q-21 composition;Q-22volatility;Q-24 best-effort targets.
- Q-15 official KNHY11 identity/category; valid assets reviewed independently.
- Calendar/session/action/target/stale/age/rearm/expiry/recipient policies and account budget/sharing.
- Q-09/25 exact outbound WAHA edition/version/auth/send/idempotency plus shared isolation; inbound later separately.
- Q-13 owner backup/encryption/key/retention/RPO/RTO/high-water/restore authority.
- Q-14/27 pinned Java/Boot/build/PostgreSQL/indicator library/license/API/numerical compatibility.
- Q-20 previous-close date/basis/actions; technical series/finality/units/seed assurance.
- Q-23/FIN-06 separate upstream authorization and actual public producer/fidelity; optional history exporter separately.
- No current authorization for PUSH, phase closure, external-DB migrations, external/sibling access or live activation. Local authorized commits exist on branch checkpoint/cycle7-reviewed (NO push); implementation ≠ phase acceptance.

## Session Continuity

Last session: 2026-10-08T23:00:00.000Z
Stopped at: Cycle 17 — adversarially hardened the cycle-16 analytics import boundary: an HMAC-SHA256 preview→commit token bound to {purpose|schemaVersion|snapshotId|checksum|actor|issuedAt|ttl} (constant-time verify; cannot be replayed across actor/content/snapshot/schema or after expiry; tamper/wrong-purpose/malformed reject fail-closed; no secret or raw payload in the token); a strict parser (STRICT_DUPLICATE_DETECTION, unknown-top-level-field rejection, depth<=12, non-finite rejection, numeric scale/precision/magnitude bounds, duplicate-ticker rejection); canonical-checksum golden tests (reorder/whitespace => same, value/asOf => different); time/as-of invariants (generatedAt not future, per-record asOf<=marketAsOf, importedAt from the injected clock); a concurrent same-snapshotId commit IT proving exactly one durable row; asOf-based current-context selection (older-asOf imported later does not become current); and a proof that a synthetic import can never become runtime VERIFIED. Implementation hardened across cycles 5–19 with 293 green unit/slice tests on Spring Boot 4.1.1 / Java 21, Flyway V1–V15. PostgreSQL/Flyway Testcontainers ITs (transport-only OutboxPostgresIT + real-fence LifecycleFencePostgresIT + V14 analytics schema + concurrent same-snapshotId commit) compile but are NOT RUN (Docker absent); runtime PESSIMISTIC_WRITE NOT_PROVEN. The analytics consumer is CONSUMER_VERIFIED_SYNTHETIC — NO real projecao-carteira snapshot consumed. Local authorized commits exist on branch checkpoint/cycle7-reviewed (NO push). No GSD phase approved/closed; no live/deploy; all 57 requirements Pending; all 23 assets NOT_AUTHORIZED; KNHY11 quarantined; SNAG11 FII/FIAGRO conflict preserved. SEC-01 DONE; UI-03 DONE for the current outbox contract; UI-01/UI-02 PARTIAL.
Next eligible action: Human review + Phase-1 identity dispositions; run `mvn verify -Pdocker-it` where Docker is available to exercise V1–V15 + the concurrency races + the concurrent-import IT on real PostgreSQL; integrate a REAL authorized projecao-carteira producer snapshot (per the 11-point acceptance gate) to move the analytics consumer past CONSUMER_VERIFIED_SYNTHETIC; resolve Q-19 for RUL-03/RUL-05/LEVEL. Do not auto-advance; 01-04 (human approval) and GOV-01/CAT-01 remain human gates.
Boundary: Current repository only. No services, credentials, sibling inspection, upstream changes, migrations, real messages, commit or deployment. No automatic phase advance.
