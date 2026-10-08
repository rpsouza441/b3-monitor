# B3 Monitor — Development Handoff (rev 18)

**MVP vertical:** validated B3 quote monitoring with explainable CROSSING price alerts, a durable
submission-authority outbox, a linearizable rule-lifecycle fence, an append-only admin audit ledger, a
fail-closed authenticated private admin surface + browser UI (status / readiness / assets / analytics /
imports / rules / outbox / alerts / audit), and a fail-closed analytics-snapshot CONSUMER with an
HMAC full-document-bound ADMIN preview→commit importer and lossless structured provenance. Offline/local
only — no live Brapi, no live WAHA, no activation, no deploy.

## Build & test

- **Spring Boot 4.1.1**, Java 21. `mvn test` → **272 passed, 0 failures, 0 errors**.
- **PostgreSQL IT NOT_RUN** (Docker absent); `OutboxPostgresIT` (V1–V15, V15 analytics lossless schema,
  concurrent same-snapshotId commit) + `LifecycleFencePostgresIT` (concurrency races; transaction-bound
  `pg_backend_pid()`/`pg_blocking_pids()` lock-wait proof) compile and run under `mvn verify -Pdocker-it`.
  Runtime PESSIMISTIC_WRITE: **NOT_PROVEN**.
- Migrations **V1–V15** (Flyway). V15 = document_digest + NUMERIC(24,12) indicators + structured
  analytics_context_metric child table.

## Git (branch `checkpoint/cycle7-reviewed`, NO push — `git branch -r` = only `origin/main`)

| SHA | What |
|---|---|
| `0035487`..`653674c` | cycle-12/13 (UI RBAC, session expiry, UI-01/UI-03, true concurrency races, docs) |
| `c9f0aac`..`06492c6` | cycle-14/15 (concurrency-test integrity, lock-proof identity, readiness dimensions, drift, docs) |
| `28c16a3`..`e404d39` | cycle-16 (analytics consumer contract v1 + V14 + preview→commit importer + UI-01/UI-02 + producer doc) |
| `2b0c275`..`23d0141` | cycle-17 (HMAC preview token, strict parser, time invariants, golden checksum, concurrency IT, docs) |
| `f782ea1` | **cycle-18: V15 lossless analytics + full-document token binding + document-identity idempotency** |
| `fe83b50` | **cycle-18: strict nested v1 schema + length-prefixed checksum (quality fields)** |
| `eaacb28` | **cycle-18: per-ticker current selection + remove invented stale SLA + HTTP body-size limit** |
| `45519c3` | **cycle-18: full-document/per-ticker/numeric/lossless/checksum tests** |
| (this) | **cycle-18 docs rev 18 + CYCLE18 evidence + archive CYCLE17** |

## Cycle-18 changes (detail: `CYCLE18-REVIEW-EVIDENCE.md`)

- **A full-document binding** — token binds records checksum AND full-document digest; envelope-only /
  whitespace change after preview rejected.
- **B document-identity idempotency** — same snapshotId+document ⇒ NO_OP; changed envelope/records ⇒ CONFLICT;
  `document_digest` persisted (V15; legacy V14 rows NULL).
- **C unambiguous token** — base64url-per-claim (pipe-safe); configured-but-short secret fails startup;
  TTL bounded min+max; overflow-safe expiry.
- **D per-ticker selection**; **E** no invented stale SLA; **F** NUMERIC(24,12) matches validator;
  **G** lossless structured context metrics (incl. quality) + dup-name rejection; **H** strict nested schema +
  status vocabulary + IANA tz; **I** length-prefixed checksum incl. quality; **J** HTTP body-size limit;
  **K** typed concurrent-commit UX (NO_OP/CONFLICT, never a generic 500).

## Git — earlier cycles

| SHA | What |
|---|---|
| `0035487`..`9b0c8d5` | cycle-12 (UI RBAC + session expiry, Postgres V13 IT prep, UI-01, UI-03 groundwork, docs) |
| `62f053f`..`653674c` | cycle-13 (UI-03 full lifecycle, true concurrency races, bounded session timeout, docs) |
| `c9f0aac`..`8d5eec2` | cycle-14 (concurrency-test integrity, UI-03 ordering, README/STATE drift, UI-02 readiness, docs) |
| `f07b8e7`..`06492c6` | cycle-15 (transaction-bound lock proof, no swallowed races, factual drift, readiness dimensions, docs) |
| `28c16a3`..`e404d39` | cycle-16 (analytics consumer contract v1 + validator + V14, preview→commit importer, UI-01/UI-02, producer doc, tests, docs) |
| `2b0c275` | **cycle-17: HMAC-bound preview→commit token (actor/schema/snapshot/content/purpose/expiry)** |
| `41abdfb` | **cycle-17: strict analytics parser + time invariants** |
| `0bf6300` | **cycle-17: adversarial hardening tests (token, golden checksum, bounds, time, selection, side-effects, concurrency)** |
| `f2c85f6` | **cycle-17: analytics contract — token/parser rules, context selection, producer acceptance checklist** |
| (this) | **cycle-17 docs rev 17 + CYCLE17 evidence + archive CYCLE16** |

## Cycle-17 changes (detail: `CYCLE17-REVIEW-EVIDENCE.md`)

- **HMAC preview token (item 2).** Replaced the bare-checksum token with an HMAC-SHA256 over
  `purpose|schemaVersion|snapshotId|checksum|actor|issuedAt|ttl`, constant-time verified and re-bound to the
  live commit; cannot be replayed across actor/content/snapshot/schema or after expiry; tamper/wrong-purpose/
  malformed reject fail-closed; no secret or raw payload in the token.
- **Strict parser + time invariants (items 3/5).** STRICT_DUPLICATE_DETECTION; unknown top-level fields
  rejected (strict v1); depth ≤ 12; non-finite rejected; numeric scale/precision/magnitude bounds; duplicate
  tickers rejected; generatedAt-not-future; per-record asOf ≤ marketAsOf; importedAt from the injected clock.
- **Canonical checksum golden (item 4).** reorder/whitespace → same; value/asOf → different; dup-key rejected.
- **Atomicity/concurrency (item 6).** Concurrent same-snapshotId commit IT → exactly one durable (unique
  constraint), no duplicate rows, no swallowed error. NOT_RUN (Docker absent).
- **Current-context selection (item 7).** By `marketAsOf` DESC, `importedAt` tie-break — older-asOf imported
  later never becomes current.
- **Synthetic ≠ VERIFIED (item 8)** and **side-effect non-interference (item 9)** proven by test.
- **Producer acceptance checklist (item 11)** documented; `projecao-carteira` untouched; no real snapshot consumed.

## Cycle-16 changes (detail: `archive/cycles/CYCLE16-REVIEW-EVIDENCE.md`)

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

## Requirement status (acceptance-literal, current)

- **SEC-01 → DONE** (loopback-only admin surface + proven real session expiry).
- **UI-03 → DONE** for the current outbox contract (full lifecycle, logical/transport/attempt separated).
- **UI-01 → PARTIAL** (quote freshness real; analytics context now inspectable + separate, but real
  daily-indicator/Python values require a producer that does not exist yet — NOT_INTEGRATED).
- **UI-02 → PARTIAL** (rules audited + readiness/import visibility; no real producer snapshot consumed —
  the analytics consumer is CONSUMER_VERIFIED_SYNTHETIC).
- **RUL-03/RUL-05 → PARTIAL** (Q-19 unresolved; LEVEL/false-confirmation/cooldown fail-closed).
- **PostgreSQL runtime gate → NOT_RUN** (Docker absent); ITs compile.

## Running the admin surface + UI locally

Disabled by default. Enable on loopback: `B3MONITOR_ADMIN_ENABLED=true`, `B3MONITOR_ADMIN_USERNAME=<user>`,
`B3MONITOR_ADMIN_PASSWORD_HASH=<bcrypt>`, optional `B3MONITOR_ADMIN_SESSION_TIMEOUT_SECONDS` (default 1800,
coerced to [1..86400]), optional `B3MONITOR_ADMIN_ANALYTICS_TOKEN_SECRET` (>=16 chars; a configured-but-short
secret fails startup, otherwise a per-process random secret is used), `B3MONITOR_BIND_ADDRESS=127.0.0.1`
(loopback enforced). Non-loopback or missing creds ⇒ startup fails. UI at `/admin/login`; JSON API under
`/api/admin/**`. Analytics import: `/admin/imports` (ADMIN preview→commit) + `/admin/analytics` (per-ticker
context). Importing authorizes nothing, enables no worker, triggers no Brapi/WAHA, changes no rule.

## Open items / recommended next 1–3 actions

1. **PostgreSQL IT on a Docker host** (`mvn verify -Pdocker-it`): V1–V15 validate (incl. the V15 lossless
   context schema + NUMERIC(24,12)), the concurrent same-snapshotId commit, and the transaction-bound
   PESSIMISTIC_WRITE races — the remaining runtime proofs.
2. **Producer acceptance gate** (`docs/contracts/ANALYTICS-SNAPSHOT-CONTRACT.md`): obtain sibling-change
   authorization for `projecao-carteira`, then a shared canonical-checksum golden test, to move the consumer
   past CONSUMER_VERIFIED_SYNTHETIC. Not started; human-gated.
3. **RUL-03/RUL-05 + LEVEL (Q-19)** remain human-gated.

## Still prohibited

push · modify projecao-carteira/ticker-scraper · live Brapi · live WAHA · recipients · OPERATIONAL_ACTIVATION
· deploy · production DB · trading · LEVEL/false-confirmation/cooldown while Q-19 is open. Seven protected
inputs byte-identical; phase closure 0/8; all 23 assets NOT_AUTHORIZED.

## Handoff packaging — SINGLE ATTACHMENT (count derived from the FINAL ZIP)

Active docs: `docs/handoff/DEVELOPMENT-HANDOFF.md`, `docs/handoff/CYCLE18-REVIEW-EVIDENCE.md`.
Archive (in the ZIP): `docs/handoff/archive/cycles/` (CYCLE6–CYCLE17). Generated/gitignored:
`b3-monitor-review.zip`, `test-evidence-cycle18.log`. The ZIP's actual entry count + SHA-256 are read back
FROM the built ZIP and reported in the closure message. `.env.example` is a template, NOT a real `.env`.

**Para revisão no ChatGPT, anexe apenas `docs/handoff/b3-monitor-review.zip`; não anexe os arquivos do archive
nem os handoffs individualmente, salvo se o revisor pedir.**
