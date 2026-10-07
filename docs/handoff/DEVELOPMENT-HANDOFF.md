# B3 Monitor — Development Handoff (rev 9)

**MVP vertical:** validated B3 quote monitoring with explainable CROSSING price alerts, a durable
submission-authority outbox, and a fail-closed operational-activation gate. Offline/local only — no live
Brapi, no live WAHA, no activation, no deploy.

## Build & test

- **Spring Boot 4.1.1**, Java 21. `mvn test` → **148 passed, 0 failures, 0 errors** (H2 slice + unit).
- **PostgreSQL IT NOT_RUN** (Docker absent). Run `mvn verify -Pdocker-it` where Docker exists.
- Migrations **V1–V12** (Flyway). No new migration this cycle (A–G were behaviour, not schema).
- Testcontainers **2.0.5** (BOM-managed) via `testcontainers-junit-jupiter` + `testcontainers-postgresql`.

## Git (branch `checkpoint/cycle7-reviewed`, NO push)

| SHA | What |
|---|---|
| `6de333d` | Initial commit |
| `41fa260` | cycle-7 reviewed baseline (123 tests) |
| `0daada3` | cycle-8 functional fixes (134 tests) |
| `e9f5725` | cycle-8 docs |
| `172e768` | **cycle-9 functional fixes** (148 tests) |
| `e864fad` | **chore: migrate to Spring Boot 4.1.1** |

`git branch -r` shows only `origin/main` (nothing pushed). Working tree clean.

## Cycle-9 changes (see CYCLE9-REVIEW-EVIDENCE.md for detail + regressions)

- **A — resume baseline persistence:** the rebaseline branch persists the *evaluated* state (not the stale
  entity), so the first eligible post-resume comparison is kept and a later observed crossing fires.
- **B — latch preserved on resume:** `markRebaselineRequired()` sets only the marker; the LATCHED/ARMED
  phase and episode epoch survive resume; the first eligible observation cannot fire.
- **C — no implicit CROSSING:** `PriceRule` convenience ctors default `UNSELECTED`; `withMode()` is the
  explicit path; `RuleEvaluator` and `process()` fail closed on any non-CROSSING mode.
- **D — expired SENDING closes its attempt UNKNOWN** (`lease-expired`) transactionally; IN_FLIGHT has no
  attempt and safely recovers to PENDING; integrity breaches fail closed with a metric/log.
- **E — attempt-ledger invariants:** single `OPEN→CLOSED` transition (second close / null rejected);
  `record()` requires the matching attempt (no terminal state without provenance).
- **F — optimistic-race reporting:** a pre-send race reports the actual persisted state, never a
  fabricated UNKNOWN; no adapter call. Stale `findClaimable()` doc corrected.
- **G — versioned calendar consolidated:** old `TradingCalendar` deleted; scheduler uses the single
  `TradingSessionCalendar`; dataset carries version/source/timezone/windows (regular + per-date special);
  no hardcoded hours in production; importer validation matches its claims; default UNKNOWN/"none".
- **H — Spring Boot 4.1.1 migration DONE** (Jackson 3, modern test modules, Testcontainers 2.0.5,
  `@MockitoBean`). See SPRING-41-PROBE-RESULT.md.

## State-machine invariants (unchanged, still enforced)

- Outbox transitions go through `OutboxTransitions`; `SENDING`/`IN_FLIGHT` are never declared "definitely
  not sent"; `SENDING` is the submission-authority state committed before any I/O.
- Rule revision reconcile: STALE (reject) / SAME / BUMPED (re-baseline + cancel old PENDING).
- Fail-closed everywhere: authorization → calendar → quota; non-operable mode; UNKNOWN quote.

## Open items / recommended next 1–3 actions

1. **PostgreSQL IT** on a Docker host: `mvn verify -Pdocker-it` (release gate for V1–V12 DDL, optimistic
   races, attempt-ledger constraints, Boot 4.1.1 + Testcontainers 2.0.5 container startup).
2. **Admin/status surface (item J, NOT started):** authenticated private read/manage surface
   (list/create/edit/pause/resume/select-CROSSING; outbox/reconciliation metrics; quote freshness) under
   SEC-01 (auth required, non-admin denied, CSRF + session-expiry tested, synthetic identities,
   local-bind). No recipient mutation / live WAHA.
3. **RUL-03/RUL-05 completion** (initial-consumed, false-confirmation count, cooldown) and the **LEVEL**
   mode pending **Q-19** approval — currently modelled but fail-closed, not implemented.

## Still prohibited

push · live Brapi · live WAHA · OPERATIONAL_ACTIVATION · deploy · production DB · sibling-repo changes ·
non-synthetic credentials · trading. Seven protected inputs byte-identical.
