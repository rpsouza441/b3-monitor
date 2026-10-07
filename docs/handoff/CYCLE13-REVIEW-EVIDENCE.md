# B3 Monitor — Cycle 13 Review Evidence

Date: 2026-10-07
Branch: `checkpoint/cycle7-reviewed`
Start HEAD: `9b0c8d5` (cycle-12 docs)
Local commits this cycle (no push):
- `62f053f` — fix: expose complete bounded alert lifecycle (P1-A, UI-03)
- `115bc0a` — test: add true postgres lifecycle concurrency races (P1-B)
- `4a10c7b` — fix: make session-timeout genuinely bounded (P2)
- (docs commit) — handoff rev 13, this evidence, archive CYCLE12

`mvn test` → **213 passed, 0 failures, 0 errors** on **Spring Boot 4.1.1** (was 205).
PostgreSQL IT: **NOT_RUN** (Docker absent) — both IT classes compile. Seven protected inputs byte-unchanged.
The ZIP entry count + SHA-256 are DERIVED FROM THE FINAL ZIP and reported in the closure message.

---

## P1-A — UI-03 now exposes the FULL transport lifecycle (was dead-letter only)

**Defect.** `AdminQueryService.alertOutcomes()` delegated to `reconciliation.deadLetters()`, which returns only
UNKNOWN_OUTCOME/FAILED. So `/admin/alerts` could NOT show ACCEPTED, PENDING, IN_FLIGHT, SENDING, CANCELLED,
EXPIRED or SUPPRESSED logical alerts — UI-03 was not actually complete.

**Fix.** New bounded `OutboxRepository.findRecentAlerts(Pageable)` over `alert_outbox` across ALL states,
`order by o.id desc` (deterministic newest-first with a stable id tie-break). `alertOutcomes()` now uses it,
hard-capped at 100, with bounded child attempts (≤20). The dead-letter reconciliation query/workflow is
UNCHANGED (`/admin/outbox` still focuses on UNKNOWN_OUTCOME/FAILED). The `AlertOutcomeView` DTO gained
`acceptedAt`, `sendStartedAt`, `attemptFinishedAt`; `deliveryConfirmed` stays separate from `state`.

**Per logical alert (sanitized):** logical_key, rule_id, ticker, rule_revision, episode_epoch, source_as_of,
intent_created_at, state, delivery_confirmed, accepted_at, provider_message_id, suppression/failure/cancel
reason, send_started_at, attempt_finished_at, uncertain flag + bounded attempt lineage (claim_generation,
fencing_token, started_at, finished_at, outcome, sanitized_status, provider_message_id, provider_accepted_at).
No JPA entity is serialized; no payload/token/credential is exposed.

**Semantics enforced:** ACCEPTED ≠ delivery_confirmed; UNKNOWN_OUTCOME flagged uncertain; FAILED/CANCELLED/
EXPIRED/SUPPRESSED are not successful transport.

**Tests (`AlertOutcomeViewTest`, H2):** all 9 states present in the view; ACCEPTED shown with
`deliveryConfirmed=false` + `acceptedAt` + provider id; UNKNOWN uncertain; FAILED + PENDING visible; attempt
lineage maps to the correct logical alert; page size hard-capped at 100; reconciliation still shows ONLY
FAILED/UNKNOWN_OUTCOME. VIEWER-readable (covered in `AdminSecurityEnabledTest.assetsAndAlertsJsonRenderForAuthenticated`);
no mutation/live-send endpoint introduced.

## P1-B — true concurrent races in LifecycleFencePostgresIT (status: compiled, NOT_RUN)

The cycle-12 fence ITs were SEQUENTIAL (ordered calls). Added REAL concurrent races using
`ExecutorService` (2 threads) + `CyclicBarrier` (start together) + independent transactions
(`RuleAdminService`/`OutboxTxOps` open their own) + explicit `Future.get(timeout)` assertions:
- **Race 1 pause vs process** — both from the same ARMED state; invariant: a committed pause leaves no valid
  unsent PENDING regardless of order.
- **Race 2 pause vs prepareSend** — over a real claimed row; asserts EXACTLY one legal outcome (prepareSend
  AUTHORIZED→SENDING preserved after pause, OR pause won→0 adapter calls / no SENDING).
- **Race 3 edit vs prepareSend** — a stale-revision intent NEVER receives new send authority.
- **Race 4 explicit locking proof** — thread A holds the `PESSIMISTIC_WRITE` fence
  (`findByRuleIdForUpdate`) inside an open `TransactionTemplate`, pinned by a latch; thread B's pause is
  asserted STILL BLOCKED while held and resolves only AFTER release (`pauseReturnedAt >= releasedAt`). The
  one `Thread.sleep(500)` only SAMPLES that the competitor is still waiting — it is not the race coordinator;
  the latch is.

The REAL `FailClosedDispatchEligibilityGuard` + `PersistentRuleRegistry` + `RuleAdminService` are used; the
allow-all guard stays isolated in the transport-only `OutboxPostgresIT`. **Docker is absent, so these did NOT
run — PostgreSQL PESSIMISTIC_WRITE behaviour is NOT claimed proven at runtime.** Both ITs compile; run with
`mvn verify -Pdocker-it` on a Docker host.

## P2 — session-timeout claim made accurate

`AdminProperties.effectiveSessionTimeout()` was only coercing ≤0 → 1800 (no upper bound), so "bounded" was
inaccurate. Now it is genuinely bounded: a non-positive value → the 1800 default, and a value above the
explicit `MAX_SESSION_TIMEOUT_SECONDS = 86400` (24h) is clamped down — the window can never be effectively
infinite. The 1800 default and the 1-second test value are preserved.

**Tests (`AdminSessionTimeoutTest`):** default 1800; 1s preserved; 0/−5 → 1800; above-cap clamped to 86400;
at-cap kept. The real-expiry proof (`SessionExpiryTest`, RANDOM_PORT, real cookie) remains green.

## Requirement status changes (acceptance-literal)

- **UI-03 → DONE** for the current outbox contract: the full transport lifecycle is inspectable, logical alert
  / transport state / attempt lineage are separated, ACCEPTED≠confirmed and UNKNOWN≠delivered are visible,
  bounded + read-only. (Delivery confirmation itself remains a future WAHA-receipt concern, out of scope.)
- **SEC-01 → DONE** (unchanged; session-timeout claim now accurate).
- **UI-01 → PARTIAL** (asset/freshness real; daily indicators + Python context NOT_INTEGRATED).
- **UI-02 → PARTIAL** (rules audited + pause stops collection; catalog/policies/imports not covered).
- **RUL-03/RUL-05 → PARTIAL** (Q-19 unresolved; LEVEL/false-confirmation/cooldown fail-closed).
- **PostgreSQL runtime gate → NOT_RUN**; **true-race proof → written (compiled), NOT_RUN** pending Docker.

## Boundaries honoured

No push; no live Brapi; no live WAHA; no OPERATIONAL_ACTIVATION; no deploy; no production DB; no sibling-repo
change; no non-synthetic credentials; no trading. Workers disabled; all assets NOT_AUTHORIZED. Q-19 fail-closed.
Seven protected inputs byte-identical. `.env.example` is a template (not a real `.env`).
