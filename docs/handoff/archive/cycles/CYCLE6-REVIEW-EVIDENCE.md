# Cycle 6 — review evidence

**Build:** `mvn "-Dspring.profiles.active=test" test` → **BUILD SUCCESS, 103 tests, 0 failures, 0 errors**
(Spring Boot 3.3.13). Git HEAD unchanged `6de333d`; 7 protected inputs byte-unchanged; no `.env`;
Docker absent → PostgreSQL IT **NOT_RUN**.

## 1. Defects confirmed (cycle-5 review, verified against code)

| # | Defect | Where |
|---|--------|-------|
| P0-1 | `OutboxTxOps.record()` accepted a late result on token match even if the row had been moved out of `IN_FLIGHT`; reconciliation transitions (quarantine/abandon/requeue) did not invalidate the token — so a slow worker could overwrite a terminal/operator decision. | `OutboxTxOps.record`, `OutboxEntity`, `OutboxReconciliationService` |
| P0-2 | The dispatcher sent any `PENDING` row with no pre-dispatch recheck; `AlertIntent.sourceAsOf`/`createdAt` were dropped on persistence (`OutboxEntity` used `Instant.now()`); no rule_revision/expiry/cancel lineage. | `OutboxDispatcher`, `OutboxEntity`, `AlertIntent` |
| P0-3 | `RuleStateEntity.bumpRevisionTo()` ignored a lower incoming revision yet evaluation proceeded with the stale rule's parameters against newer state; a bump did not cancel old-revision PENDING intents. | `RuleStateEntity`, `MonitorProcessingService` |
| P1-1 | Quota treated `resetDelta >= 300s` as an account cycle — a duration heuristic, not the `x-ratelimit-window`/`limit` evidence. | `QuotaTxOps` |
| P1-2 | `Quote.remapped` was not persisted on the observation. | `QuoteObservationEntity`, `MonitorProcessingService` |

## 2. Defects fixed + exact regression tests

### P0-1 — stale late result can never overwrite a reconciliation
- `OutboxEntity.claimGeneration` is bumped on every `claim()` AND every `invalidateClaim()`; `record()`
  and `suppress()` require `isActiveClaim(token)` = still `IN_FLIGHT` with the exact token.
- `reconcileExpiredLeases()`, `abandon()`, `markFailedAfterProofOfNonDelivery()` all `invalidateClaim()`.
- Tests (`OutboxDispatcherTest`, latch adapter): `lateAcceptAfterAbandonDoesNotOverwriteFailed`,
  `lateResultAfterRequeueDoesNotOverwriteNewPending` (and asserts a NEW claim gets a newer generation),
  `expiredLeaseInFlightRowIsQuarantinedNotResentAndClaimInvalidated`,
  `ambiguousSendBecomesUnknownOutcomeAndIsNotResentOnNextDrain`.

### P0-2 — fail-closed pre-dispatch eligibility fence
- `DispatchEligibilityGuard` port + `FailClosedDispatchEligibilityGuard` default + `RuleRegistry` port +
  `EmptyRuleRegistry` (knows no rule → unknown → closed). The dispatcher calls it AFTER the IN_FLIGHT
  claim and BEFORE any adapter send; a denial becomes a terminal no-send
  (`SUPPRESSED`/`EXPIRED`/`CANCELLED`) with no adapter call. Outbox persists `rule_revision`,
  `source_as_of`, `intent_created_at` (from the injected Clock), `expires_at`, `episode_epoch`,
  `suppression_reason` (V7).
- Tests: `FailClosedDispatchEligibilityGuardTest` (8 — unknown rule/ deauthorized/ paused/ superseded/
  expired/ stale-source/ null-source all closed; authorized+current+fresh passes);
  `OutboxDispatcherTest.deniedEligibilityIsSuppressedWithNoSend`,
  `supersededRevisionDeniedBecomesCancelledNoSend`, `expiredDeniedBecomesExpiredNoSend`.

### P0-3 — regressive rule revision fails closed; bump cancels old PENDING
- `RuleStateEntity.reconcileRevision()` → `STALE` (incoming < persisted: no eval/mutation/fire),
  `SAME`, or `BUMPED` (re-baseline + the caller cancels superseded PENDING via
  `OutboxTxOps.cancelSupersededPending`, which runs in the SAME transaction as the bump).
- Tests (`MonitorPipelineReplayTest`): `lowerIncomingRevisionIsRejectedStaleNoEvaluation`,
  `bumpCancelsOldPendingButPreservesAcceptedHistory`, plus the retained
  `revisionBumpReBaselinesWithoutFiring`.

### P1 — billing-cycle quota provenance
- `QuotaSignal` carries limit/window/serverDate/requestId; the client captures them on 2xx (and
  429 carries retry/reset/remaining via `BrapiException`). `QuotaTxOps.observeReset` records an
  account-cycle deadline ONLY when `window == "billing-cycle"` AND `limit == hardLimit` AND a positive
  delta, anchored on `serverDate + delta`; otherwise provenance only (fail-closed). Conservative
  high-water `consumed = max(local, hardLimit - remaining)`, never lowered. Observing a deadline still
  does NOT reset `consumed` — rollover is confirmed only when the deadline elapses.
- Tests (`BrapiQuotaManagerTest`, 19): `billingCycleWithMatchingLimitAcceptedAsCycleProvenance`,
  `sandboxShortWindowNotAcceptedAsCycle`, `nonBillingHourlyWindowNotAcceptedAsCycle`,
  `mismatchedLimitStaysFailClosed`, `missingWindowOrLimitStaysFailClosed`, `serverDateAnchorsTheDeadline`,
  `providerRemainingRaisesHighWaterButNeverLowersIt`, `resetHeaderDoesNotZeroBudget_defectFromCycle4Fixed`
  (still green), `cycleRollsOverOnlyWhenObservedDeadlineElapsed`, `clockSkewBackwardDoesNotConfirmRollover`.

### P1 — quote provenance persisted
- `QuoteObservationEntity.providerRemapped` + `providerContract` (V8); written in
  `MonitorProcessingService.persistObservation`.

## 3. Dispatch state-machine guarantees now proven

- A result is recorded ONLY for the exact active claim (`IN_FLIGHT` + token + generation).
- Reconciliation (quarantine/abandon/requeue) invalidates the prior claim; a late worker is fenced.
- No network send occurs on a denied intent; denial → terminal `SUPPRESSED`/`EXPIRED`/`CANCELLED`.
- `UNKNOWN_OUTCOME` is never blind-resent; requeue needs proof of non-delivery; `ACCEPTED != DELIVERED`.
- A superseded (old-revision) PENDING intent is `CANCELLED` on the bump and can never be dispatched;
  `ACCEPTED`/`UNKNOWN_OUTCOME` evidence is preserved.

## 4. Quota billing-cycle evidence semantics

Account cycle accepted iff `x-ratelimit-window == billing-cycle` AND `ratelimit-limit == hardLimit(15000)`
AND `ratelimit-reset > 0`; deadline anchored on server `Date + reset`. Sandbox 20/60s and any non-billing
window (even 3600s) are rejected. A verified billing-cycle `remaining` raises a conservative high-water
and never lowers local `consumed`. An observed deadline is not a reset — rollover is a separate elapsed-
deadline confirmation.

## 5. Migrations added

- **V7** `alert_outbox`: `claim_generation`, `rule_revision`, `source_as_of`, `intent_created_at`
  (NOT NULL, backfilled from `created_at`), `expires_at`, `episode_epoch`, `suppression_reason`;
  index `ix_outbox_rule_rev_state`. New terminal states are enum-string values (no DDL).
- **V8** `quote_observation`: `provider_remapped`, `provider_contract`.

## 6. PostgreSQL IT

**NOT_RUN** — Docker absent on the build host. `OutboxPostgresIT` is written for V1–V8 and includes the
two-consumer dispatch race, the quota first-allocation race, expired-lease quarantine, and the
abandon-stays-terminal case. Run `mvn verify -Pdocker-it` on a Docker host.

## 7. Remaining blockers / next actions

See `DEVELOPMENT-HANDOFF.md` §10–§11.
