# B3 Monitor — Development Handoff (rev 12)

**MVP vertical:** validated B3 quote monitoring with explainable CROSSING price alerts, a durable
submission-authority outbox, a linearizable rule-lifecycle fence, an append-only admin audit ledger, and a
fail-closed authenticated private admin surface + browser UI (status / assets / rules / outbox / alerts /
audit). Offline/local only — no live Brapi, no live WAHA, no activation, no deploy.

## Build & test

- **Spring Boot 4.1.1**, Java 21. `mvn test` → **205 passed, 0 failures, 0 errors**.
- **PostgreSQL IT NOT_RUN** (Docker absent); `OutboxPostgresIT` + `LifecycleFencePostgresIT` compile and run
  under `mvn verify -Pdocker-it`.
- Migrations **V1–V13** (Flyway).

## Git (branch `checkpoint/cycle7-reviewed`, NO push — `git branch -r` shows only `origin/main`)

| SHA | What |
|---|---|
| `d0d5e85`..`3430308` | cycle-11 (security hardening, fence proof, audit ledger, UI, docs) |
| `0035487` | **cycle-12: admin UI RBAC + real session expiry (SEC-01)** |
| `743e76c` | **cycle-12: complete Postgres lifecycle + V13 release-gate ITs** |
| `2c5ee53` | **cycle-12: asset freshness view (UI-01)** |
| `cd912ca` | **cycle-12: alert outcome lineage view (UI-03) + PRG form feedback** |
| (this) | **cycle-12 docs + archive CYCLE11** |

## Cycle-12 changes (detail: `CYCLE12-REVIEW-EVIDENCE.md`)

- **B** — UI RBAC: mutating POSTs under `/admin/**` and `GET /admin/audit` are ADMIN-only (matching the API);
  read-only GET UI is viewer-readable; deny-all catch-all retained. `RuleAdminService` stays role-free.
- **C** — SEC-01 session expiry: bounded configurable browser-session timeout (safe default, never infinite),
  enforced to the second via an `HttpSessionListener`, surfaced in status, and proven with a real
  RANDOM_PORT + real-cookie expiry test.
- **D** — Postgres ITs updated to the V13 contract (V1–V13 validate, audit persistence, latest-quote index)
  and a new real-fence `LifecycleFencePostgresIT` (pause↔prepareSend / stale-revision, real guard).
- **E** — UI-01 asset/freshness view from the trusted `AssetCatalog` (explicit UNKNOWN; daily indicators +
  Python context explicitly NOT_INTEGRATED, never synthesized).
- **F** — UI-03 alert view separating logical alert / transport state / attempt lineage (read-only, bounded).
- **G** — PRG + flash form feedback (sanitized errors, clear conflict, no false SUCCESS).

## Requirement status (acceptance-literal)

- **SEC-01 → DONE** (auth, non-admin mutation denied API+UI, CSRF, real session expiry tested).
- **UI-01 → PARTIAL** (asset/freshness real; daily indicators + Python context NOT_INTEGRATED).
- **UI-02 → PARTIAL** (rules audited + pause stops collection; catalog/policies/imports not covered).
- **UI-03 → substantially met** for the current outbox contract (logical/transport/attempt separated).
- **RUL-03/RUL-05 → PARTIAL** (Q-19 unresolved; LEVEL/false-confirmation/cooldown fail-closed).

## Running the admin surface + UI locally

Disabled by default. Enable on loopback: `B3MONITOR_ADMIN_ENABLED=true`, `B3MONITOR_ADMIN_USERNAME=<user>`,
`B3MONITOR_ADMIN_PASSWORD_HASH=<bcrypt>`, optional `B3MONITOR_ADMIN_SESSION_TIMEOUT_SECONDS` (default 1800),
`B3MONITOR_BIND_ADDRESS=127.0.0.1` (loopback enforced). Non-loopback or missing creds ⇒ startup fails. UI at
`/admin/login`; JSON API under `/api/admin/**`.

## Open items / recommended next 1–3 actions

1. **PostgreSQL IT** on a Docker host (`mvn verify -Pdocker-it`): V1–V13 validate, both lifecycle ITs
   (pause↔process, pause↔prepareSend, stale-revision), V13 audit + index. Real release gate.
2. **UI-01 completion** requires a real daily-indicator / Python-context integration (currently NOT_INTEGRATED)
   — gated on the HIS-*/Python boundary; and UI-02 breadth (catalog/policies/imports).
3. **RUL-03/RUL-05 + LEVEL (Q-19)** remain human-gated.

## Still prohibited

push · live Brapi · live WAHA · OPERATIONAL_ACTIVATION · deploy · production DB · sibling-repo changes ·
non-synthetic credentials · trading. Seven protected inputs byte-identical.

## Handoff packaging — SINGLE ATTACHMENT (count derived from the FINAL ZIP)

Active docs: `docs/handoff/DEVELOPMENT-HANDOFF.md`, `docs/handoff/CYCLE12-REVIEW-EVIDENCE.md`.
Archive (in the ZIP): `docs/handoff/archive/cycles/` (CYCLE6–CYCLE11), `docs/handoff/archive/migrations/`.
Generated/gitignored: `b3-monitor-review.zip`, `test-evidence-cycle12.log`. The ZIP's actual entry count and
SHA-256 are read back FROM the built ZIP and reported in the closure message (not hardcoded here).

**Para revisão no ChatGPT, anexe apenas `docs/handoff/b3-monitor-review.zip`; não anexe os arquivos do archive
nem os handoffs individualmente, salvo se o revisor pedir.**
