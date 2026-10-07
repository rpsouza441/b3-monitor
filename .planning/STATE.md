---
gsd_state_version: 1.0
milestone: v1.0
milestone_name: milestone
status: paused
stopped_at: Plans 01-01/01-02 executed; DP-01 amendment applied; MVP reliability vertical implemented and HARDENED (cycle 7: SENDING submission-authority gate closes the eligibility→send TOCTOU, narrow SubmissionResult transport type, explicit outbox transition matrix, unified 429/2xx quota provenance, persistent typed rule registry as single rule source, bounded drain + attempt provenance, versioned trading-calendar groundwork) with green unit/slice tests (mvn test = 205 passed) on Spring Boot 4.1.1. No GSD phase approved/closed; no commit/deploy/live.
last_updated: "2026-10-07T18:40:00.000Z"
last_activity: "2026-10-07 (cycle 7) — closed the two remaining dispatch P0s and advanced persistent groundwork. (P0-1 TOCTOU) added a SENDING submission-authority state: OutboxDispatcher now claims → prepareSend (one short tx: reload, require active claim+lease, re-run eligibility, commit SENDING + send_started_at BEFORE any I/O) → adapter.send → record; a pre-send terminalization that wins before SENDING yields no adapter call, and once SENDING commits the row can never be declared 'definitely not sent'. (P0-2 transport type) WahaOutboundAdapter.send now returns a narrow SubmissionResult (ACCEPTED/DEFINITE_FAILURE/UNKNOWN + safe metadata) — an adapter can no longer return PENDING/IN_FLIGHT and cause a duplicate send; the dispatcher owns the mapping and record() requires SENDING. (C) an explicit OutboxTransitions matrix centralizes legal edges; abandon() is legal only from UNKNOWN_OUTCOME/FAILED (ACCEPTED/SENDING/PENDING refused), with a separate pre-send cancelPending. (D) 429 now carries the SAME unified QuotaSignal (window/limit/serverDate) as 2xx. (E) a persistent typed rule registry (rule_definition, immutable monotonic revision, enabled/paused, optimistic lock) is the single RuleSource for the scheduler AND the RuleRegistry for the guard; RuleAdminService (local, no HTTP) does create/edit/pause. (F) bounded drainBatch (no Integer.MAX_VALUE) + durable attempt provenance (send_started_at/attempt_finished_at/provider_message_id/accepted_at) + full-lifecycle metrics incl. oldest-pending age. (G) versioned TradingSessionCalendar + importer/validation contract + fixtures; production stays UNKNOWN/none (no invented holidays). Flyway V9+V10 (cycle 7), V11 (RuleMode default UNSELECTED + rule_state.rebaseline_required + precision CHECK 0..6) and V12 (append-only outbox_attempt ledger) (cycle 8). CYCLE 8: AUTHORIZED LOCAL checkpoint committed on branch checkpoint/cycle7-reviewed (41fa2605 reviewed baseline, 0daada39 functional fixes; NO push); pause cancels unsent PENDING + resume rebaseline marker (no replay/inferred crossing); explicit RuleMode (UNSELECTED default fail-closed/CROSSING/LEVEL-blocked); durable attempt ledger (attempt only on SENDING); IN_FLIGHT safe-recovery vs SENDING ambiguous; single RuleSource, tick(List) package-private; lossless NUMERIC(19,6). Spring 4.1 migration ATTEMPTED on the branch (test-classic starter clears the @DataJpaTest slice blocker + Jackson 3 + MockitoBean) but BLOCKED on the Testcontainers 2.0 BOM coordinate; reverted to 0daada39 (134 green on 3.3.13), nothing migration-related committed. Spring 4.1 full probe (throwaway copy): main compiles after Jackson 2→3; NEW blocker found at test compile — Boot 4 test-slice modularization (@DataJpaTest needs a modular test dependency), so Jackson is NOT the only blocker. 134 tests green (was 103); Postgres IT (V1–V10) written but NOT RUN (Docker absent). No phase closure, no commit."
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
Status: In progress — Phase 1 NOT closed (human gates GOV-01/CAT-01 pending). MVP reliability vertical IMPLEMENTED with green tests (not a phase sign-off). All 57 requirements remain Pending; all 23 assets NOT_AUTHORIZED (now enforced by an executable per-asset egress gate, not only documentation).
Last activity: 2026-10-07 (cycle 7) — see frontmatter last_activity.
Progress: [██░░░░░░░░] phase closure = 0/8 (unchanged); application implementation PRESENT and hardened (MVP reliability vertical, 205 tests green, Boot 4.1.1 (cycle 12)) — implementation ≠ phase acceptance.

## Performance Metrics

- Eight phases; 57 v1 IDs (all original 52 retained + 5 new); five explicit v2 IDs including FUT-05 volatility proposal.
- Application implementation PRESENT and HARDENED (cycle 7): Brapi v2 client (literal symbol + separate remapped flag, one-ticker guard, full ratelimit provenance — reset/remaining/limit/window/serverDate — on BOTH 2xx and 429); quote validation; change calc; CROSSING engine with durable in-transaction rule-state (optimistic lock + replay/ordering guard + regressive-revision rejection: a lower incoming revision is STALE_RULE_REVISION with no eval/mutation/fire, a bump cancels superseded PENDING intents while preserving ACCEPTED/UNKNOWN); REAL proxied @Transactional MonitorProcessingService (atomic + rollback-no-orphan proven); atomic PENDING outbox + post-commit claiming dispatcher with claim_generation so a stale late result cannot overwrite a reconciliation (record/suppress require the exact active IN_FLIGHT claim); a fail-closed DispatchEligibilityGuard (+ RuleRegistry port / EmptyRuleRegistry default) rechecks asset authorization, rule known/current/not-paused, revision, expiry and source-age BEFORE any send, transitioning a denied intent to a terminal no-send (SUPPRESSED/EXPIRED/CANCELLED) with no adapter call; outbox persists rule_revision/source_as_of/intent_created_at/expires_at/episode_epoch/suppression_reason; expired IN_FLIGHT leases QUARANTINE to UNKNOWN_OUTCOME (never resent) with a local OutboxReconciliationService (metrics + proof-gated requeue, no HTTP endpoint). Brapi quota admission is durable single-in-flight, FENCED by owner+monotonic token; ratelimit-reset is OBSERVED as a predicted deadline (billing-cycle provenance: accepted only when window=billing-cycle AND limit=hardLimit, anchored on serverDate+delta) and NEVER zeroes the budget — the counter is zeroed only by an elapsed-deadline rollover confirmation; conservative high-water consumed=max(local,hardLimit-remaining), never lowered. Per-asset OperationalAuthorization egress gate (all 23 NOT_AUTHORIZED, SNAG11 PARTIAL, KNHY11 QUARANTINED) checked BEFORE calendar/quota. quote_observation persists provider_remapped + provider_contract. Disabled injectable scheduler with UNKNOWN trading calendar. Unit/slice tests green (134) on Spring Boot 4.1.1; PostgreSQL/Flyway Testcontainers IT (V1–V8, two-consumer + quota races + abandon-terminal, clock-deterministic) written but NOT RUN (Docker absent on host). Spring 4.1 migration probed in a throwaway copy (3.5.6 green; 4.1.0 blocked only by Jackson 2→3). Green tests ≠ runtime/phase acceptance.
- GSD auto-advance/auto-chain disabled; no commits, no deploy, no live polling, no real WAHA send.

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
- No current task authorization for implementation, phase execution, migrations, external/sibling access or live activation.

## Session Continuity

Last session: 2026-10-07T13:30:00.000Z
Stopped at: Executed Plans 01-01/01-02; applied DP-01; implemented + HARDENED the MVP reliability vertical (cycle 7: late-result outbox fencing via claim_generation; fail-closed DispatchEligibilityGuard rechecking authorization/revision/expiry/source-age before any send; regressive rule-revision rejection + old-PENDING supersession cancel; billing-cycle quota provenance with conservative high-water; persisted quote remapped provenance) with 134 green unit/slice tests on Spring Boot 4.1.1. PostgreSQL/Flyway Testcontainers IT (V1–V8 + cycle-6 regressions) written + clock-deterministic but NOT RUN (Docker absent). Spring 4.1 migration probed in a throwaway copy (3.5.6 green; 4.1.0 blocked only by Jackson 2→3). No GSD phase approved/closed; no commit/deploy/live; all 57 requirements Pending; all 23 assets NOT_AUTHORIZED; KNHY11 quarantined; SNAG11 FII/FIAGRO conflict preserved.
Next eligible action: Human review of the hardened vertical + Phase-1 identity dispositions; run `mvn verify -Pdocker-it` where Docker is available to exercise V1–V8 + the dispatch-safety/revision/quota regressions on real PostgreSQL; execute the scoped Spring 4.1 migration (see docs/handoff/SPRING-41-PROBE-RESULT.md) in a dedicated follow-up with a commit checkpoint. Do not auto-advance; 01-04 (human approval) and GOV-01/CAT-01 remain human gates.
Boundary: Current repository only. No services, credentials, sibling inspection, upstream changes, migrations, real messages, commit or deployment. No automatic phase advance.
