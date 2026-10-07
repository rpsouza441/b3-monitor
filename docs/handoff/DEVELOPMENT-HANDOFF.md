# B3 Monitor — Development Handoff (rev 7, cycle 7: submission authority, typed transport, persistent rules)

**Date:** 2026-10-07 · **Repo:** `C:\ws\b3-monitor`
**Build:** `mvn "-Dspring.profiles.active=test" test` → **BUILD SUCCESS, 123 tests, 0 failures, 0 errors.**
**Spring Boot 3.3.13.** No commit, no deploy, no real WhatsApp send, no live polling, no real Brapi
request, no sibling-repo change. Git HEAD unchanged `6de333d`; 7 protected inputs byte-unchanged;
no `.env`; `.gitignore` present. Docker absent → PostgreSQL IT **NOT_RUN**.

Full defect/fix/test detail: `docs/handoff/CYCLE7-REVIEW-EVIDENCE.md`. Probe:
`docs/handoff/SPRING-41-PROBE-RESULT.md`.

## Summary
Closed the two remaining dispatch P0s (eligibility→send TOCTOU; transport returning arbitrary states),
imposed an explicit transition matrix, unified 429/2xx quota telemetry, built the persistent typed rule
registry as the single rule source, added bounded drain + attempt provenance, and advanced versioned
trading-calendar groundwork. Verified the cycle-6 review's findings against code — all real.

## P0/P1 fixed
- **P0-1 (TOCTOU):** `SENDING` submission-authority state. Flow is claim → `prepareSend` (reload +
  active-claim + lease + re-eligibility, commit `SENDING`+`send_started_at` BEFORE I/O) → `adapter.send`
  → `record`. Pre-send terminalization wins → no adapter call; post-`SENDING` → never "definitely not
  sent"; no DB tx across the adapter call.
- **P0-2 (transport type):** `WahaOutboundAdapter.send()` returns a narrow `SubmissionResult`
  (ACCEPTED/DEFINITE_FAILURE/UNKNOWN + safe metadata). The dispatcher owns the mapping; `record()`
  requires `SENDING`. Duplicate-send-by-`PENDING` is impossible by type.
- **C (transition matrix):** `OutboxTransitions` is the single source of legal edges; `abandon()` legal
  only from UNKNOWN_OUTCOME/FAILED (ACCEPTED/SENDING/PENDING refused); separate `cancelPending()`.
- **D (unified quota telemetry):** 429 now carries the same `QuotaSignal` (window/limit/serverDate) as
  2xx; billing-cycle detection still needs window + matching limit + positive delta; high-water never
  lowers consumed; all cycle-4/5/6 reset regressions green.
- **E (persistent rule registry):** `rule_definition` (immutable monotonic revision, enabled/paused,
  optimistic lock, bounded typed fields) is the single `RuleSource` for the scheduler AND `RuleRegistry`
  for the guard; `RuleAdminService` (local, no HTTP) edits it. **RUL-01/RUL-05 NOT marked complete.**
- **F (bounded ops):** `drainBatch()` (default 50, no `Integer.MAX_VALUE`); durable attempt provenance
  (send/finished/provider-id/accepted-at); full-lifecycle metrics + oldest-pending age.
- **G (versioned calendar):** `TradingSessionCalendar` + importer/validation contract + fixtures;
  production stays UNKNOWN/"none" — no invented holidays.

## Tests
**123 run, 123 pass.** New: `OutboxTransitionsTest`, `PersistentRuleRegistryTest`,
`TradingCalendarImporterTest`; expanded dispatcher (SENDING/TOCTOU latch), reconciliation (matrix),
client (429 unified), quota (billing-cycle). `test-evidence-cycle7.log` has the per-suite breakdown.

## Migrations
**V9** outbox attempt-lineage columns; **V10** `rule_definition` table. Additive; `ddl-auto=validate`
on the gated IT proves V1–V10 match the entities.

## PostgreSQL IT
**NOT_RUN** (Docker absent). Covers V1–V10, two-consumer race, quota race, expired-lease quarantine,
abandon-terminal. Run `mvn verify -Pdocker-it` on a Docker host.

## Spring probe (no overclaim)
Main compiles under 4.1.0 after Jackson 2→3; NEW blocker at test compile — Boot 4 test-slice
modularization (`@DataJpaTest` needs a modular test dependency). Jackson is NOT the only blocker; the
full 4.1 suite run was not reached. Main tree stays on 3.3.13 (no commit authorization).

## 9. Remaining blockers
- **Docker absent** → V1–V10 + the real races NOT_RUN. H2 is not a substitute for PostgreSQL DDL/tx proof.
- **Git checkpoint recommended but not authorized.** HEAD is still `6de333d` with 7 cycles of
  uncommitted work (10 migrations, 123 tests). The review recommends a single reviewed checkpoint commit
  before the Spring 4.1 migration — this needs explicit user authorization; nothing was committed.
- **Spring 3.x fully OSS-EOL;** 4.1 migration is a multi-step change (Jackson 3 + `@MockitoBean` +
  test-slice modular dependency), scoped in the probe doc; needs a checkpoint + its own cycle.
- **Human/approval gates unchanged:** OPERATIONAL_ACTIVATION per asset; WAHA contract (Q-09/Q-25);
  dedicated-Brapi-account declaration; CROSSING/LEVEL (Q-19); previousClose basis (Q-20); version pins
  (Q-14/27); KNHY11 (Q-15); SNAG11 class. The rule registry has no authenticated admin surface yet
  (domain service only). No real WAHA/Brapi/activation/deploy.

## 10. Exact recommended next actions (1–3)
1. **Authorize a single reviewed Git checkpoint commit** of the current green tree (after re-confirming
   the 7 protected hashes + no `.env`/`target`/secrets), so 7 cycles of work gain a recoverable history
   before the major migration.
2. On an isolated Docker host, run `mvn verify -Pdocker-it` (V1–V10 + the SENDING-authority, transition,
   two-consumer and quota races on real PostgreSQL). Record PASS/NOT_RUN honestly.
3. Execute the scoped **Spring 4.1 migration** per `SPRING-41-PROBE-RESULT.md` (3.5.6 checkpoint →
   Jackson 2→3 → `@MockitoBean`/`@MockitoSpyBean` → add the Boot 4 modular JPA test-slice dependency),
   gated by the full suite + the Docker IT, only with the checkpoint from (1).

OPERATIONAL activation, the real WAHA adapter, live polling and any Brapi account-dedication stay gated
pending human approval; the authenticated rule-admin surface is the next product step after a checkpoint.
