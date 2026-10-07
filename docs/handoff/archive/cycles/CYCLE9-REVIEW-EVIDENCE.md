# B3 Monitor — Cycle 9 Review Evidence

Date: 2026-10-07
Branch: `checkpoint/cycle7-reviewed`
Start HEAD: `e9f5725` (cycle-8 docs)
End HEAD: `e864fad` (Spring Boot 4.1.1 migration)
Local commits this cycle (no push):
- `172e768` — cycle-9 functional fixes (A–G)
- `e864fad` — `chore: migrate to Spring Boot 4.1.1`

`mvn test` → **148 passed, 0 failures, 0 errors** on **Spring Boot 4.1.1** (was 134 on 3.3.13).
PostgreSQL IT: **NOT_RUN** (Docker absent). Seven protected inputs byte-unchanged.

---

## A — P0-live: post-resume baseline persistence

**Defect.** The ordinary-resume rebaseline branch in `MonitorProcessingService.process()` evaluated into a
local `RuleState state` (mutated by the evaluator to ARMED/LATCHED) but then persisted
`entity.updateFrom(entity.toDomain(), …)` — rebuilding the domain from the STALE entity (still
`UNBASELINED` because `markRebaselineRequired()` had set it). The first eligible post-resume comparison
was discarded; the SECOND eligible observation then only baselined, and a genuine crossing was lost.

**Fix.** The rebaseline branch now persists the evaluated `state` with `fired=false`:
`entity.updateFrom(state, quote.sourceTime(), false, null)`. The first eligible observation establishes the
fresh baseline from the real evaluation; a subsequent observed FALSE→TRUE fires normally.

**Regression (required by the review):** `armedPauseResume_firstFalseThenTrue_firesOnSecondEligible` —
ARMED → pause → resume → first eligible FALSE (fresh ARMED baseline, no fire) → second eligible TRUE
(observed crossing, fires once, exactly one PENDING).

## B — P0-live: latch/episode preserved across ordinary resume

**Defect.** `markRebaselineRequired()` set `phase = UNBASELINED`, destroying the persisted latch
(`phase == LATCHED`), contradicting CONTRACTS ("ordinary resume preserves consumed/latch/episode/cooldown
state").

**Fix.** `markRebaselineRequired()` now sets ONLY the marker; `phase` is preserved. The first eligible
observation after resume evaluates against the preserved phase but is forced not to fire (item A), the
resulting phase is persisted, the marker cleared and source-time advanced atomically. `episode_epoch` is
not incremented by a rebaseline (no new episode minted by resume).

**Regressions:** `latchedBeforePause_isNotConvertedToUnbaselinedByResume` (LATCHED survives resume; first
TRUE does not replay; epoch unchanged); `unknownAfterResumePreservesMarkerAndLatch` (an ineligible
observation after resume consumes neither the marker nor the latch); plus the retained
`latchedBeforePauseFirstTruePostResumeDoesNotReplay` and `rebaselineMarkerSurvivesRestart`.

RUL-03/RUL-05 are NOT claimed complete: initial-consumed, false-confirmation count, cooldown and LEVEL
policy remain unimplemented.

## C — P0-live: remove all implicit CROSSING defaults

**Defect.** `PriceRule`'s two convenience constructors defaulted to `RuleMode.CROSSING`; `RuleEvaluator`
evaluated ANY incoming mode with CROSSING semantics; the guard's `syntheticRule` relied on the hidden
default.

**Fix.**
- Both convenience constructors now default `RuleMode.UNSELECTED` (fail-closed); a new `withMode(mode)`
  copy-factory is the explicit way to make a rule operable. All operational call sites use explicit CROSSING.
- `RuleEvaluator.evaluate()` fails closed on `mode != CROSSING` → UNKNOWN, no state mutation, no fire.
- `MonitorProcessingService.process()` returns `NON_OPERABLE_MODE` before any state load/mutation for a
  non-operable rule (defence-in-depth).
- The guard's `syntheticRule` is now explicitly `UNSELECTED` (an authorization probe that can never imply
  CROSSING).

**Regressions:** `unselectedModeYieldsUnknownNoMutationNoFire`, `levelModeYieldsUnknownNoMutationNoFire`
(direct evaluator); every pipeline/scheduler/IT rule migrated to explicit CROSSING.

## D — P1-high: close expired SENDING attempt as UNKNOWN

**Defect.** Expired-SENDING reconciliation moved the outbox to UNKNOWN_OUTCOME but left the
`outbox_attempt` row open (`finished_at/outcome = null`).

**Fix.** In the same transaction, `reconcileExpiredLeases()` now locates the attempt by
`(outbox_id, claim_generation)`, requires it OPEN, and closes it `UNKNOWN` with `finished_at=now` and
`sanitized_status="lease-expired"` (provider fields preserved, never fabricated), THEN transitions the
outbox to UNKNOWN_OUTCOME. Expired IN_FLIGHT asserts there is NO attempt row for that generation before
the safe PENDING recovery. A broken invariant (attempt present on IN_FLIGHT, or absent/already-closed on
SENDING) fails closed: quarantine to UNKNOWN_OUTCOME with an `integrity-*` suppression reason and an
`INTEGRITY` log + counter — no fabricated coherent history.

**Regressions:** `expiredSendingClosesAttemptUnknownThenRequeueAddsSecond`,
`expiredInFlightHasNoAttemptAndRecoversToPending`, plus the real-Postgres IT variant
(`expiredLeaseSendingQuarantinedInFlightRecoveredOnRealPostgres`, NOT_RUN).

## E — P1: attempt-ledger invariants

**Defect/precision.** The ledger is not strictly "append-only"; `record()` used `ifPresent`, so an outbox
could terminalize without a matching attempt row.

**Fix.**
- Doc corrected to "durable per-attempt ledger / immutable after close".
- `OutboxAttemptEntity.close()` rejects a second close and a null result; `isOpen()` added.
- `OutboxTxOps.record()` now REQUIRES the exact open attempt (`orElseThrow`): a missing attempt is a
  data-integrity breach that rolls the transaction back (row stays SENDING for lease reconciliation)
  rather than minting a coherent-looking terminal state.

**Regressions:** `OutboxAttemptEntityTest` (open → close once → second close rejected; null rejected).

## F — P1: optimistic-race reporting matches persisted state

**Defect.** `OutboxDispatcher.dispatchOne()` caught `ObjectOptimisticLockingFailureException` from
`prepareSend` and returned `UNKNOWN_OUTCOME` though no UNKNOWN transition was persisted and no adapter
call occurred.

**Fix.** On a pre-send race the dispatcher reloads the actual persisted row (`tx.loadRow`) and reports its
real state (empty if gone); it never fabricates UNKNOWN. No adapter call. The stale `findClaimable()`
javadoc was corrected (IN_FLIGHT = pre-send safe-recoverable; SENDING = ambiguous after lease expiry).

**Regression:** `preSendRaceReportsPersistedStateNotFabricatedUnknown`.

## G — CAL-01: versioned calendar consolidated into the scheduler

**Defect.** Production still used the old `TradingCalendar` (OPEN/CLOSED/UNKNOWN); the versioned
`TradingSessionCalendar` was disconnected; the importer hardcoded 10:00–17:00; special sessions were
date-only; validation claims exceeded the implementation.

**Fix.**
- The old `TradingCalendar` class is DELETED; the scheduler now depends on the single
  `TradingSessionCalendar` port. OPEN/SPECIAL → collect; CLOSED/HOLIDAY/UNKNOWN → fail-closed skip (logs
  session + dataset version).
- The dataset carries version, **source/provenance**, **timezone**, coverage, a **regular session window**,
  and a **per-date special-session window map**. No hardcoded B3 hours remain in production.
- The importer validates version/source/timezone/coverage/regular-window, special-without-window,
  special-window validity, holiday↔special conflicts, and holidays/specials within coverage. Outside
  coverage / missing dataset → UNKNOWN. Production default stays `UnavailableTradingSessionCalendar`
  (UNKNOWN / "none") — nothing invented.
- Fixtures use explicitly TEST-ONLY synthetic hours.

**Regressions:** rewritten `TradingCalendarImporterTest` (provenance/timezone/window/conflict validation;
OPEN/CLOSED/HOLIDAY/SPECIAL/weekend/out-of-coverage classification via the dataset zone+windows); scheduler
tests exercise OPEN/SPECIAL collect vs HOLIDAY/UNKNOWN skip.

No new DB migration was required for A–G (no schema change). Migrations remain **V1–V12**.

## H — Spring Boot 4.1.1 migration — COMPLETED

Target **4.1.1** (not 4.1.0). Changes:
- parent → `spring-boot-starter-parent:4.1.1`;
- removed the project `testcontainers.version` override;
- Testcontainers via BOM-managed `org.testcontainers:testcontainers-junit-jupiter` +
  `org.testcontainers:testcontainers-postgresql` (resolved **2.0.5**, no explicit version);
- modern Boot-4.1 test modules: added `spring-boot-data-jpa-test`; `@DataJpaTest` import moved to
  `org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest` across 9 slice tests
  (NO `spring-boot-starter-test-classic` bridge needed);
- Jackson 2 → 3: `RestClientBrapiClient` import `tools.jackson.databind.JsonNode` (node API unchanged);
- `@MockBean`/`@SpyBean` → `@MockitoBean`/`@MockitoSpyBean`.

**Result:** `mvn -DskipTests compile` ✓, `mvn test` → **148 green on 4.1.1**. Dependency-tree sanity:
active databind is `tools.jackson.core:jackson-databind:3.1.5`; the only `com.fasterxml` jar is
`jackson-annotations:2.21` (Jackson 3's own intentional dependency — NOT a Jackson 2 databind shadow).
Committed separately as `e864fad`.

## I — PostgreSQL IT — NOT_RUN

Docker daemon absent on this host (`docker version` returns no server). The IT was extended for the item-D
attempt-close path and the IN_FLIGHT-no-attempt case and the doc bumped to V1–V12, and compiles, but is
**NOT_RUN** — no PASS is claimed. It remains the real release gate for V1–V12 DDL, optimistic-lock races,
attempt-ledger constraints, and pause/resume concurrency.

## J — Admin/status surface — NOT STARTED (recommended next)

Deferred: it is a net-new authenticated HTTP surface (SEC-01: auth required, non-admin denied,
CSRF/session-expiry tested, synthetic identities, local-bind only). Starting it half-done is worse than a
clean handoff; recommended as the next cycle's focus now that correctness + the framework migration are
green.

## Boundaries honoured

No push; no live Brapi; no live WAHA; no OPERATIONAL_ACTIVATION; no deploy; no production DB; no
sibling-repo change; no credential discovery outside synthetic config; no trading. Seven protected inputs
byte-identical to the preflight baseline.
