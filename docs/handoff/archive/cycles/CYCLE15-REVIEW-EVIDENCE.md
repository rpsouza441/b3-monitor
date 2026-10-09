# Cycle 15 — Review Evidence

Transaction-bound PostgreSQL lock proof · eliminate swallowed concurrency failures · final factual recoverability · readiness semantics.

`mvn test` → **216 passed, 0 failures, 0 errors** on **Spring Boot 4.1.1 / Java 21**, Flyway **V1–V13** (unchanged count: cycle 15 hardened and refactored existing tests, it did not add test methods). PostgreSQL IT = **NOT_RUN** (Docker absent) — runtime `PESSIMISTIC_WRITE` **NOT_PROVEN**. **No push.**

## Preflight (recorded)

- Branch `checkpoint/cycle7-reviewed`, HEAD at preflight `8d5eec2`, clean tree.
- `git branch -r` → only `origin/HEAD -> origin/main`, `origin/main`.
- Docker: **not installed** (`docker --version` exit 1) → Postgres IT stays NOT_RUN.
- Baseline `mvn test` = 216 green, BUILD SUCCESS.
- Seven protected inputs hashed and re-verified byte-unchanged at closure (see handoff).

## A — PostgreSQL lock-proof connection identity (transaction-bound)

**Defect:** `race_pessimisticLockActuallyBlocksCompetitor` read `pg_backend_pid()` from a fresh
`dataSource.getConnection()` inside the `TransactionTemplate` callback — a *different* pooled backend than
the JPA transaction that actually held the `PESSIMISTIC_WRITE` lock. The proven blocker pid was therefore
not guaranteed to be the lock holder.

**Fix:** the holder now opens its own `EntityManager` transaction and, through
`em.unwrap(org.hibernate.Session.class).doReturningWork(conn -> …)`, reads `pg_backend_pid()` **on the exact
JDBC connection the Hibernate session uses**, then takes the row lock with
`em.createQuery(…).setLockMode(PESSIMISTIC_WRITE).getSingleResult()` **in that same transaction/connection**.
So `holderPid` is provably the backend holding the lock.

**Assertions:** `holderPid > 0`; the competitor really entered the production `admin.pause()` path
(`competitorEntered` latch); `pg_blocking_pids()`/`pg_stat_activity` reports `holderPid` as a blocker of a
lock-waiting backend; the competitor stays blocked (`!competitor.isDone()`) until `release`; it completes
only after release (`pauseReturnedAt >= releasedAt`); `holder.get()`/`competitor.get()` propagate any
failure; executors shut down and the lock is released in `finally`. The lone `Thread.sleep(500)` only lets
the blocked `UPDATE` enqueue — the latch, not the sleep, is the coordinator. Real paths throughout
(`RuleAdminService`, `MonitorProcessingService`, `OutboxTxOps`, `FailClosedDispatchEligibilityGuard`,
`PersistentRuleRegistry`); allow-all guard remains isolated to transport-only `OutboxPostgresIT`.
**Real lock-wait evidence mechanism: `pg_blocking_pids()` bound to a transaction-identified holder pid.**
Runs only under `mvn verify -Pdocker-it` → **NOT_RUN** here.

## B — Removed all swallowed concurrency failures

| File | Test | Before | After |
|---|---|---|---|
| `SchedulerTransactionTest` | `concurrentFirstCreationYieldsOneConsistentStateNoFire` | `catch (Exception ignored) {}` | worker rethrows; `Future.get` surfaces it; only a typed uniqueness/optimistic conflict is accepted (`isLegalFirstCreationRace`), else fail |
| `RuleLifecycleFenceTest` | `concurrentPauseAndProcessAreLinearizedNoDeadlock` | `catch (Exception ignored) {}` | worker rethrows; `Future.get` surfaces it; only a typed optimistic-lock conflict is accepted (`isOptimisticConflict`), else fail |

Accepted types are explicit: `DataIntegrityViolationException` / `DuplicateKeyException` /
`ObjectOptimisticLockingFailureException` / `OptimisticLockingFailureException` /
`CannotAcquireLockException` / Hibernate `ConstraintViolationException` /
`SQLIntegrityConstraintViolationException` (first-creation) and the optimistic-lock set
(`ObjectOptimisticLockingFailureException` / `OptimisticLockingFailureException` /
`CannotAcquireLockException` / `StaleObjectStateException`) for pause-vs-process. The final DB invariant is
still asserted, but it is no longer the *only* guard — an unrelated worker failure now fails the test.

**Scan result:** `catch (Exception|RuntimeException|Throwable ignored)` across `src/test` → **0 matches**.
The one remaining `catch (InterruptedException ignored)` (a latch `await` in `OutboxDispatcherTest`) is a
standard interrupt idiom on a coordination latch, not a swallowed production-path failure, and is outside
the review's named cases.

## C — Factual drift fully closed (overlay only)

- `README.md`: `213 → 216` in **both** places; cycle narrative `5–14 → 5–15`; added the cycle-15 lock-identity hardening; Boot 4.1.1 / V1–V13 / local-commits-no-push / 0/8 all stated.
- `.planning/STATE.md`: frontmatter `stopped_at`/`last_updated`/`last_activity`, **Current Position**, **Progress**, **Performance Metrics** (was the stale `134`-tests + `V1–V8` line) and **Session Continuity** all corrected to 216 / Boot 4.1.1 / V1–V13 / local authorized commits (NO push). The stale "no current task authorization … no commits" line replaced with current local-commit reality.
- Phase closure stays **0/8**; no audit reopened; no phase auto-closed; all 23 assets `NOT_AUTHORIZED`.
- **Protected baseline inputs untouched** (PROJECT/REQUIREMENTS/ROADMAP/CONTRACTS/SECURITY/ARCHITECTURE/ADRS). `ROADMAP.md` still carries the frozen `134 / V1–V8 / 3.3.13` planning-proposal baseline **by design** — it is protected and corrected via the overlay, not edited.
- **Stale-fact scan** (current references, archive excluded): README/STATE → **0**. Remaining repo matches are the protected `ROADMAP.md` baseline, a phase *acceptance criterion* in `01-04-PLAN.md` ("no commits/activation"), and historical cycle-14 evidence — all legitimate.

## D — Readiness semantics refactored into three dimensions

`ReadinessComponentView(component, wiringStatus, operationalStatus, runtimeStatus, detail)`:

| component | wiring | operational | runtime | note |
|---|---|---|---|---|
| asset_catalog | READY | NOT_APPLICABLE | VERIFIED | 23 trusted tickers |
| operational_authorization | READY | BLOCKED_BY_GATE (0 authorized) | VERIFIED | executable gate; all NOT_AUTHORIZED |
| trading_calendar | READY | NOT_APPLICABLE | NOT_READY/VERIFIED | code ready, dataset readiness separate |
| workers | READY | BLOCKED_BY_GATE | NOT_RUN | disabled this milestone |
| brapi_contract | READY | BLOCKED_BY_GATE | NOT_RUN | client wired, live call is a human gate |
| python_daily_indicators | NOT_INTEGRATED | NOT_APPLICABLE | NOT_RUN | not wired; never synthesized |
| catalog_import | NOT_INTEGRATED | NOT_APPLICABLE | NOT_APPLICABLE | no import pipeline |
| waha_delivery | PARTIAL | BLOCKED_BY_GATE | NOT_RUN | simulated adapter only |

Code integration, operational authorization and runtime verification are now independent axes — readiness is
never overstated. Read-only; `/admin/readiness` + `GET /api/admin/readiness` activate nothing (no import,
poll, send, mutation, secret); viewer-readable; smoke test asserts the three axes and that no POST endpoint
exists. **UI-02 stays PARTIAL** — the literal acceptance (imports creating audit revisions) is not met.

## E — optional (skipped)

UI-03 attempt fetch stays 1+N (bounded: ≤100 parents × ≤20 attempts) — not a correctness defect. Per the
cycle brief ("do not risk the cycle for this optimization"), the bulk-fetch optimization was **not** done.

## Requirement status (unchanged this cycle)

SEC-01 **DONE** · UI-03 **DONE** (current outbox contract) · UI-01 **PARTIAL** · UI-02 **PARTIAL** (readiness
now three-dimension; literal acceptance unmet) · RUL-03/RUL-05 **PARTIAL** (Q-19). PostgreSQL runtime gate
**NOT_RUN**; true-race proof written/compiled with transaction-bound lock identity, **NOT_RUN**.

## Human gates (unchanged)

push · live Brapi · real WAHA · recipients/live messaging · OPERATIONAL_ACTIVATION · production DB/deploy ·
sibling-repo changes · trading · LEVEL/false-confirmation/cooldown while Q-19 is unresolved. UNSELECTED
fail-closed; only explicit CROSSING operable; workers disabled.
