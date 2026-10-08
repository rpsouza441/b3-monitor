# B3 Monitor — Cycle 14 Review Evidence

Date: 2026-10-08
Branch: `checkpoint/cycle7-reviewed`
Start HEAD: `653674c` (cycle-13 docs)
Local commits this cycle (NO push):
- `c9f0aac` — test: harden postgres lifecycle concurrency tests (A)
- `0068130` — fix: order UI-03 alert view by intentCreatedAt DESC, id DESC (B)
- `0f70334` — docs: correct README/STATE factual drift (C)
- `f4e3b74` — feat: add read-only UI-02 operator readiness view (E)
- (docs commit) — handoff rev 14, this evidence, archive CYCLE13

`mvn test` → **216 passed, 0 failures, 0 errors** on **Spring Boot 4.1.1**, Flyway **V1–V13** (was 213).
PostgreSQL IT: **NOT_RUN** (Docker absent) — both IT classes compile. Runtime PESSIMISTIC_WRITE: **NOT_PROVEN**.
The ZIP entry count + SHA-256 are DERIVED FROM THE FINAL ZIP and reported in the closure message.

---

## A — PostgreSQL concurrency-test integrity

**A1 — no catch-and-ignore.** `race_pauseVsProcess_noStalePendingSurvivesPause()` previously wrapped the
process thread in `catch (Exception ignored) {}`. Now the thread body runs `process(...)` with no catch; the
main thread observes it via `Future.get(20s)`. The ONLY legal in-race failure for the losing thread is an
optimistic-lock conflict against the concurrent revision bump — asserted by exact type
(`ObjectOptimisticLockingFailureException` / `OptimisticLockingFailureException` / an `OptimisticLock`-named
cause). **Any** SQL/deadlock/transaction/programming error is re-thrown and FAILS the test. Nothing is swallowed.

**A2 — false-positive fix in the lock-wait proof.** `race_pessimisticLockActuallyBlocksCompetitor()` could
previously pass if the competitor thread was simply never scheduled before the holder released the lock. Fixed:
- a `competitorEntered` `CountDownLatch` is counted down IMMEDIATELY before the competitor invokes the
  production `admin.pause()` path; the main thread waits for `competitorEntered` before testing the blocked
  state, so a never-scheduled competitor can no longer produce a false pass;
- the holder captures its backend pid via `pg_backend_pid()` and is released only AFTER the competitor is known
  to have started;
- the block is proven at the **DATABASE** level, not by timing: a query over `pg_stat_activity` asserts that a
  backend with `wait_event_type='Lock'` has the holder's pid among its `pg_blocking_pids(...)` — real lock-wait
  evidence, not an inferred sleep;
- both executors are closed in `finally`, and `release.countDown()` in `finally` guarantees the holder never
  dangles. The single `Thread.sleep(500)` is explicitly NOT the race coordinator (the latch is) — it only lets
  the blocked `UPDATE` reach the lock-wait queue before the `pg_blocking_pids` probe.

The production paths are REAL throughout: `RuleAdminService`, `MonitorProcessingService`, `OutboxTxOps`,
`FailClosedDispatchEligibilityGuard`, `PersistentRuleRegistry`. The allow-all `DispatchEligibilityGuard` stays
isolated to the transport-only `OutboxPostgresIT`.

**A3 — race-3 wording corrected.** Renamed `race_editVsPrepareSend_staleIntentNeverGetsAuthority` →
`race_editVsPrepareSend_oldRevisionGetsNoNewAuthorityAfterBump`. If `prepareSend` wins the lock before the edit
commits, its authority was VALID at that linearization point (the revision it saw was the then-current one) — it
is NOT "stale authority". The asserted invariant is forward-looking: once the revision bump wins/commits, the
OLD revision can receive NO NEW send authority (a re-prepare of the old claim is denied).

**Lock-wait evidence added:** YES — `pg_blocking_pids()` / `pg_stat_activity` (database-level), guarded by a
`competitorEntered` latch. Runs only under `mvn verify -Pdocker-it` (NOT_RUN here).

## B — UI-03 semantic ordering

`OutboxRepository.findRecentAlerts(Pageable)` changed from `order by o.id desc` to
`order by o.intentCreatedAt desc, o.id desc` — newest-first by the logical event clock, with id as the
deterministic tie-break. Two regressions in `AlertOutcomeViewTest`:
- `orderingIsSemanticNewestFirstByIntentCreatedAt` — rows inserted so id-order and intentCreatedAt-order
  DISAGREE; the view returns `[newest, middle, oldest]` by intentCreatedAt, not insertion order.
- `orderingTieBreaksOnIdDescWhenIntentCreatedAtEqual` — equal intentCreatedAt ⇒ higher id first.

Unchanged: all 9 states exposed, page cap 100, bounded (≤20) child attempts, ACCEPTED ≠ deliveryConfirmed,
UNKNOWN_OUTCOME uncertain, reconciliation (dead-letter) query separate.

## C — factual state drift corrected

- `README.md`: 134 → **216** tests (two places), cycle-5/6 → cycles 5–14 narrative, Boot **4.1.1**, Flyway
  **V1–V13**, local-commit reality (branch `checkpoint/cycle7-reviewed`, NO push), admin surface/UI, and an
  explicit "implementação ≠ aceitação de fase GSD".
- `.planning/STATE.md`: frontmatter `status: paused` → `in-progress`; `stopped_at`/`last_updated`/
  `last_activity` rewritten to cycle-14 reality; "Current Position", "Session Continuity" factual lines
  updated (213/216 tests, V1–V13, local commits exist, SEC-01 DONE, UI-03 DONE, UI-01/UI-02 PARTIAL,
  PostgreSQL NOT_RUN).

GSD phase closure stays **0/8** (implementation ≠ phase acceptance). The seven protected baseline inputs are
byte-unchanged. No audit reopened, no phase auto-closed, all 23 assets remain NOT_AUTHORIZED.

## E — safe UI-02 breadth (read-only readiness)

New GET `/admin/readiness` + GET `/api/admin/readiness`: a bounded, read-only operator readiness snapshot —
catalog/authorization summary (counts by status over the 23 canonical tickers), trading-calendar dataset
readiness (version/zone/READY|NOT_READY), workers enabled/disabled, rule counts, and the integration readiness
of the components that are NOT wired, each with an explicit cause:
- `brapi_contract` → NOT_INTEGRATED (live Brapi is a human gate; no credential/real poll);
- `python_daily_indicators` → NOT_INTEGRATED (SMA/RSI/EMA/volume + Python context not wired; never synthesized);
- `catalog_import` → NOT_INTEGRATED (no import pipeline; catalog is a static trusted list; no destructive import);
- `waha_delivery` → NOT_INTEGRATED (real WAHA is a human gate; only a simulated adapter; no recipients).

It activates NOTHING: no import, no live poll, no send, no mutation, no secret. Viewer-readable; a smoke test
asserts `catalogAssets=23`, workers disabled, the NOT_INTEGRATED components, and that no POST endpoint exists
under readiness. **UI-02 stays PARTIAL** — the literal acceptance ("validated changes create audit revisions;
pause switches immediately stop subsequent collection/delivery claims") is satisfied for the rule-mutation path
(RuleAdminService + V13 audit + the pause fence) but NOT for catalog/policy/import administration, which remain
NOT_INTEGRATED. This view advances VISIBILITY only.

## Requirement status after cycle 14 (acceptance-literal)

- **SEC-01 = DONE** (unchanged — real loopback-only admin surface + proven inactivity session expiry).
- **UI-01 = PARTIAL** (freshness real; daily indicators + Python context NOT_INTEGRATED).
- **UI-02 = PARTIAL** (rule-mutation→audit path met; catalog/policy/import administration NOT_INTEGRATED;
  cycle-14 added read-only readiness visibility only).
- **UI-03 = DONE** for the current outbox contract (full lifecycle, semantic ordering, lineage, read-only).
- **RUL-03/RUL-05 = PARTIAL** (Q-19 unresolved; LEVEL/false-confirmation/cooldown fail-closed).
- **PostgreSQL runtime gate = NOT_RUN**; **true-race proof = written (compiled), NOT_RUN** pending Docker.

## Human gates (unchanged)

push · live Brapi · real WAHA · recipients/live messaging · OPERATIONAL_ACTIVATION · production DB/deploy ·
sibling-repo changes · trading · LEVEL/false-confirmation/cooldown while Q-19 is unresolved. UNSELECTED stays
fail-closed; only explicit CROSSING is operable; workers remain disabled.
