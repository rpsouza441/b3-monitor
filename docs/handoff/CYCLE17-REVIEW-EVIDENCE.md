# Cycle 17 — Review Evidence

Adversarial hardening of the analytics import boundary + consumer/producer boundary proof.

`mvn test` → **258 passed, 0 failures, 0 errors** on **Spring Boot 4.1.1 / Java 21**, Flyway **V1–V14**
(was 231; +27 = 11 token + 16 hardening). PostgreSQL IT = **NOT_RUN** (Docker absent) — runtime
PESSIMISTIC_WRITE **NOT_PROVEN**. **No push. No REAL producer snapshot consumed** (synthetic only).

## Preflight (recorded)

- Branch `checkpoint/cycle7-reviewed`, HEAD at preflight `e404d39`, clean tree.
- `git branch -r` → only `origin/main`.
- Docker **not installed** → Postgres gate NOT_RUN.
- Baseline `mvn test` = 231 green. Migrations at **V14** (unchanged this cycle — no schema change needed).
- Seven protected inputs hashed; re-verified byte-unchanged at closure.
- **Cycle-16 claims verified against the repo** (not assumed): contract `b3-monitor.analytics-snapshot/1`,
  fail-closed validator, V14 tables, IMPORT_SNAPSHOT audit, preview→commit importer, UI-01/UI-02, producer
  doc, synthetic-only — all present. The one weakness confirmed: the cycle-16 "token" was the bare
  canonical checksum (content-bound only), which this cycle replaces.

## 1 — PostgreSQL runtime gate: NOT_RUN

Docker is not installed; per policy nothing was installed and no host infra changed. The ITs (incl. the new
concurrent-import test and the V14 analytics schema) compile and run under `mvn verify -Pdocker-it`.
Runtime PESSIMISTIC_WRITE and the concurrent-commit uniqueness remain written-not-proven.

## 2 — Preview→commit token: HMAC-bound, replay-safe

`AnalyticsPreviewToken` replaces the bare checksum with an **HMAC-SHA256** over
`purpose | schemaVersion | snapshotId | checksum | actor | issuedAt | ttl`, wire form
`base64url(claims).base64url(mac)`. Verification does a **constant-time** MAC compare FIRST, then re-binds
every claim to the live commit context. **Properties proven** (`AnalyticsPreviewTokenTest`, 11):
tampered → BAD_SIGNATURE/MALFORMED; actor A's token committed by B → ACTOR_MISMATCH; payload A → payload B
→ CONTENT_MISMATCH; snapshot A → B → SNAPSHOT_MISMATCH; schema A → B → SCHEMA_MISMATCH; expired (TTL 900 s)
→ EXPIRED; wrong purpose → WRONG_PURPOSE; malformed/null/one-part → MALFORMED fail-closed; constant-time
compare is length-safe; a <16-byte secret is refused at construction. **Replay policy:** reusable only
insofar as the commit is idempotent for the same snapshotId+checksum; a changed payload moves the checksum
so the MAC no longer binds. No single-use store added (no security gain over content+context binding + DB
uniqueness). **No secret, no raw payload in the token** — only the digest; token contents are never logged.

## 3 — Parser / input bounds (hardened before trust)

Strict parse via `StreamReadFeature.STRICT_DUPLICATE_DETECTION`: **duplicate JSON keys rejected**.
**Unknown top-level fields rejected** (strict v1 — allowed set is exactly the 10 envelope keys). JSON
**depth ≤ 12**. **Non-finite numbers (NaN/Infinity) rejected** (the literal tokens fail the strict parse;
a decoded non-finite double is caught defensively). Per-metric numeric **scale ≤ 12, precision ≤ 24,
magnitude ≤ 1e12**. **Duplicate tickers** in one snapshot rejected (no implicit merge — contract is
one-row-per-ticker). Document ≤ 512 KiB, ≤ 500 records, strings ≤ 200 chars (cycle-16, retained). Dangerous
string content (`<script`/`javascript:`/`://`/`file:`/`${`/`#{`/traversal/NUL) and private-portfolio keys
(FIN-04) rejected (cycle-16, retained). No archive, no remote URL, no filesystem-path import.

## 4 — Canonical checksum correctness (golden)

The checksum is SHA-256 over a canonical records encoding (ticker-sorted; fixed indicator order;
context-metrics name-sorted; numbers normalized via `stripTrailingZeros().toPlainString()`), independent of
JSON property order and insignificant whitespace, over UTF-8 bytes. The envelope's own `checksum` field is
**excluded** from the digest (the digest is over records only — no self-reference). Golden tests
(`AnalyticsImportHardeningTest`): reordered JSON keys → same checksum; whitespace-only change → same;
changed value → different; changed asOf → different; a duplicate-key payload is rejected, never ambiguously
canonicalized.

## 5 — Time / as-of invariants

`generatedAt` not in the future (≤ 5-min skew) → reject; `marketAsOf` not after today → reject; each record
`asOf ≤ marketAsOf` (marketAsOf is the upper bound) → reject; `importedAt` is always the **injected server
Clock**, never from the payload (asserted). Timestamps parse strictly (ISO-8601, UTC). No market-freshness
SLA threshold was invented.

## 6 — Atomicity / idempotency / concurrency

Validation precedes any durable mutation; snapshot + context rows commit in one `@Transactional` import; a
SUCCESS audit cannot be written if persistence rolls back (same transaction). Idempotent on same
snapshotId+checksum (NO_OP); conflict on same id/different checksum; the DB `uq_analytics_snapshot_id`
constraint enforces core uniqueness. **Concurrency IT** (`OutboxPostgresIT`, NOT_RUN): two threads commit
the same snapshotId behind a CyclicBarrier; exactly one durable row, the other hits the unique constraint
(DataIntegrityViolation), any other exception propagates — no duplicate rows, no swallowed error.

## 7 — Current analytics context selection

`analyticsContext()` selects the latest snapshot by **`marketAsOf` DESC, `importedAt` DESC**
(`findFirstByOrderByMarketAsOfDescImportedAtDesc`) — never by id/import order. Test: a newer-asOf snapshot
imported FIRST stays current after an older-asOf snapshot is imported SECOND. Equal asOf → deterministic
importedAt tie-break. A rejected/conflicted preview never persists, so it is never current.

## 8 — Synthetic never becomes runtime VERIFIED

A validated synthetic import persists status `CONSUMER_VERIFIED_SYNTHETIC`; the readiness `analytics_consumer`
component reports `runtimeStatus = NOT_VERIFIED` (wiring READY). Test asserts the runtime status is NEVER
`VERIFIED` after a synthetic import. Real provenance is not inferred from `producer = "projecao-carteira"`.

## 9 — Side-effect non-interference

Test proves an import creates/changes **no rule**, **no alert_outbox row**, **no quote observation** — and
by construction it calls no Brapi/WAHA, touches no trading calendar or quota, and never authorizes an asset
or enables a worker (there is no such code path in the importer).

## 10 — Admin / audit / history

Preview + commit are POST under `/api/admin/**` → **ADMIN-only**; history/analytics are GET → viewer-readable;
CSRF chain-enforced (ADMIN-without-CSRF → 403, tested). Audit detail is sanitized/bounded; the token is never
logged or stored; the checksum is shown only as a 12-char prefix in history.

## 11 — Producer acceptance checklist

`docs/contracts/ANALYTICS-SNAPSHOT-CONTRACT.md` now documents the 11-point future acceptance gate (sibling
authorization, exact schema v1, shared golden checksum test, exporter version, atomic delivery, real
artifact, imported via the ADMIN path, documented provenance, ticker reconciliation, no synthetic flag,
runtime promoted only after evidence). `projecao-carteira` was NOT touched.

## 12 — Requirements (unchanged this cycle)

UI-01 **PARTIAL**, UI-02 **PARTIAL** (no real producer snapshot exists — synthetic importer tests do not
make them DONE). SEC-01 **DONE**, UI-03 **DONE** (no regression). RUL-03/RUL-05 **PARTIAL** (Q-19).
PostgreSQL runtime gate NOT_RUN.

## 13 — Tests (258; +27)

`AnalyticsPreviewTokenTest` (11) + `AnalyticsImportHardeningTest` (16) added; `AnalyticsImportTest` (11) and
`AdminSecurityEnabledTest` migrated to the HMAC token; `OutboxPostgresIT` gained the concurrent-import test.

## 14 — Human gates (unchanged)

push · modify projecao-carteira/ticker-scraper · live Brapi · real WAHA · recipients · OPERATIONAL_ACTIVATION
· production DB/deploy · trading · LEVEL/false-confirmation/cooldown while Q-19 open. UNSELECTED fail-closed;
only explicit CROSSING operable; workers disabled.
