# B3 Monitor — Development Handoff (rev 11)

**MVP vertical:** validated B3 quote monitoring with explainable CROSSING price alerts, a durable
submission-authority outbox, a linearizable rule-lifecycle fence, an append-only admin audit ledger, and a
fail-closed authenticated private admin surface + thin browser UI. Offline/local only — no live Brapi, no live
WAHA, no activation, no deploy.

## Build & test

- **Spring Boot 4.1.1**, Java 21. `mvn test` → **197 passed, 0 failures, 0 errors**.
- **PostgreSQL IT NOT_RUN** (Docker absent). Run `mvn verify -Pdocker-it` where Docker exists.
- Migrations **V1–V13** (Flyway); V13 adds the admin audit ledger + a latest-quote index.
- Added this cycle: Spring Security hardening, Thymeleaf private UI.

## Git (branch `checkpoint/cycle7-reviewed`, NO push — `git branch -r` shows only `origin/main`)

| SHA | What |
|---|---|
| `6429cfa` | cycle-10 lifecycle fence + retry-safe mutations |
| `01001ab` | cycle-10 admin/status surface |
| `bc7460f` | cycle-10 docs |
| `d0d5e85` | **cycle-11: harden admin security (no gen password, deny-by-default, loopback invariant, sessions)** |
| `d657a5a` | **cycle-11: prove dispatch lifecycle fence + bound admin reads** |
| `466621e` | **cycle-11: admin mutation audit ledger (V13)** |
| `b5724d7` | **cycle-11: thin private browser UI** |
| (this) | **cycle-11 docs + archive CYCLE10** |

## Cycle-11 changes (detail: `CYCLE11-REVIEW-EVIDENCE.md`)

- **A** — Boot `UserDetailsServiceAutoConfiguration` excluded; deliberate empty user store when disabled, env
  BCrypt admin when enabled; no generated password is created or logged.
- **B** — global deny-by-default: both chains cover ALL requests, ending in `anyRequest().denyAll()`; an
  unapproved route is denied, never public.
- **C** — loopback is a startup INVARIANT when admin is enabled (wildcard/LAN ⇒ context fails).
- **D** — real browser `formLogin`/`logout`/session inside the active chain; session reuse without Basic;
  invalidated session no longer authenticates; CSRF enforced on mutations.
- **E** — integrated pause↔prepareSend / edit↔prepareSend fence regressions against the real services.
- **F** — bounded admin reads (single-row latest quote; capped reconciliation + audit pages).
- **G** — append-only `admin_audit_event` (V13); every mutation audited (actor/action/revisions/outcome), no
  secret stored; ADMIN-only bounded read; reusable by a future command layer.
- **H** — thin Thymeleaf private UI under `/admin/**` (login/status/rules/outbox/audit), same service layer as
  the API, CSRF forms, no secret/activation/live-send controls.

## Running the admin surface + UI locally (operator notes)

Disabled by default. To enable on loopback:
`B3MONITOR_ADMIN_ENABLED=true`, `B3MONITOR_ADMIN_USERNAME=<user>`,
`B3MONITOR_ADMIN_PASSWORD_HASH=<bcrypt-hash>` (generate with a BCrypt encoder; never commit it),
optional `B3MONITOR_BIND_ADDRESS=127.0.0.1` (loopback enforced). Non-loopback or missing creds ⇒ startup fails.
UI at `/admin/login`; JSON API under `/api/admin/**`.

## Open items / recommended next 1–3 actions

1. **PostgreSQL IT** on a Docker host (`mvn verify -Pdocker-it`) — PESSIMISTIC row-lock ordering, the
   pause↔process and pause↔prepareSend races, V1–V13 DDL incl. the audit table, Boot 4.1.1 + Testcontainers
   2.0.5 startup. Real release gate.
2. **UI-01/UI-03 pages** (asset freshness view; alert/channel-outcome view) and session-timeout policy
   surfaced in the UI; small hygiene fixes (Mockito agent, H2 dialect) if a clean path exists.
3. **RUL-03/RUL-05 + LEVEL (Q-19)** remain human-gated: initial-consumed, false-confirmation count, cooldown,
   LEVEL initial policy are modelled fail-closed, not implemented.

## Still prohibited

push · live Brapi · live WAHA · OPERATIONAL_ACTIVATION · deploy · production DB · sibling-repo changes ·
non-synthetic credentials · trading. Seven protected inputs byte-identical.

## Handoff packaging — SINGLE ATTACHMENT

Active docs: `docs/handoff/DEVELOPMENT-HANDOFF.md`, `docs/handoff/CYCLE11-REVIEW-EVIDENCE.md`.
Archive (in the ZIP for traceability): `docs/handoff/archive/cycles/` (CYCLE6–CYCLE10),
`docs/handoff/archive/migrations/`. Generated/gitignored: `b3-monitor-review.zip`, `test-evidence-cycle11.log`.

**Para revisão no ChatGPT, anexe apenas `docs/handoff/b3-monitor-review.zip`; não anexe os arquivos do archive
nem os handoffs individualmente, salvo se o revisor pedir.**
