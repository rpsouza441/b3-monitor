# B3 Monitor — Cycle 11 Review Evidence

Date: 2026-10-07
Branch: `checkpoint/cycle7-reviewed`
Start HEAD: `bc7460f` (cycle-10 docs)
Local commits this cycle (no push):
- `d0d5e85` — fix: harden admin security defaults and sessions (A/B/C/D)
- `d657a5a` — fix: prove dispatch lifecycle fence + bound admin reads (E/F)
- `466621e` — feat: admin mutation audit ledger V13 (G)
- `b5724d7` — feat: thin private browser admin UI (H)
- (docs commit) — handoff rev 11, this evidence, archive CYCLE10

`mvn test` → **197 passed, 0 failures, 0 errors** on **Spring Boot 4.1.1** (was 173).
PostgreSQL IT: **NOT_RUN** (Docker absent). Seven protected inputs byte-unchanged.

---

## A — P0-security: no Boot-generated password/user fallback

**Defect (confirmed in the cycle-10 log).** With `b3monitor.admin.enabled=false`, Boot's
`UserDetailsServiceAutoConfiguration` ran and logged `Using generated security password: …` 3×, contradicting
the handoff's "no Boot-generated-password fallback".

**Fix.** `UserDetailsServiceAutoConfiguration` is EXCLUDED on `B3MonitorApplication`. `AdminSecurityConfig`
supplies the `UserDetailsService` deliberately: an EMPTY `InMemoryUserDetailsManager` when disabled, the
env-backed single ADMIN when enabled (blank username/hash while enabled ⇒ context fails to start).

**Acceptance proven:** a fresh `AdminSecurityEnabledTest` run shows **0** `Using generated security password`
lines; `AdminSurfaceDisabledTest.noGeneratedOrDefaultUserWhenDisabled` asserts loading `user` throws
`UsernameNotFoundException`; `AdminSecurityContextTest` asserts disabled → `noAdminUsers` bean present,
`adminUserDetailsService` absent; enabled-without-creds ⇒ context fails. Docs now say "no REAL credential
committed" (a synthetic test password literal legitimately exists in the test).

## B — P0-before-UI: global deny-by-default

**Defect.** Both chains used `securityMatcher("/api/admin/**")`; any future route outside that matcher would
be unprotected.

**Fix.** Both chains now cover ALL requests (no `securityMatcher`) and end in `anyRequest().denyAll()`.
Disabled → deny all, no login. Enabled → `/admin/login`+`/admin/logout` permitted; `/admin/**` authenticated;
`/api/admin/**` GET authenticated + mutations ADMIN; **everything else denied**.

**Regression:** `AdminSurfaceDisabledTest.unapprovedRouteIsDeniedNotPublic` — `GET /unapproved-surface` is
denied (401/403), never public, proving no future controller leaks by a missing matcher.

## C — P0-security: loopback invariant (not just a default)

**Defect.** `server.address` defaulted to `127.0.0.1` but a runtime `0.0.0.0`/LAN override could expose the
admin surface remotely with no approval.

**Fix.** When `admin.enabled=true`, `LoopbackBindValidator` fails context startup unless the configured bind
address is a loopback literal (`127.0.0.0/8`, `::1`, `localhost`); wildcard (`0.0.0.0`, `::`) and LAN/WAN
literals are refused. No remote-admin escape hatch.

**Tests:** `LoopbackBindValidatorTest` (loopback/subnet/localhost pass; `0.0.0.0`/`::`/`192.168.*`/`10.*`/blank
fail) + `AdminSecurityContextTest` (enabled+wildcard ⇒ context fails, enabled+LAN ⇒ fails, enabled+loopback ⇒
starts). `admin=false` keeps a conservative loopback default.

## D — P1-high: real browser session semantics

**Fix.** The enabled chain serves a real `formLogin` (`/admin/login` processing URL inside the active chain),
`logout` (invalidates the session + clears `JSESSIONID`), and a session policy. CSRF is enforced on every
mutation (never disabled).

**Proof (`AdminSecurityEnabledTest`):** `browserFormLoginThenSessionReuseThenLogout` — form login
authenticates with the synthetic credential (real BCrypt), a later GET reuses the session WITHOUT HTTP Basic,
logout invalidates it and the invalidated session returns 401; `browserFormLoginWithWrongPasswordFails` →
unauthenticated. HTTP Basic is retained only for the local JSON API (it resubmits credentials each request, so
session-expiry applies to browser sessions, documented).

## E — P1: lifecycle fence proof at `prepareSend`

Added integrated latch regressions against the REAL `RuleAdminService` + `OutboxTxOps` + a call-recording
adapter (`RuleLifecycleFenceTest`):
- **E1 pause wins** → `prepareSend` returns `DENIED_TERMINAL`, **0 adapter calls**, no SENDING minted;
- **E2 prepareSend wins** → SENDING authority commits; a later pause does NOT rewrite it as unsent; the normal
  typed `record(ACCEPTED)` then applies;
- **E3 edit before prepareSend** → a stale-revision intent cannot obtain send authority (0 adapter calls).
No DB transaction spans adapter I/O.

## F — P1: bounded admin reads

- Latest quote now uses `findFirstByRequestedTickerOrderByReceiptTimeDesc` (single row; no full-list load);
  index `ix_qobs_ticker_receipt (requested_ticker, receipt_time)` added in V13.
- Reconciliation uses a bounded `Pageable` with a hard cap (≤ 200); the API `size` param cannot exceed it.
- The audit read is likewise hard-capped.

## G — P1: append-only admin audit ledger (V13)

New `admin_audit_event` table (append-only; insert-only in code). Every admin mutation writes one row via
`AdminAuditService` from `RuleAdminService`: actor (authenticated principal), action
(CREATE_RULE/EDIT_RULE/SELECT_MODE/PAUSE/RESUME/DISABLE), rule_id, before/after revision, outcome
(SUCCESS/NO_OP/REJECTED_CONFLICT/…), bounded sanitized detail. It stores NO secret (no password/hash, auth
header, CSRF token, Brapi/WAHA credential, or arbitrary payload). A stale-edit conflict is audited in a
separate transaction. The audit lives in the command layer so a future authenticated WhatsApp-command service
reuses it. A bounded `GET /api/admin/audit` is **ADMIN-only** (VIEWER forbidden).

**Tests:** `successfulMutationCreatesOneAuditEvent` (one event, no credential in content),
`auditEndpointIsAdminOnly`, and the idempotent NO_OP semantics are recorded consistently.

## H — thin private browser UI (after A–G green)

Server-rendered Thymeleaf under `/admin/**` (enabled-only): login, status, rules (list + create[UNSELECTED] +
select-CROSSING + pause/resume/disable + edit[expectedRevision]), outbox metrics/reconciliation, audit page.
Every mutation is a CSRF-protected POST form delegating to the SAME `RuleAdminService`/`AdminQueryService` as
the JSON API — no duplicated business logic. No secret in HTML, no asset-activation / WAHA / Brapi / live-send
/ trading control. HTML is auto-escaped by Thymeleaf.

**Tests:** `uiLoginPageIsReachableAndRulesPageRequiresAuth` (login page 200; unauthenticated `/admin/rules`
denied; authenticated renders HTML); `AdminSurfaceDisabledTest` asserts both `AdminController` and
`AdminUiController` beans are absent when disabled. UI-02 is NOT claimed complete from this groundwork.

## I — hygiene

Non-blocking warnings (Mockito dynamic-agent self-attach, H2 dialect, a deprecated Testcontainers usage in the
NOT_RUN IT) are left documented rather than chased, to keep the security/fence work focused.

## J — PostgreSQL IT — NOT_RUN

Docker absent. The fence, retry semantics, admin surface and audit are proven on H2. The real gate (PESSIMISTIC
row-lock ordering, the pause↔process and pause↔prepareSend races, V1–V13 DDL incl. the audit table, Boot 4.1.1
+ Testcontainers 2.0.5 startup) remains for `mvn verify -Pdocker-it`. No PASS claimed.

## Boundaries honoured

No push; no live Brapi; no live WAHA; no OPERATIONAL_ACTIVATION; no deploy; no production DB; no sibling-repo
change; no non-synthetic credentials; no trading. Workers disabled by default; all assets NOT_AUTHORIZED. Q-19
(LEVEL/false-confirmation/cooldown) remains human-gated and fail-closed. Seven protected inputs byte-identical.
