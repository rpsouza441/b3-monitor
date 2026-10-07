# B3 Monitor — Development Handoff (rev 8, cycle 8: local checkpoint + rule lifecycle + attempt ledger)

**Date:** 2026-10-07 · **Repo:** `C:\ws\b3-monitor` · **Branch:** `checkpoint/cycle7-reviewed`
**Build:** `mvn "-Dspring.profiles.active=test" test` → **BUILD SUCCESS, 134 tests, 0 failures, 0 errors.**
**Spring Boot 3.3.13.** Local commits only — **NO push**; no real WhatsApp/Brapi, no live polling, no
deploy, no asset activation, no sibling-repo change. 7 protected inputs byte-unchanged; no `.env`.
Docker absent → PostgreSQL IT **NOT_RUN**.

Full detail: `docs/handoff/CYCLE8-REVIEW-EVIDENCE.md`. Migration: `docs/handoff/SPRING-41-PROBE-RESULT.md`.

## 1. Authorized local Git checkpoint (done first)
Branch `checkpoint/cycle7-reviewed` off `6de333d`. Commits (no push):
- `41fa2605` — `checkpoint: cycle 7 reviewed baseline` (123 tests, reviewed green state).
- `0daada39` — `cycle 8: rule lifecycle + RuleMode + attempt ledger + calendar/numeric fixes` (134 tests).
`.gitignore` hardened to exclude `.env`, the review ZIP, raw logs, `.kiro/`, and the stray root ZIP;
none of those are committed. The 7 protected inputs hash identically to the baseline.

## 2–9. Functional fixes (all green, committed at `0daada3`)
- **A pause/resume:** pause cancels unsent PENDING (evidence preserved); resume sets a persisted
  `rebaseline_required` marker so the first post-resume observation re-baselines and cannot fire — no
  replay of a pre-pause episode, no CROSSING inferred across the gap.
- **B RuleMode:** typed `UNSELECTED`(default, fail-closed)/`CROSSING`/`LEVEL`(blocked, Q-19). Scheduler
  and guard fail closed on non-operable modes; `selectMode` bumps the revision; no auto-promotion.
- **C attempt ledger:** append-only `outbox_attempt` (V12); a row is created only when SENDING commits;
  requeue creates a NEW immutable row; `attempts` counts started external submissions, not claims.
- **D recovery split:** expired IN_FLIGHT → safe PENDING recovery (no send happened); expired SENDING →
  UNKNOWN_OUTCOME (ambiguous). New legal edge `IN_FLIGHT→PENDING`.
- **E bypass removal:** `tick(List)` package-private; production uses the single `RuleSource`.
- **F calendar:** numeric/mode migrations landed (V11); calendar scheduler-consolidation + dataset
  session windows remain OPEN (see §10).
- **G numeric:** threshold/hysteresis bounded to NUMERIC(19,6) (lossless round-trip); precision 0..6.

## 10. Spring 4.1 migration — BLOCKED, reverted
On the branch: parent→4.1.0, `spring-boot-starter-test-classic` (clears the cycle-7 `@DataJpaTest`
test-slice blocker), Jackson 2→3, `@MockitoBean`/`@MockitoSpyBean`. Next blocker: **Testcontainers 2.0
artifact coordinates** — `org.testcontainers:junit-jupiter:2.0.0` is absent from Central; the Boot-4
BOM-managed coordinates must be read from the BOM, not guessed. Reverted to `0daada3` (134 green on
3.3.13); nothing migration-related committed.

## Tests / migrations / IT
**134 tests pass** (was 123). Migrations **V11** (mode + rebaseline + precision CHECK) and **V12**
(outbox_attempt). `OutboxPostgresIT` extended to V1–V12 + the recovery split — **NOT_RUN** (Docker
absent); run `mvn verify -Pdocker-it` on a Docker host.

## 10. Remaining blockers & next actions
- **Docker absent** → V1–V12 + races NOT_RUN. H2 is not a PostgreSQL DDL/tx substitute.
- **Spring 4.1** blocked only on the Testcontainers 2.0 BOM coordinate (one BOM lookup away); the rest
  of the migration (test-classic starter + Jackson 3 + MockitoBean) is proven to apply.
- **Calendar** scheduler-consolidation onto `TradingSessionCalendar` + dataset-carried session windows
  (removing the fixture-only 10:00–17:00 from any production path) is the main open functional item.
- **No authenticated admin HTTP surface** yet (domain services only — deliberate; no insecure listener).
- Human/approval gates unchanged (OPERATIONAL_ACTIVATION, WAHA Q-09/Q-25, dedicated-Brapi, Q-19 LEVEL,
  Q-20, Q-14/27, Q-15, SNAG11). No real WAHA/Brapi/activation/deploy. **No push.**

### Exact next 1–3 actions
1. Finish the **Spring 4.1 migration** from `0daada3`: resolve the Testcontainers 2.0 coordinates from
   the Boot 4.1 BOM, `mvn test`, then (on a Docker host) `mvn verify -Pdocker-it`; commit as a separate
   local commit only if green, else revert and keep `0daada3`.
2. **Consolidate the calendar**: make the scheduler depend on `TradingSessionCalendar`, carry validated
   session windows (+ provenance) in the dataset, keep production UNKNOWN until a validated dataset is
   supplied; drop any production hardcoded hours.
3. On a Docker host, `mvn verify -Pdocker-it` (V1–V12 + pause/resume, mode, attempt-ledger immutability,
   IN_FLIGHT-recovery vs SENDING-unknown, two-consumer races). Record PASS/NOT_RUN honestly.
