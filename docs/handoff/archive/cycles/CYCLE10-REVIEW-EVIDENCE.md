# B3 Monitor — Cycle 10 Review Evidence

Date: 2026-10-07
Branch: `checkpoint/cycle7-reviewed`
Start HEAD: `6efd84f` (cycle-9 docs)
Local commits this cycle (no push):
- `6429cfa` — linearizable lifecycle fence + retry-safe mutations (items A, B)
- `01001ab` — authenticated loopback admin/status surface + SEC-01 tests (items C–F)
- (docs commit) — handoff rev 10, this evidence, archive restructure (item 0/K)

`mvn test` → **173 passed, 0 failures, 0 errors** on **Spring Boot 4.1.1** (was 148).
PostgreSQL IT: **NOT_RUN** (Docker absent). Seven protected inputs byte-unchanged.

---

## A — P0-live: linearizable lifecycle fence around `rule_definition`

**Confirmed race.** The scheduler snapshots an active `PriceRule` and fetches from Brapi OUTSIDE any
transaction; an admin pause/edit/selectMode/disable can commit during the fetch; the returned snapshot
could then still mutate `rule_state`/create an outbox row AFTER the admin mutation. The dispatch guard
blocked a *paused* dispatch but did not serialize the SOURCE of eligibility, and a later resume could
reopen the window.

**Fix — one shared fence.** A `PESSIMISTIC_WRITE` row lock on the single `rule_definition` row
(`RuleDefinitionRepository.findByRuleIdForUpdate`) is acquired, in its own transaction, by all three
paths before they decide:
1. `RuleAdminService` edit/selectMode/pause/resume/disable;
2. `MonitorProcessingService.process()` BEFORE any `rule_state`/outbox mutation — and it re-validates the
   fetched snapshot against the CURRENT committed definition, failing closed with a typed reason:
   `RULE_DISABLED_CURRENT`, `RULE_PAUSED_CURRENT`, `STALE_RULE_DEFINITION`, `RULE_DEFINITION_MISMATCH`;
3. `OutboxTxOps.prepareSend()` BEFORE the eligibility / SENDING authority decision.
No transaction holding the lock spans the Brapi fetch or the WAHA send. The observation is still
persisted for provenance when evaluation is denied; no rule_state mutation, no episode/rebaseline
consumption, no outbox row is produced.

**Linearization semantics (as implemented).**
- admin locks first → processing/prepareSend wait, then observe the committed state and fail closed;
- processing locks first → finishes atomically; a later pause cancels the resulting unsent PENDING;
- prepareSend locks first → SENDING authority commits; a later pause cannot claim the side effect was
  unsent;
- pause locks first → prepareSend waits then denies; zero adapter calls.

**Tests (`RuleLifecycleFenceTest`, full `@SpringBootTest`, no ambient tx, latches):**
`pauseBeforeProcessDeniesStaleEvaluation` (RULE_PAUSED_CURRENT, no state/PENDING),
`processWinsThenPauseCancelsResultingPending`, `editWinsSoStaleRevisionSnapshotIsDenied`
(STALE_RULE_DEFINITION), `concurrentPauseAndProcessAreLinearizedNoDeadlock` (completes → no deadlock; no
unsent PENDING survives an active rule).

## B — retry-safe / idempotent admin mutations

- **pause:** active→paused real transition (cancels unsent PENDING); already-paused = idempotent no-op,
  no revision mint.
- **resume:** paused→active sets `rebaseline_required` ONLY on the real transition; already-active =
  idempotent no-op that does NOT re-mark rebaseline (a retried resume cannot consume a fresh-baseline
  window and suppress a later crossing).
- **selectMode:** selecting the already-current mode is an idempotent no-op returning the current
  revision WITHOUT bumping; a real change bumps + cancels old PENDING.
- **edit:** requires `expectedRevision`; exact match applies once + bumps; a stale value is a
  `StaleRevisionException` → HTTP 409 with no mutation. `@Version` remains the DB concurrency guard.

Tests: `duplicateResumeDoesNotReMarkRebaseline`, `duplicatePauseIsIdempotent`,
`duplicateCrossingSelectionDoesNotMintExtraRevision`, `staleExpectedRevisionEditIsRejected`, plus the
HTTP-level equivalents in `AdminSecurityEnabledTest` (#13/#14/#15).

## C–F — authenticated private admin/status surface (SEC-01)

**Fail-closed defaults.** `b3monitor.admin.enabled=false` by default; HTTP bound to `127.0.0.1`
(`server.address`); no committed credential. When enabled, the username + a one-way **BCrypt** password
HASH come from the environment (`B3MONITOR_ADMIN_USERNAME` / `B3MONITOR_ADMIN_PASSWORD_HASH`); if either
is blank the context FAILS TO START (`AdminUserConfig` throws) — no anonymous or Boot-generated-password
fallback.

**Security (two mutually-exclusive chains on `/api/admin/**`).** Disabled → deny-all (403), controller
bean absent. Enabled → session-based auth; reads require authentication, mutations require role `ADMIN`;
**CSRF enforced** on mutations (never disabled); anonymous → 401.

**Endpoints (DTOs only — no JPA entity serialized, no secret/payload).** Reads: `GET /status`,
`/rules`, `/rules/{id}`, `/outbox/metrics`, `/outbox/reconciliation` (sanitized rows),
`/quotes/{ticker}/latest` (freshness/provenance, bounded ticker). Mutations: create (stays UNSELECTED),
edit (expectedRevision), select CROSSING (LEVEL rejected), pause, resume, disable. There is NO endpoint
for asset activation, recipients, WAHA, Brapi credentials, live send or trading.

**Security tests (`AdminSecurityEnabledTest` + `AdminSurfaceDisabledTest`), all green:**
1 anonymous read 401 · 2 anonymous mutation 401 · 3 non-admin read 200 · 4 non-admin mutation 403 ·
5 ADMIN mutation without CSRF 403 · 6 ADMIN mutation with CSRF 200 · 7 Basic auth with synthetic admin
(real BCrypt) 200 · 8 wrong password 401 · 9 admin disabled → controller absent + 403 ·
10 no credential echoed in a response · 11 create returns UNSELECTED · 12 LEVEL rejected 400 ·
13 stale expectedRevision edit 409 (no mutation) · 14 duplicate resume does not re-mark rebaseline ·
15 duplicate CROSSING selection mints no extra revision. The BCrypt admin hash is generated dynamically
in the test from a synthetic password (`@DynamicPropertySource`); no plaintext credential is committed.

## G — Q-19 boundaries

No LEVEL initial policy, false-confirmation count, cooldown, or expiry default was invented. UNSELECTED
remains the create default; only explicit CROSSING operates; LEVEL fails closed in the domain, the
scheduler/guard, and the admin API. RUL-03/RUL-05 remain pending.

## H — PostgreSQL IT — NOT_RUN

Docker absent. The fence, retry semantics and admin surface are proven on H2. The real release gate
(PESSIMISTIC row-lock semantics, V1–V12 DDL, concurrency, Boot 4.1.1 + Testcontainers 2.0.5 startup)
remains for `mvn verify -Pdocker-it` on a Docker host. No PASS claimed. No new migration this cycle
(no schema change).

## Boundaries honoured

No push; no live Brapi; no live WAHA; no OPERATIONAL_ACTIVATION; no deploy; no production DB; no
sibling-repo change; no non-synthetic credentials; no trading. Workers remain disabled by default.
Seven protected inputs byte-identical to the preflight baseline.
