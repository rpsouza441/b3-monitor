# B3 Monitor — Cycle 12 Review Evidence

Date: 2026-10-07
Branch: `checkpoint/cycle7-reviewed`
Start HEAD: `3430308` (cycle-11 docs)
Local commits this cycle (no push):
- `0035487` — fix: enforce admin ui rbac + prove real session expiry (B/C)
- `743e76c` — test: complete postgres lifecycle + V13 release gate (D)
- `2c5ee53` — feat: asset freshness admin view UI-01 (E)
- `cd912ca` — feat: alert outcome lineage admin view UI-03 + PRG feedback (F/G)
- (docs commit) — handoff rev 12, this evidence, archive CYCLE11

`mvn test` → **205 passed, 0 failures, 0 errors** on **Spring Boot 4.1.1** (was 197).
PostgreSQL IT: **NOT_RUN** (Docker absent) — both IT classes compile. Seven protected inputs byte-unchanged.
The ZIP entry count + SHA-256 are DERIVED FROM THE FINAL ZIP and reported in the closure message (item J).

---

## B — P0: UI RBAC gap closed

**Defect.** The security chain gated `/admin/**` as merely `authenticated()`, but `AdminUiController` has POST
mutation routes there — so an authenticated VIEWER could mutate through the browser UI even though the JSON
API correctly returned 403. `/admin/audit` was also only `authenticated()` while the JSON audit endpoint was
ADMIN-only.

**Fix (in `AdminSecurityConfig`, not in `RuleAdminService` — the command layer stays role-free and reusable):**
```
GET  /admin/audit        → hasRole('ADMIN')
POST /admin/**           → hasRole('ADMIN')
GET  /admin/**           → authenticated   (read-only pages, viewer-readable)
GET  /api/admin/audit    → hasRole('ADMIN')
GET  /api/admin/**       → authenticated
      /api/admin/**      → hasRole('ADMIN')  (mutations)
anyRequest()             → denyAll
```

**Regressions (`AdminSecurityEnabledTest`):** VIEWER GET /admin/rules|/assets|/alerts → 200;
VIEWER POST pause/resume/disable/mode/create (valid CSRF) → 403 with state unchanged; VIEWER GET /admin/audit
→ 403; ADMIN pause (CSRF) → PRG redirect + state changed; ADMIN mutation without CSRF → 403; anonymous UI
mutation → denied.

## C — P0 / SEC-01: real session expiry proven

**Added.** A bounded, configurable browser-session inactivity timeout
(`b3monitor.admin.session-timeout-seconds`, safe 30-min default, never infinite), surfaced in the status
view. Because the container's own `server.servlet.session.timeout` truncates to whole minutes, the exact
per-second policy is enforced by an enabled-only `HttpSessionListener` that sets
`maxInactiveInterval` on session creation.

**Proof (`SessionExpiryTest`, `@SpringBootTest(RANDOM_PORT)`, 1-second test timeout, JDK HttpClient, real
cookie):** form login succeeds and issues a cookie; the cookie authenticates `/api/admin/status` without
Basic; after `Thread.sleep(2500)` (BEYOND the 1s inactivity timeout) the SAME cookie returns **401** and a
mutation returns 401/403. This is genuine inactivity EXPIRY — not a logout/invalidate substitute. HTTP Basic
is distinguished: it re-authenticates whenever credentials are resent, so expiry applies to browser sessions.

## D — P1: OutboxPostgresIT completed for the V13 contract

- Doc corrected to **V1–V13**; `ddl-auto=validate` proves V1–V13 match the entities.
- Added **V13 audit persistence** test (`admin_audit_event` round-trips) and a **latest-quote V13 index/entity**
  test (bounded `findFirst…` returns the newest-by-receipt row).
- New **`LifecycleFencePostgresIT`** exercises the REAL fence with the REAL
  `FailClosedDispatchEligibilityGuard` + `PersistentRuleRegistry` + `RuleAdminService` (WEGE3 authorized):
  pause-wins-before-prepareSend → 0 adapter calls; prepareSend-wins → SENDING authority preserved;
  stale-revision → no send authority; pause↔process serialized → no stale outbox. The allow-all guard remains
  ONLY in `OutboxPostgresIT` (transport-only), clearly isolated.
- No DB transaction spans adapter I/O. **NOT_RUN** (Docker absent); both ITs compile.

## E — UI-01: asset / freshness view

New trusted in-code `AssetCatalog` (the 23 canonical tickers mirrored from the protected ASSET-CATALOG; asset
class is NEVER inferred from a suffix). `/admin/assets` (+ `GET /api/admin/assets`) shows one bounded row per
catalog asset: ticker, authorization status, latest price, source/receipt time, computed age, provider
contract, remapped, currency, eligible, stale, rejection reason. No observation ⇒ an explicit UNKNOWN row
(23 single-row lookups, no N+1 over history). Daily indicators and Python context are reported as explicit
**NOT_INTEGRATED** — never synthesized. (Honest status: UI-01 is PARTIAL — asset/freshness is real; the daily
indicators + Python-context columns are explicit readiness markers, so UI-01 acceptance is not fully met.)

## F — UI-03: logical alerts vs channel outcomes

New `/admin/alerts` (+ `GET /api/admin/alerts`, bounded) separates: the LOGICAL alert (logical_key, rule_id,
ticker, revision, episode, source/intent timestamps); the TRANSPORT state (outbox state, delivery_confirmed,
provider_message_id, suppression/cancel/failure reason, UNKNOWN_OUTCOME flagged uncertain); and the ATTEMPT
lineage (claim_generation, fencing_token, started/finished, outcome, sanitized_status, provider accepted
id/time) — bounded child attempts. The page states explicitly: ACCEPTED ≠ delivery; UNKNOWN_OUTCOME ≠
delivered; FAILED/dead-letter ≠ delivered. Read-only; no live-send/retry; no WAHA credential/payload.

## G — UI form error handling (PRG)

All browser mutation handlers now use Post/Redirect/Get with `RedirectAttributes` flash: a validation error or
a stale-expectedRevision conflict is shown sanitized AFTER the redirect (bounded message, no stack trace /
secret / payload), with no mutation on invalid input and no false SUCCESS. The stale-edit conflict renders a
clear "reload and retry" message with the expected vs current revision.

## H — requirement traceability (acceptance-literal)

- **SEC-01** → now satisfied: auth required, non-admin mutation denied (API + UI), CSRF enforced, and REAL
  session expiry tested with synthetic identities. Marked DONE only because the expiry test passes.
- **UI-02** → PARTIAL: validated changes DO create audit revisions and pause stops subsequent collection;
  but UI-02 covers catalog/policies/imports beyond rules, so not fully met.
- **UI-01** → PARTIAL: asset/freshness real; daily-indicator + Python-context are explicit NOT_INTEGRATED.
- **UI-03** → the logical/transport/attempt separation is delivered and read-only; dead-letter/uncertain
  outcomes are inspectable. Substantially met for the current outbox contract.
- **RUL-03/RUL-05** remain PARTIAL while Q-19 is unresolved (LEVEL/false-confirmation/cooldown fail-closed).

## I — hygiene

Removed the explicit `H2Dialect` from the test profile (Hibernate auto-detects it; no deprecation warning).
The Mockito dynamic-agent warning is left documented (no clean JDK-21/Boot-4 agent path without risking the
suite); the Testcontainers API in the ITs is current (2.0.5 artifacts).

## Boundaries honoured

No push; no live Brapi; no live WAHA; no OPERATIONAL_ACTIVATION; no deploy; no production DB; no sibling-repo
change; no non-synthetic credentials; no trading. Workers disabled; all assets NOT_AUTHORIZED. Q-19 fail-closed.
Seven protected inputs byte-identical to the preflight baseline.
