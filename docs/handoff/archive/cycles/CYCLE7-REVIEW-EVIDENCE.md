# Cycle 7 — review evidence

**Build:** `mvn "-Dspring.profiles.active=test" test` → **BUILD SUCCESS, 123 tests, 0 failures, 0 errors**
(Spring Boot 3.3.13). HEAD unchanged `6de333d`; 7 protected inputs byte-unchanged; no `.env`; Docker
absent → PostgreSQL IT **NOT_RUN**.

## 1. Confirmed defects (cycle-6 review, verified against code)
- **P0-1 eligibility→send TOCTOU:** the guard was evaluated, then `adapter.send()` ran; a reconciliation
  winning in between could terminalize the row locally while the external submission still happened.
  Fencing protected only the write-back, not the side effect.
- **P0-2 transport returns arbitrary `OutboxState`:** `WahaOutboundAdapter.send(): OutboxState` could
  return `PENDING` → re-claim → duplicate send.
- **P1-1 `abandon()` had no transition matrix** (could drive `ACCEPTED → FAILED`).
- **P1-2 429 lost window/limit/serverDate** provenance (only 2xx carried it).
- **P1-3 attempt ledger / bounded drain** missing (`drain(Integer.MAX_VALUE)`).

## 2. Exact state-machine fix (P0-1)
New `SENDING` state + submission-authority gate. `OutboxDispatcher.dispatchOne()`:
`claimNext` (PENDING→IN_FLIGHT) → `prepareSend` → `adapter.send` → `record`. `OutboxTxOps.prepareSend`
runs ONE short transaction that reloads the row, requires the exact active claim token, requires the
lease unexpired, RE-RUNS `DispatchEligibilityGuard`, and commits `SENDING` + `send_started_at` BEFORE
any network I/O. The adapter is called ONLY after that commit. A pre-send terminalization that wins
before the SENDING commit → no adapter call; once SENDING commits, the row can never be declared
"definitely not sent". No DB transaction is held across the adapter call.
Regressions (`OutboxDispatcherTest`, latch adapter): `preSendTerminalizationWinsBeforeSendingNoAdapterCall`,
`onceSendingCommitsOperatorCannotMarkUnsentAndCrashIsAmbiguous`,
`leaseExpiredBeforePrepareSendYieldsNoAdapterCall`, `expiredLeaseMidAttemptIsQuarantinedNotResent`.

## 3. Legal transition matrix (item C)
`OutboxTransitions` centralizes edges: `PENDING→IN_FLIGHT|CANCELLED|EXPIRED|SUPPRESSED`;
`IN_FLIGHT→SENDING|CANCELLED|EXPIRED|SUPPRESSED|UNKNOWN_OUTCOME`;
`SENDING→ACCEPTED|UNKNOWN_OUTCOME|FAILED`; `UNKNOWN_OUTCOME→FAILED|PENDING`; `FAILED→FAILED`;
`ACCEPTED/CANCELLED/EXPIRED/SUPPRESSED→∅`. `record()`/`prepareSend()`/reconcile call `requireLegal`.
`OutboxReconciliationService.abandon()` is legal ONLY from `UNKNOWN_OUTCOME`/`FAILED` (ACCEPTED/
SENDING/PENDING refused); a separate `cancelPending()` handles pre-send PENDING cancel.
Tests: `OutboxTransitionsTest` (3), `OutboxReconciliationServiceTest` (abandon refused from
ACCEPTED/PENDING/SENDING; cancelPending only from PENDING).

## 4. Adapter result type (P0-2)
`WahaOutboundAdapter.send()` returns `SubmissionResult{ACCEPTED|DEFINITE_FAILURE|UNKNOWN + nullable
providerMessageId/acceptedAt/sanitizedStatus}`. The dispatcher maps it to the one legal state;
`record()` requires `SENDING`. null result → UNKNOWN; thrown → UNKNOWN; an adapter can no longer emit
`PENDING`/`IN_FLIGHT`/`SENDING`/`CANCELLED`/`EXPIRED`/`SUPPRESSED`. Tests: `nullResultBecomesUnknownNotResent`,
`thrownSendBecomesUnknown`, `definiteFailureMapsToFailed`, `happyPathClaimsSendingThenAccepts`.

## 5. Persistent typed rule-registry scope (item E)
`RuleDefinitionEntity` (unique ticker, immutable monotonic `revision` never decreasing, typed
comparator/threshold/precision/hysteresis with bounded validation, enabled/paused, created/updated via
injected Clock, `@Version`). `PersistentRuleRegistry` (@Primary) implements BOTH `RuleSource`
(scheduler's active rules) AND `RuleRegistry` (guard's revision/pause) — one source of truth.
`RuleAdminService` (local domain service, NO HTTP) does create/edit(bump revision)/pause/resume/disable.
Flyway V10. `MonitorScheduler.tick()` reads from `RuleSource`. **RUL-01/RUL-05 are NOT marked complete**
— this is groundwork; acceptance criteria (e.g. full admin/auth, combined rules) are not all met.
Tests: `PersistentRuleRegistryTest` (7).

## 6. Bounded attempt/drain scope (item F)
`drainBatch()` uses a configurable `b3monitor.outbox.max-batch` (default 50) — no `Integer.MAX_VALUE`.
Durable attempt provenance on the outbox row: `send_started_at`, `attempt_finished_at`,
`provider_message_id` (nullable, only from a verified adapter — never fabricated), `accepted_at`
(Flyway V9). Full-lifecycle metrics + oldest-PENDING age in `OutboxReconciliationService.metrics()`
(OPS-01/NOT-04 groundwork). No automatic UNKNOWN_OUTCOME retry; no invented WAHA idempotency.

## 7. Tests run / pass / fail
**123 run, 123 passed, 0 failed, 0 skipped** (was 103). Evidence: `test-evidence-cycle7.log`.
New suites: `OutboxTransitionsTest`, `PersistentRuleRegistryTest`, `TradingCalendarImporterTest`;
expanded: `OutboxDispatcherTest` (SENDING/TOCTOU), `OutboxReconciliationServiceTest` (matrix),
`RestClientBrapiClientTest` (429 unified provenance), `BrapiQuotaManagerTest` (billing-cycle).

## 8. PostgreSQL PASS or NOT_RUN
**NOT_RUN** — Docker absent. `OutboxPostgresIT` covers V1–V10, two-consumer race, quota race,
expired-lease quarantine, abandon-terminal. Run `mvn verify -Pdocker-it` on a Docker host.

## 9. Spring probe status (no overclaim)
Full probe in a throwaway copy: main compiles under 4.1.0 after Jackson 2→3; a NEW blocker was found at
TEST compile — Boot 4 test-slice modularization (`@DataJpaTest` not on the `spring-boot-starter-test`
classpath; needs a modular test dependency). So Jackson is NOT the only blocker, and the full 4.1 suite
run was NOT reached. Detail: `SPRING-41-PROBE-RESULT.md`. Main tree NOT migrated (no commit auth).

## 10. Migrations added
**V9** outbox: `send_started_at`, `attempt_finished_at`, `provider_message_id`, `accepted_at`.
**V10** `rule_definition` table. Both additive. SENDING is an enum-string value (no DDL). `ddl-auto=
validate` on the gated IT proves V1–V10 match the entities.

## 11. Protected-input integrity
HEAD `6de333d`; `git status` shows only the (expected) uncommitted dev tree; the 7 protected inputs
hash identically to the cycle-5/6 baseline; no `.env`/secrets/`target`/nested-zip contamination.

## 12. Remaining blockers + next 1–3 actions
See `DEVELOPMENT-HANDOFF.md` §9–§10.
