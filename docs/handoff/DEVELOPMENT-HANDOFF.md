# B3 Monitor — Development Handoff (rev 10)

**MVP vertical:** validated B3 quote monitoring with explainable CROSSING price alerts, a durable
submission-authority outbox, a linearizable rule-lifecycle fence, and a fail-closed authenticated
private admin surface. Offline/local only — no live Brapi, no live WAHA, no activation, no deploy.

## Build & test

- **Spring Boot 4.1.1**, Java 21. `mvn test` → **173 passed, 0 failures, 0 errors** (H2 slice + unit + security).
- **PostgreSQL IT NOT_RUN** (Docker absent). Run `mvn verify -Pdocker-it` where Docker exists.
- Migrations **V1–V12** (Flyway). No new migration this cycle (A–F were behaviour/surface, not schema).
- Testcontainers **2.0.5** (BOM-managed); Spring Security + `spring-boot-webmvc-test` added this cycle.

## Git (branch `checkpoint/cycle7-reviewed`, NO push — `git branch -r` shows only `origin/main`)

| SHA | What |
|---|---|
| `6de333d` | Initial commit |
| `41fa260` | cycle-7 reviewed baseline |
| `0daada3` | cycle-8 functional fixes |
| `e9f5725` | cycle-8 docs |
| `172e768` | cycle-9 functional fixes |
| `e864fad` | Spring Boot 4.1.1 migration |
| `6efd84f` | cycle-9 docs |
| `6429cfa` | **cycle-10: lifecycle fence + retry-safe mutations** |
| `01001ab` | **cycle-10: admin/status surface (SEC-01)** |
| (this) | **cycle-10 docs + handoff archive** |

## Cycle-10 changes (detail: `CYCLE10-REVIEW-EVIDENCE.md`)

- **A — linearizable lifecycle fence:** a `PESSIMISTIC_WRITE` lock on `rule_definition`
  (`findByRuleIdForUpdate`) shared by `RuleAdminService` mutations, `MonitorProcessingService.process()`
  and `OutboxTxOps.prepareSend()`. `process()` re-validates the fetched snapshot against the current
  committed definition (typed denials: `RULE_PAUSED_CURRENT`, `RULE_DISABLED_CURRENT`,
  `STALE_RULE_DEFINITION`, `RULE_DEFINITION_MISMATCH`); the observation is still persisted but no
  state/episode/outbox mutation happens on a denial. No lock spans the fetch or the send.
- **B — retry-safe mutations:** pause/resume/selectMode idempotent (resume marks rebaseline only on the
  real paused→active transition); edit requires `expectedRevision` (stale → 409, no mutation).
- **C–F — admin/status surface (SEC-01):** Spring Security, session + CSRF, loopback bind,
  `b3monitor.admin.enabled=false` default, env-only BCrypt credentials, fail-closed when enabled without
  creds. DTO-only read models (status/rules/outbox metrics/reconciliation/quote freshness) and typed
  mutations (create→UNSELECTED, edit→expectedRevision, select CROSSING, pause/resume/disable). No asset
  activation / recipients / WAHA / Brapi / live-send / trading endpoint. 15-case security matrix green.

## Handoff packaging — SINGLE ATTACHMENT

Active docs:
- `docs/handoff/DEVELOPMENT-HANDOFF.md` (this file)
- `docs/handoff/CYCLE10-REVIEW-EVIDENCE.md`

Archived history (in the ZIP for traceability, not to be attached separately):
- `docs/handoff/archive/cycles/` — CYCLE6/7/8/9 review evidence
- `docs/handoff/archive/migrations/` — SPRING-MIGRATION-STUDY.md, SPRING-41-PROBE-RESULT.md

Generated, gitignored: `docs/handoff/b3-monitor-review.zip`, `docs/handoff/test-evidence-cycle10.log`.

**Para revisão no ChatGPT, anexe apenas `docs/handoff/b3-monitor-review.zip`; não anexe os arquivos do
archive nem os handoffs individualmente, salvo se o revisor pedir.**

## Running the admin surface locally (operator notes)

Disabled by default. To enable on loopback:
`B3MONITOR_ADMIN_ENABLED=true`, `B3MONITOR_ADMIN_USERNAME=<user>`,
`B3MONITOR_ADMIN_PASSWORD_HASH=<bcrypt-hash>` (generate with a BCrypt encoder; never commit it),
optional `B3MONITOR_BIND_ADDRESS=127.0.0.1` (default). Missing creds while enabled ⇒ startup fails.

## Open items / recommended next 1–3 actions

1. **PostgreSQL IT** on a Docker host: `mvn verify -Pdocker-it` — the real gate for PESSIMISTIC row-lock
   lifecycle ordering, pause-vs-process / pause-vs-prepareSend races, V1–V12 DDL, and Boot 4.1.1 +
   Testcontainers 2.0.5 startup.
2. **Admin UI / session-expiry hardening**: the current surface is API-only; a thin private UI and an
   explicit session-timeout policy test would complete the SEC-01 browser story.
3. **RUL-03/RUL-05 + LEVEL (Q-19)** remain human-gated: initial-consumed, false-confirmation count,
   cooldown, LEVEL initial policy are modelled fail-closed, not implemented.

## Still prohibited

push · live Brapi · live WAHA · OPERATIONAL_ACTIVATION · deploy · production DB · sibling-repo changes ·
non-synthetic credentials · trading. Seven protected inputs byte-identical.
