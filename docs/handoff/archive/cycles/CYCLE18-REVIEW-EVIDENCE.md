# Cycle 18 — Review Evidence

Full-document import binding · per-ticker analytics selection · lossless persistence · strict nested schema.

`mvn test` → **272 passed, 0 failures, 0 errors** on **Spring Boot 4.1.1 / Java 21**, Flyway **V1–V15**
(was 258; +14). PostgreSQL IT = **NOT_RUN** (Docker absent). **No push. No REAL producer snapshot consumed.**

## Preflight

Branch `checkpoint/cycle7-reviewed`, HEAD `23d0141`, clean; only `origin/main`; Docker absent; migrations at
V14 → new **V15**; seven protected hashes verified unchanged; baseline 258 green. Cycle-17 claims verified
against the repo before extending.

## A — Full-document preview→commit binding

The token bound records-checksum only, so an envelope-only change passed. Fixed: `documentDigest =
SHA-256(exact raw bytes)`; the token now binds BOTH `recordsChecksum` AND `documentDigest`, and commit
recomputes both. **Tests:** envelope-only change after preview → `REJECTED_TOKEN_DOCUMENT_MISMATCH`;
whitespace-only change → rejected (exact-byte binding, documented). The token unit test adds
`tokenForDocumentACannotCommitDocumentB`. Whitespace-only changes DO invalidate a preview token.

## B — Idempotency on document/provenance identity

Same snapshotId + same `document_digest` ⇒ `IDEMPOTENT_NOOP`; same snapshotId + changed envelope (same
records) ⇒ `REJECTED_CONFLICT`; changed records ⇒ `REJECTED_CONFLICT`. `document_digest` persisted (V15).
Legacy strategy: V14 rows never stored raw bytes → `document_digest` is NULLABLE and legacy rows stay NULL
(an honest unknown); for a NULL-digest row, idempotency falls back to the records checksum. No fabricated
digest for historical rows.

## C — Unambiguous token claims

Claims are base64url-per-field joined by `|` (a `|` inside snapshotId/actor can no longer break parsing —
`pipeInClaimDoesNotConfuseParser`). A configured-but-shorter-than-16-char secret FAILS STARTUP (never a
silent random fallback). TTL bounded min 60 s / max 24 h. Expiry uses `Math.multiplyExact`/`addExact` —
overflow → rejected, never a wrap to a valid window.

## D — Per-ticker current selection

`analyticsContext()` now selects, FOR EACH ticker, the latest valid context across ALL committed snapshots
(`snapshot.marketAsOf DESC, snapshot.importedAt DESC, snapshot.id DESC, context.id DESC`). **Test
`perTickerPartialSnapshotKeepsEachTickersLatestValidContext`:** an older snapshot has BPAC11+WEGE3; a newer
PARTIAL snapshot has WEGE3 only → WEGE3 uses the newer data, BPAC11 keeps its older valid data. Deterministic
same-asOf tie-break by importedAt/id.

## E — Removed the invented 2-day stale SLA

The hardcoded `2*24h ⇒ stale` is gone. Age is still shown; `stalePolicy = POLICY_NOT_CONFIGURED` (no approved
threshold). `analyticsHasNoInventedStaleSla` asserts it.

## F — Numeric storage matches the validator

V15 widens indicator columns to **NUMERIC(24,12)** to match the validator (scale ≤ 12, precision ≤ 24).
**Tests:** a scale-12 value round-trips exactly; a scale-13 value is rejected by the validator BEFORE the DB
(no silent rounding).

## G — Lossless FIN-02 context

The compact string that could truncate at ~1800/2000 chars is replaced by a STRUCTURED child table
`analytics_context_metric (name, metric_value, units, readiness, quality)` with `UNIQUE(context, name)`.
Every validated field round-trips (**`contextMetricRoundTripsAllFieldsLosslessly`** incl. quality, which
cycle-16 dropped); duplicate metric names rejected. Nothing is truncated silently.

## H — Strict v1 at every level

Exact allowed-keys enforced for record (`ticker,asOf,indicators,context,quality,status`), indicators (the 12
`<name>`/`<name>Readiness` keys) and context metric (`name,value,units,readiness,quality`). Duplicate context
metric name → reject; nonblank name required; record status ∈ {OK,PARTIAL,UNKNOWN}; timezone a valid IANA
`ZoneId` or null.

## I — Canonical checksum fidelity

Rewritten as a **length-prefixed** (`<utf8-len>:<value>`) encoding — a delimiter-like string cannot collide
with the framing — and now includes **record.quality** and **context-metric quality** (both omitted before).
**Tests:** record-quality change → checksum changes; context-quality change → checksum changes; the cycle-17
order/whitespace/value/asOf golden tests still hold. This is a v1 checksum-contract fix made BEFORE a real
producer exists; the producer-boundary doc + golden tests are updated together.

## J — Request size before full materialization

`AnalyticsImportSizeLimitFilter` (admin-enabled, highest precedence, scoped to `/api/admin/imports/*` +
`/admin/imports/*`): a declared Content-Length over `MAX_FILE_BYTES` is rejected 413 before a byte is read;
an unknown-length/chunked body is wrapped in a bounded input stream that aborts past the cap. The payload is
never echoed (short status text only). Exactly-at-limit is accepted; one over is rejected.

## K — Concurrent commit user semantics

On a concurrent same-snapshotId commit, the loser's `DataIntegrityViolationException` is caught, the durable
row is RE-READ, and the full document identity is compared → the user sees `IDEMPOTENT_NOOP` (same document)
or `REJECTED_CONFLICT` (different), never a generic 500. Only this specific snapshot-id race is translated;
an arbitrary integrity error is re-thrown, never converted to NO_OP. (IT, NOT_RUN — Docker absent.)

## L — PostgreSQL: NOT_RUN

Docker not installed. `OutboxPostgresIT` (`ddl-auto=validate`, V1–V15 incl. V15 lossless schema + concurrent
same-snapshotId commit) + `LifecycleFencePostgresIT` compile; run under `mvn verify -Pdocker-it`.

## M — Recoverability

The DEVELOPMENT-HANDOFF bottom (stale V1–V13 / Cycle-13/14 / UI-01 wording) was replaced with current
cycle-18 status/running/next/packaging. Historical archives untouched. Seven protected baseline files
untouched.

## Requirement status

UI-01 **PARTIAL** (analytics context inspectable + separate + lossless, but no real producer values —
NOT_INTEGRATED). UI-02 **PARTIAL** (no real snapshot — CONSUMER_VERIFIED_SYNTHETIC). SEC-01 DONE, UI-03 DONE
(no regression). RUL-03/RUL-05 PARTIAL (Q-19). PostgreSQL runtime gate NOT_RUN.

## Tests (272; +14)

`AnalyticsPreviewTokenTest` 13 (adds document-mismatch + pipe-in-claim); `AnalyticsImportHardeningTest` 28
(adds envelope/whitespace binding, document-identity NO_OP/CONFLICT, numeric round-trip, lossless context,
dup-metric, checksum fidelity, per-ticker partial selection, no-SLA); `AnalyticsImportTest` 11 (token-based);
`OutboxPostgresIT` V15 schema + concurrent-commit (NOT_RUN).

## Human gates (unchanged)

push · modify projecao-carteira/ticker-scraper · live Brapi · real WAHA · recipients · OPERATIONAL_ACTIVATION
· production DB/deploy · trading · LEVEL/false-confirmation/cooldown while Q-19 open. UNSELECTED fail-closed;
only explicit CROSSING operable; workers disabled; all 23 assets NOT_AUTHORIZED; phase closure 0/8.
