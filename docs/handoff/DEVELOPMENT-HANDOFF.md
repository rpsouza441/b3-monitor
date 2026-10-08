# B3 Monitor — Development Handoff (rev 16)

**MVP vertical:** validated B3 quote monitoring with explainable CROSSING price alerts, a durable
submission-authority outbox, a linearizable rule-lifecycle fence, an append-only admin audit ledger, a
fail-closed authenticated private admin surface + browser UI (status / readiness / assets / analytics /
imports / rules / outbox / alerts / audit), and a fail-closed analytics-snapshot CONSUMER contract with an
ADMIN preview→commit importer. Offline/local only — no live Brapi, no live WAHA, no activation, no deploy.

## Build & test

- **Spring Boot 4.1.1**, Java 21. `mvn test` → **231 passed, 0 failures, 0 errors**.
- **PostgreSQL IT NOT_RUN** (Docker absent); `OutboxPostgresIT` (V1–V14 incl. the V14 analytics schema +
  the extended audit CHECK) + `LifecycleFencePostgresIT` (true concurrency races; the lock-wait proof reads
  `pg_backend_pid()` and takes the `PESSIMISTIC_WRITE` lock through the SAME Hibernate/JPA connection via
  `EntityManager.unwrap(Session).doReturningWork`, with a `pg_blocking_pids()` block assertion) compile and
  run under `mvn verify -Pdocker-it`. Runtime PESSIMISTIC_WRITE: **NOT_PROVEN**.
- Migrations **V1–V14** (Flyway). V14 = analytics_snapshot/analytics_context provenance + IMPORT_SNAPSHOT audit action.

## Git (branch `checkpoint/cycle7-reviewed`, NO push — `git branch -r` = only `origin/main`)

| SHA | What |
|---|---|
| `0035487`..`9b0c8d5` | cycle-12 (UI RBAC + session expiry, Postgres V13 IT prep, UI-01, UI-03 groundwork, docs) |
| `62f053f`..`653674c` | cycle-13 (UI-03 full lifecycle, true concurrency races, bounded session timeout, docs) |
| `c9f0aac`..`8d5eec2` | cycle-14 (concurrency-test integrity, UI-03 ordering, README/STATE drift, UI-02 readiness, docs) |
| `f07b8e7`..`06492c6` | cycle-15 (transaction-bound lock proof, no swallowed races, factual drift, readiness dimensions, docs) |
| `28c16a3` | **cycle-16: analytics consumer contract v1 + fail-closed validator + V14 provenance** |
| `8cb0a09` | **cycle-16: ADMIN preview→commit importer + UI-01 analytics context + UI-02 import history** |
| `ac9ee2b` | **cycle-16: analytics import validation/idempotency/RBAC + V14 postgres schema tests** |
| `c7ecc40` | **cycle-16: analytics snapshot producer-boundary document** |
| (this) | **cycle-16 docs rev 16 + CYCLE16 evidence + archive CYCLE15** |

## Cycle-16 changes (detail: `CYCLE16-REVIEW-EVIDENCE.md`)

- **Consumer contract v1 (FIN-01/03/04).** `AnalyticsSnapshotContract` + a fail-closed
  `AnalyticsSnapshotValidator`: typed daily indicators (IND-01/02/04/05) each with its own readiness + FIN-02
  context metrics; canonical-checksum idempotency; rejects unknown schema/malformed/unknown-ticker/future-asof/
  checksum-mismatch/oversize/private-portfolio keys (XIRR/holdings/…)/dangerous content. No private data.
- **V14 provenance.** `analytics_snapshot` (unique snapshotId) + `analytics_context` (typed indicators, bounded
  context string, cascade); V13 audit CHECK extended with `IMPORT_SNAPSHOT`.
- **ADMIN preview→commit importer.** Cryptographic preview token binds the committed bytes (changed content
  rejected); idempotent on same id+checksum; conflict on same id/different checksum; appends IMPORT_SNAPSHOT
  audit. Authorizes nothing, enables no worker, triggers no Brapi/WAHA, changes no rule.
- **UI-01** `/admin/analytics`: analytics context SEPARATE from quote freshness (analytics-stale vs
  analytics-missing vs NOT_INTEGRATED vs CONSUMER_VERIFIED_SYNTHETIC). **UI-02** `/admin/imports`: bounded
  import history + an `analytics_consumer` readiness component (wiring READY, runtime NOT_VERIFIED until a real
  snapshot). **UI-02 stays PARTIAL.**
- **Producer boundary** `docs/contracts/ANALYTICS-SNAPSHOT-CONTRACT.md`: what a future authorized
  projecao-carteira exporter must produce; "consumer contract implemented" ≠ "integration complete".
  projecao-carteira NOT edited. **No REAL producer snapshot consumed** (synthetic only).

## Cycle-15 changes (detail: `archive/cycles/CYCLE15-REVIEW-EVIDENCE.md`)

- **A — lock-proof connection identity.** The `pg_blocking_pids()` lock-wait proof now reads
  `pg_backend_pid()` and takes the `PESSIMISTIC_WRITE` row lock through the SAME Hibernate/JPA connection
  (`EntityManager.unwrap(Session).doReturningWork`), so the pid proven blocked is provably the lock holder —
  not an unrelated pooled backend. NOT_RUN (Docker absent).
- **B — no swallowed concurrency failures.** `SchedulerTransactionTest` first-creation race and
  `RuleLifecycleFenceTest` pause-vs-process no longer `catch (Exception ignored)`; workers rethrow, `Future.get`
  surfaces failures, and only a typed uniqueness/optimistic conflict is accepted. Tree scan → 0 swallowed catches.
- **C — factual drift fully closed.** README (both 213→216) and STATE corrected to current reality; phase
  closure 0/8; seven protected inputs untouched; stale-fact scan clean on the overlay.
- **D — readiness semantics.** `ReadinessComponentView` reports independent `wiringStatus` /
  `operationalStatus` / `runtimeStatus` + detail. Read-only; **UI-02 stays PARTIAL.**
- **E — optional** bulk-fetch attempt optimization **skipped** (bounded 1+N is not a defect).

## Cycle-14 changes (detail: `archive/cycles/CYCLE14-REVIEW-EVIDENCE.md`)

- **A — concurrency-test integrity.** Removed the catch-and-ignore in the pause-vs-process race (only an exact
  optimistic-lock conflict is legal; anything else fails via `Future.get`). Fixed the lock-wait false positive
  with a `competitorEntered` latch + a database-level `pg_blocking_pids()` block assertion. Corrected race-3
  wording: a pre-edit authorization is valid at its linearization point; the invariant is that the OLD revision
  gets no NEW authority after the bump.
- **B — UI-03 ordering** changed to `intentCreatedAt DESC, id DESC` with a semantic newest-first regression +
  an id tie-break regression. States/cap/attempts/semantics unchanged.
- **C — factual drift** corrected in README (134→216) and STATE (cycle/version/commit reality); phase closure
  stays 0/8; seven protected inputs untouched.
- **E — UI-02 readiness** read-only view (`/admin/readiness` + JSON) with explicit NOT_INTEGRATED/NOT_READY
  causes. Activates nothing. **UI-02 stays PARTIAL.**

## Cycle-13 changes (detail: `archive/cycles/CYCLE13-REVIEW-EVIDENCE.md`)

- **P1-A** — `/admin/alerts` (+ `GET /api/admin/alerts`) now shows the FULL transport lifecycle
  (PENDING…SUPPRESSED) via a bounded `findRecentAlerts` query (hard-capped 100, ≤20 child attempts),
  not just dead-letters. ACCEPTED≠delivery_confirmed; UNKNOWN uncertain; FAILED/CANCELLED/EXPIRED/SUPPRESSED
  shown. Reconciliation (`/admin/outbox`) unchanged.
- **P1-B** — `LifecycleFencePostgresIT` gained true concurrent races (CyclicBarrier + 2 threads + independent
  tx + a latch-held `PESSIMISTIC_WRITE` lock proof). Compiled; **NOT_RUN** (no Docker) — runtime proof pending.
- **P2** — session timeout genuinely bounded (explicit 24h cap + 1800 default; 1s test preserved).

## Requirement status (acceptance-literal)

- **SEC-01 → DONE** (timeout claim now accurate).
- **UI-03 → DONE** for the current outbox contract (full lifecycle, logical/transport/attempt separated).
- **UI-01 → PARTIAL** (freshness real; daily indicators + Python context NOT_INTEGRATED).
- **UI-02 → PARTIAL** (rules audited + pause stops collection; catalog/policies/imports not covered).
- **RUL-03/RUL-05 → PARTIAL** (Q-19 unresolved; LEVEL/false-confirmation/cooldown fail-closed).
- **PostgreSQL runtime gate → NOT_RUN**; **true-race proof → written, NOT_RUN** pending Docker.

## Running the admin surface + UI locally

Disabled by default. Enable on loopback: `B3MONITOR_ADMIN_ENABLED=true`, `B3MONITOR_ADMIN_USERNAME=<user>`,
`B3MONITOR_ADMIN_PASSWORD_HASH=<bcrypt>`, optional `B3MONITOR_ADMIN_SESSION_TIMEOUT_SECONDS` (default 1800,
coerced to [1..86400]), `B3MONITOR_BIND_ADDRESS=127.0.0.1` (loopback enforced). Non-loopback or missing creds
⇒ startup fails. UI at `/admin/login`; JSON API under `/api/admin/**`.

## Open items / recommended next 1–3 actions

1. **PostgreSQL IT on a Docker host** (`mvn verify -Pdocker-it`): run the true concurrency races + V1–V13
   validate + V13 audit/index. This is the only remaining runtime proof of PESSIMISTIC_WRITE behaviour.
2. **UI-01 completion** needs a real daily-indicator / Python-context integration (currently NOT_INTEGRATED,
   gated on the HIS-*/Python boundary); **UI-02 breadth** (catalog/policy/import readiness visibility).
3. **RUL-03/RUL-05 + LEVEL (Q-19)** remain human-gated.

## Still prohibited

push · live Brapi · live WAHA · OPERATIONAL_ACTIVATION · deploy · production DB · sibling-repo changes ·
non-synthetic credentials · trading. Seven protected inputs byte-identical.

## Handoff packaging — SINGLE ATTACHMENT (count derived from the FINAL ZIP)

Active docs: `docs/handoff/DEVELOPMENT-HANDOFF.md`, `docs/handoff/CYCLE14-REVIEW-EVIDENCE.md`.
Archive (in the ZIP): `docs/handoff/archive/cycles/` (CYCLE6–CYCLE13), `docs/handoff/archive/migrations/`.
Generated/gitignored: `b3-monitor-review.zip`, `test-evidence-cycle14.log`. The ZIP's actual entry count +
SHA-256 are read back FROM the built ZIP and reported in the closure message. `.env.example` is a template,
NOT a real `.env`.

**Para revisão no ChatGPT, anexe apenas `docs/handoff/b3-monitor-review.zip`; não anexe os arquivos do archive
nem os handoffs individualmente, salvo se o revisor pedir.**
