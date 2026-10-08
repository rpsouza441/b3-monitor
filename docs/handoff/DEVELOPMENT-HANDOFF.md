# B3 Monitor — Development Handoff (rev 14)

**MVP vertical:** validated B3 quote monitoring with explainable CROSSING price alerts, a durable
submission-authority outbox, a linearizable rule-lifecycle fence, an append-only admin audit ledger, and a
fail-closed authenticated private admin surface + browser UI (status / readiness / assets / rules / outbox /
alerts / audit). Offline/local only — no live Brapi, no live WAHA, no activation, no deploy.

## Build & test

- **Spring Boot 4.1.1**, Java 21. `mvn test` → **216 passed, 0 failures, 0 errors**.
- **PostgreSQL IT NOT_RUN** (Docker absent); `OutboxPostgresIT` + `LifecycleFencePostgresIT` (true concurrency
  races, cycle-14-hardened with a `pg_blocking_pids` lock-wait proof + `competitorEntered` latch) compile and
  run under `mvn verify -Pdocker-it`. Runtime PESSIMISTIC_WRITE: **NOT_PROVEN**.
- Migrations **V1–V13** (Flyway).

## Git (branch `checkpoint/cycle7-reviewed`, NO push — `git branch -r` = only `origin/main`)

| SHA | What |
|---|---|
| `0035487`..`9b0c8d5` | cycle-12 (UI RBAC + session expiry, Postgres V13 IT prep, UI-01, UI-03 groundwork, docs) |
| `62f053f`..`653674c` | cycle-13 (UI-03 full lifecycle, true concurrency races, bounded session timeout, docs) |
| `c9f0aac` | **cycle-14: harden postgres concurrency tests (no swallowed exceptions, pg_blocking_pids proof)** |
| `0068130` | **cycle-14: UI-03 semantic ordering (intentCreatedAt DESC, id DESC)** |
| `0f70334` | **cycle-14: correct README/STATE factual drift** |
| `f4e3b74` | **cycle-14: read-only UI-02 readiness view** |
| (this) | **cycle-14 docs + archive CYCLE13** |

## Cycle-14 changes (detail: `CYCLE14-REVIEW-EVIDENCE.md`)

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
