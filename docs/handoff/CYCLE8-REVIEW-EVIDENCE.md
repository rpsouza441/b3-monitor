# Cycle 8 — review evidence

**Build:** `mvn "-Dspring.profiles.active=test" test` → **BUILD SUCCESS, 134 tests, 0 failures, 0 errors**
(Spring Boot 3.3.13). Branch `checkpoint/cycle7-reviewed`; 7 protected inputs byte-unchanged; no `.env`;
Docker absent → PostgreSQL IT **NOT_RUN**.

## 1. Checkpoint branch + local commit SHAs
Branch **`checkpoint/cycle7-reviewed`** (off `6de333d`). Local commits (NO push):
- `41fa2605ee1f91afdf9c2f6655c694415fdbef29` — `checkpoint: cycle 7 reviewed baseline` (123 tests).
- `0daada39c294b807f584b26b40a6095006d95fd6` — `cycle 8: rule lifecycle + RuleMode + attempt ledger + calendar/numeric fixes` (134 tests).
The Spring 4.1 migration was attempted on this branch and **reverted** (blocked — see §10); nothing
migration-related is committed. Working tree clean at `0daada3`.

## 2. Protected-input hashes
All 7 identical to the cycle-5/6/7 baseline: REQUIREMENTS `B18E8A92…`, CONTRACTS `15378F08…`,
ASSET-CATALOG `79D5162C…`, RESULTADO `B24B77D0…`, SCHEMA `15E2DA91…`, 01-01-SUMMARY `463F9F04…`,
01-02-SUMMARY `206A31B9…`.

## 3. Pause/resume semantics + tests
`RuleAdminService.pause()` atomically marks paused AND cancels unsent PENDING intents for the rule
(`OutboxTxOps.cancelPendingForRule`, reason `paused`), preserving ACCEPTED/UNKNOWN/FAILED evidence and
latch/episode history. `resume()` keeps revision/history and sets `rule_state.rebaseline_required`;
`MonitorProcessingService` makes the first eligible post-resume observation RE-BASELINE (no fire,
detail `REBASELINED_AFTER_RESUME`) and clears the marker. Tests (`PauseResumeLifecycleTest`):
pending-before-pause cancelled & never sent after resume; ARMED+first-TRUE-post-resume no fire (then a
real later crossing does fire); LATCHED+first-TRUE no replay; marker survives restart.

## 4. Rule mode semantics / default
`RuleMode{UNSELECTED, CROSSING, LEVEL}`. New rules default **UNSELECTED** (fail-closed). The scheduler's
`RuleSource.activeRules()` excludes non-operable modes; the dispatch guard denies UNSELECTED/LEVEL.
`selectMode()` is an audited admin mutation that bumps the immutable revision and cancels old PENDING;
LEVEL is refused (Q-19 pending). No rule is auto-promoted (migration backfills UNSELECTED). Tests:
`PersistentRuleRegistryTest` (UNSELECTED not collected; CROSSING selection activates; LEVEL refused),
`FailClosedDispatchEligibilityGuardTest.unselectedModeFailsClosed`.

## 5. Attempt-ledger schema + invariants
`outbox_attempt` (V12): id, outbox_id FK, logical_key, claim_generation (unique per outbox),
fencing_token, started_at, finished_at, outcome, provider_message_id, provider_accepted_at,
sanitized_status. A row is created only when `prepareSend` commits SENDING (not on claim); `record`
closes exactly the open attempt; a proof-gated requeue creates a NEW immutable row preserving the
prior. `alert_outbox.attempts` now counts STARTED external submissions (incremented in `markSending`,
not `claim`). Tests (`OutboxAttemptLedgerTest`): claim-crash-before-SENDING → 0 attempts; one accept →
1 immutable row; unknown + proof requeue + accept → 2 immutable rows.

## 6. Expired IN_FLIGHT vs SENDING recovery
`OutboxTxOps.reconcileExpiredLeases()` returns `[recovered, quarantined]`: an expired **IN_FLIGHT** row
(no submission authority → no external send possible) is SAFELY recovered to PENDING with the claim
invalidated (next claim rechecks eligibility); an expired **SENDING** row is AMBIGUOUS → UNKNOWN_OUTCOME,
never retried. New legal edge `IN_FLIGHT → PENDING` added to the matrix. Tests: dispatcher
`expiredLeaseInFlightIsSafelyRecoveredToPending` + `expiredLeaseSendingIsQuarantinedNotResent`; IT
`expiredLeaseSendingQuarantinedInFlightRecoveredOnRealPostgres`.

## 7. RuleSource bypass removal
`MonitorScheduler.tick()` (no-arg) reads the single `RuleSource`; `tick(List<PriceRule>)` is now
PACKAGE-PRIVATE (same-package tests only). No runtime caller can inject stale/arbitrary rules and
collect outside the persistent registry.

## 8. Calendar model/provenance changes
Numeric/precision + mode migrations landed (V11). The calendar groundwork from cycle 7
(`TradingSessionCalendar` + importer/validation + fixtures) remains; production stays UNKNOWN/"none".
NOTE: full scheduler consolidation onto `TradingSessionCalendar` and dataset-carried session WINDOWS
(replacing the fixture-only 10:00–17:00) are **NOT yet done** — carried as an explicit open item (see
handoff §9). This cycle prioritized the P0-live lifecycle/mode and the attempt ledger.

## 9. Numeric scale contract
`RuleDefinitionEntity` validation now rejects threshold/hysteresis whose scale > 6 or integer part > 13
(so values round-trip NUMERIC(19,6) exactly) and precision 0..6; ruleId length/blank validated. V11
tightens the precision CHECK to 0..6. Test: `thresholdScaleBeyondSixRejected`.

## 10. Spring 4.1 migration PASS/BLOCKED + exact blockers
**BLOCKED, reverted.** On the branch: parent→4.1.0, `spring-boot-starter-test-classic` (clears the
cycle-7 `@DataJpaTest` test-slice blocker — main + test compile would proceed), Jackson 2→3,
`@MockitoBean`/`@MockitoSpyBean`. Next blocker: **Testcontainers 2.0 artifact coordinates** —
`org.testcontainers:junit-jupiter:2.0.0` does not exist in Central; the Boot-4-BOM-managed 2.0
coordinates must be read from the BOM, not guessed. Reverted to `0daada3` (134 green on 3.3.13); no
migration commit. Detail: `SPRING-41-PROBE-RESULT.md`.

## 11. Test count
**134** (was 123), 0 failures. `test-evidence-cycle8.log`.

## 12. PostgreSQL PASS or NOT_RUN
**NOT_RUN** (Docker absent). `OutboxPostgresIT` extended for V1–V12 + the IN_FLIGHT-recovery/SENDING-
quarantine split; pause/resume + mode + attempt-ledger DB coverage is partially present (expandable
under Docker).

## 13. Migrations added
**V11** (`rule_definition.mode` default UNSELECTED; `rule_state.rebaseline_required`; precision CHECK
0..6). **V12** (`outbox_attempt` ledger). Additive; `ddl-auto=validate` on the gated IT proves V1–V12.

## 14. Remaining blockers
- Docker absent → Postgres IT NOT_RUN.
- Spring 4.1 blocked on the Testcontainers 2.0 BOM coordinate (one lookup away).
- Calendar scheduler-consolidation + dataset session windows still open (item F partially done).
- Human/approval gates unchanged; no authenticated admin HTTP surface yet (domain services only).

## 15. Next 1–3 actions
See `DEVELOPMENT-HANDOFF.md` §10.
