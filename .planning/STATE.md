---
gsd_state_version: 1.0
milestone: v1.0
milestone_name: milestone
status: in-progress
stopped_at: "Cycle 15 (2026-10-08). Implementation vertical hardened across cycles 5–15 on Spring Boot 4.1.1 / Java 21, Flyway V1–V13; mvn test = 216 passed, 0 failures/errors. Local authorized commits exist on branch checkpoint/cycle7-reviewed (NO push). This is implementation progress, NOT GSD phase acceptance: 0/8 phases closed, all 57 requirements remain Pending, all 23 assets NOT_AUTHORIZED, workers disabled, Q-19/LEVEL unresolved and fail-closed. SEC-01 DONE (real loopback-only admin surface + proven session expiry); UI-03 DONE for the current outbox contract; UI-01/UI-02 PARTIAL. PostgreSQL IT = NOT_RUN (Docker absent); runtime PESSIMISTIC_WRITE = NOT_PROVEN."
last_updated: "2026-10-08T22:00:00.000Z"
last_activity: "2026-10-08 (cycle 15) — PostgreSQL lock-proof connection identity fixed (the lock-wait proof now reads pg_backend_pid() and takes the PESSIMISTIC_WRITE lock through the SAME Hibernate/JPA connection via EntityManager.unwrap(Session).doReturningWork, so the pid proven blocked by pg_blocking_pids() is provably the lock holder, not an unrelated pooled connection); removed the last swallowed concurrency failures (SchedulerTransactionTest first-creation race and RuleLifecycleFenceTest pause-vs-process now rethrow and assert the exact legal exception type via Future.get — a uniqueness/optimistic conflict is the only accepted loss, anything else fails); refined UI-02 readiness into three independent dimensions (wiringStatus / operationalStatus / runtimeStatus) so code integration is never conflated with authorization or runtime verification; completed README/STATE factual-drift correction to 216 tests / Boot 4.1.1 / V1–V13 / local-commit reality. PostgreSQL IT still NOT_RUN (Docker absent) — ITs compile; runtime concurrency NOT claimed proven. Earlier: cycle 7 dispatch TOCTOU/transport-type closure; cycle 10 lifecycle fence + loopback admin; cycle 11 security hardening + audit ledger V13 + thin UI; cycle 12 UI RBAC + session expiry + UI-01; cycle 13 UI-03 full lifecycle + true concurrency races; cycle 14 concurrency-test integrity + UI-03 ordering + portable ZIP + UI-02 readiness. No GSD phase approved/closed; no deploy/live."
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
Status: In progress — Phase 1 NOT closed (human gates GOV-01/CAT-01 pending). MVP reliability vertical IMPLEMENTED and hardened across cycles 5–15 (216 tests green, Boot 4.1.1, Flyway V1–V13) — this is implementation, NOT a phase sign-off. All 57 requirements remain Pending; all 23 assets NOT_AUTHORIZED (enforced by an executable per-asset egress gate, not only documentation). Local authorized commits exist on branch checkpoint/cycle7-reviewed (NO push).
Last activity: 2026-10-08 (cycle 15) — see frontmatter last_activity.
Progress: [██░░░░░░░░] phase closure = 0/8 (unchanged); application implementation PRESENT and hardened (MVP reliability vertical, 216 tests green, Boot 4.1.1, Flyway V1–V13, admin surface+UI, cycle 15) — implementation ≠ phase acceptance. PostgreSQL IT NOT_RUN (Docker absent); runtime PESSIMISTIC_WRITE NOT_PROVEN.

## Performance Metrics

- Eight phases; 57 v1 IDs (all original 52 retained + 5 new); five explicit v2 IDs including FUT-05 volatility proposal.
- Application implementation PRESENT and HARDENED (cycle 7): Brapi v2 client (literal symbol + separate remapped flag, one-ticker guard, full ratelimit provenance — reset/remaining/limit/window/serverDate — on BOTH 2xx and 429); quote validation; change calc; CROSSING engine with durable in-transaction rule-state (optimistic lock + replay/ordering guard + regressive-revision rejection: a lower incoming revision is STALE_RULE_REVISION with no eval/mutation/fire, a bump cancels superseded PENDING intents while preserving ACCEPTED/UNKNOWN); REAL proxied @Transactional MonitorProcessingService (atomic + rollback-no-orphan proven); atomic PENDING outbox + post-commit claiming dispatcher with claim_generation so a stale late result cannot overwrite a reconciliation (record/suppress require the exact active IN_FLIGHT claim); a fail-closed DispatchEligibilityGuard (+ RuleRegistry port / EmptyRuleRegistry default) rechecks asset authorization, rule known/current/not-paused, revision, expiry and source-age BEFORE any send, transitioning a denied intent to a terminal no-send (SUPPRESSED/EXPIRED/CANCELLED) with no adapter call; outbox persists rule_revision/source_as_of/intent_created_at/expires_at/episode_epoch/suppression_reason; expired IN_FLIGHT leases QUARANTINE to UNKNOWN_OUTCOME (never resent) with a local OutboxReconciliationService (metrics + proof-gated requeue, no HTTP endpoint). Brapi quota admission is durable single-in-flight, FENCED by owner+monotonic token; ratelimit-reset is OBSERVED as a predicted deadline (billing-cycle provenance: accepted only when window=billing-cycle AND limit=hardLimit, anchored on serverDate+delta) and NEVER zeroes the budget — the counter is zeroed only by an elapsed-deadline rollover confirmation; conservative high-water consumed=max(local,hardLimit-remaining), never lowered. Per-asset OperationalAuthorization egress gate (all 23 NOT_AUTHORIZED, SNAG11 PARTIAL, KNHY11 QUARANTINED) checked BEFORE calendar/quota. quote_observation persists provider_remapped + provider_contract. Disabled injectable scheduler with UNKNOWN trading calendar. Unit/slice tests green (216) on Spring Boot 4.1.1, hardened across cycles 5–15 (loopback-only admin surface + UI RBAC + proven real session expiry SEC-01, append-only audit ledger V13, UI-01 asset freshness / UI-03 full-lifecycle alert view / UI-02 three-dimension readiness, and PostgreSQL concurrency ITs with a transaction-bound pg_backend_pid()+pg_blocking_pids() lock-wait proof and no swallowed race failures); PostgreSQL/Flyway Testcontainers ITs (V1–V13, two-consumer + quota races + abandon-terminal + real-fence lifecycle concurrency, clock-deterministic) written but NOT RUN (Docker absent on host). Spring 4.1 migration probed in a throwaway copy (3.5.6 green; 4.1.0 blocked only by Jackson 2→3). Green tests ≠ runtime/phase acceptance.
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

Last session: 2026-10-08T22:00:00.000Z
Stopped at: Cycle 15 — fixed the PostgreSQL lock-proof connection identity (the lock-wait proof reads pg_backend_pid() and takes the PESSIMISTIC_WRITE lock through the SAME Hibernate/JPA connection via EntityManager.unwrap(Session).doReturningWork, so the pid proven blocked by pg_blocking_pids() is provably the lock holder); removed the last swallowed concurrency failures (SchedulerTransactionTest first-creation race and RuleLifecycleFenceTest pause-vs-process now rethrow and assert the exact legal exception type — a uniqueness/optimistic conflict is the only accepted loss); refined UI-02 readiness into three independent dimensions (wiring/operational/runtime); completed README/STATE factual-drift correction to 216 tests / Boot 4.1.1 / V1–V13 / local-commit reality. Implementation hardened across cycles 5–15 with 216 green unit/slice tests on Spring Boot 4.1.1 / Java 21, Flyway V1–V13. PostgreSQL/Flyway Testcontainers ITs (transport-only OutboxPostgresIT + real-fence LifecycleFencePostgresIT with true concurrent races + transaction-bound lock-wait proof) compile but are NOT RUN (Docker absent); runtime PESSIMISTIC_WRITE NOT_PROVEN. Local authorized commits exist on branch checkpoint/cycle7-reviewed (NO push). No GSD phase approved/closed; no live/deploy; all 57 requirements Pending; all 23 assets NOT_AUTHORIZED; KNHY11 quarantined; SNAG11 FII/FIAGRO conflict preserved. SEC-01 DONE; UI-03 DONE for the current outbox contract; UI-01/UI-02 PARTIAL.
Next eligible action: Human review + Phase-1 identity dispositions; run `mvn verify -Pdocker-it` where Docker is available to exercise V1–V13 + the true concurrency races on real PostgreSQL (the only remaining runtime proof of PESSIMISTIC_WRITE); integrate real daily-indicator/Python context to complete UI-01 + UI-02 breadth; resolve Q-19 for RUL-03/RUL-05/LEVEL. Do not auto-advance; 01-04 (human approval) and GOV-01/CAT-01 remain human gates.
Boundary: Current repository only. No services, credentials, sibling inspection, upstream changes, migrations, real messages, commit or deployment. No automatic phase advance.
