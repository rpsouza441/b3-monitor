# Cycle 19 — Review Evidence

**Scope:** correct and independently validate three P1 findings and one P2 finding from the Cycle-18 external
review of the analytics-snapshot *consumer* boundary, before any real-producer integration. No product
broadening. No push. Docker absent → PostgreSQL IT **NOT_RUN**.

- **Branch:** `checkpoint/cycle7-reviewed`
- **Start HEAD:** `15cb497` → **final HEAD:** `dab1767` (3 commits; **51 ahead** of `origin/main`, 0 pushed)
- **Spring Boot** 4.1.1 / **Java** 21 / **Flyway** V1–V15 (no schema change this cycle)
- **`mvn test`:** **293 passed, 0 failures/errors/skipped** (was 272) — see `test-evidence-cycle19.log`
- **PostgreSQL IT:** **NOT_RUN** (Docker CLI not installed; nothing installed, host untouched)

## Verified-vs-unexecuted separation

| Proven on H2 (executed) | NOT executed (Docker absent) |
|---|---|
| Full `mvn test` = 293 green | `mvn verify -Pdocker-it` (all ITs) |
| P1-B real-endpoint 413 (RANDOM_PORT Tomcat) | `OutboxPostgresIT.concurrentServiceCommitSameDocumentYieldsImportedAndNoop` |
| P1-C numeric fit + exact round-trip (H2) | `OutboxPostgresIT.maxNumericRoundTripsExactlyOnRealPostgres` |
| P2 legacy 409 at endpoint + service disposition | V15 JPA DDL `validate` on real Postgres |
| P1-A loser dispositions (service-level, H2) | PESSIMISTIC_WRITE lifecycle races on real Postgres |

The two new PostgreSQL ITs **compile** and are wired; they run only where Docker is available. Runtime
PESSIMISTIC_WRITE remains **NOT_PROVEN**.

## Protected hashes — identical before and after (SHA-256 prefix)

| File | Before | After |
|---|---|---|
| `.planning/PROJECT.md` | `536C21E8…` | `536C21E8…` |
| `.planning/REQUIREMENTS.md` | `B18E8A92…` | `B18E8A92…` |
| `.planning/ROADMAP.md` | `7798704B…` | `7798704B…` |
| `docs/contracts/CONTRACTS.md` | `15378F08…` | `15378F08…` |
| `docs/SECURITY.md` | `9D698EDB…` | `9D698EDB…` |
| `docs/architecture/ARCHITECTURE.md` | `88E6412C…` | `88E6412C…` |
| `docs/architecture/ADRS.md` | `81E946B7…` | `81E946B7…` |

## P1-A — PostgreSQL concurrent snapshot import (transaction soundness)

**Defect (confirmed):** `commit()` was a single `@Transactional`; after `saveAndFlush` throws
`DataIntegrityViolationException`, PostgreSQL marks the whole transaction rollback-only, so the in-tx
catch-and-requery could never read the winner's row ("current transaction is aborted…").

**Fix (`AnalyticsImportService`):** `commit()` is **no longer** `@Transactional`. It orchestrates explicit
boundaries:
1. **Pre-check** — `readTx` (`REQUIRES_NEW`, read-only): if the snapshot already exists, dispose by full
   document identity (the common idempotent/conflict path, no insert attempted).
2. **Insert** — `writeTx` (`REQUIRES_NEW`): `saveAndFlush` + the SUCCESS audit (`AdminAuditService.record`
   joins this tx) commit/roll back **together**. A unique-violation aborts only this inner tx.
3. **Post-race re-read** — fresh `readTx` (`REQUIRES_NEW`) on a clean connection: dispose by document
   identity → `IDEMPOTENT_NOOP` / `REJECTED_CONFLICT`. A `DataIntegrityViolationException` that is **not** the
   snapshot-id race (row still absent) is **re-thrown**, never translated into idempotency.

**Guarantees:** no generic 500 for a legitimate duplicate race; no SUCCESS audit without a durable row; no
partial persistence; exactly one durable row.

**Tests:** `AnalyticsImportHardeningTest.loserOfSameDocumentRaceGetsIdempotentNoop` /
`loserOfDifferentDocumentRaceGetsConflict` (H2, deterministic). Real concurrency:
`OutboxPostgresIT.concurrentServiceCommitSameDocumentYieldsImportedAndNoop` (**NOT_RUN**).

## P1-B — request-size enforcement at the real endpoint

**Audit:** `AnalyticsImportSizeLimitFilter` is a `FilterRegistrationBean` at `HIGHEST_PRECEDENCE` scoped to
`/api/admin/imports/*` and `/admin/imports/*`, so a declared-oversize body is rejected **before** Spring
Security and before `@RequestBody byte[]` materialization. The chunked path wraps the request in a bounded
stream that throws `BodyTooLargeException` (→ 413) past `MAX_FILE_BYTES`.

**Tests:**
- `AnalyticsImportSizeLimitEndpointTest` (`@SpringBootTest(RANDOM_PORT)` + JDK `HttpClient`, real Tomcat):
  declared Content-Length over cap → **413 before materialization, no payload echo**; chunked oversize →
  **deterministic reject** (401 token-less, or 413 if authenticated — never 2xx, never materialized/imported,
  honest about CSRF-before-Basic ordering); exactly 512 KiB → **not 413** (transport accepts); one byte over
  → **413**; oversize browser form POST → **413**, no echo.
- `AnalyticsImportSizeLimitFilterTest` (mechanism): declared oversize → 413 with the downstream chain
  **never invoked**; chunked oversize drained through the wrapper → `BodyTooLargeException` past MAX;
  exactly-at-limit chunked drains fully; non-import path passes through.

## P1-C — numeric boundary vs `NUMERIC(24,12)`

**Defect (confirmed):** the validator accepted `abs ≤ 1E12` with `scale ≤ 12 / precision ≤ 24`. `1E12`
(BigDecimal `scale=-12`, `precision=1`, **13 integer digits**) slipped through all three checks but **cannot**
be stored in `NUMERIC(24,12)` → overflow on PostgreSQL. Separately, Spring's default Jackson parsed JSON
floats as `double`, silently rounding `999999999999.999999999999` up to `1E12`.

**Fix:** a value must round-trip exactly into `NUMERIC(24,12)` — `scale ∈ [0,12]` **and** integer digits
`(precision − scale) ≤ 12`. `1E12` and `−1E12` (decimal or scientific) and scale-13 are rejected before
persistence. The reader now uses `USE_BIG_DECIMAL_FOR_FLOATS` so full-precision decimals keep every digit.
`NUMERIC_PRECISION/SCALE/INT_DIGITS` are single-sourced on the contract and shared with the DB column. The
same `dec()` check covers typed indicators **and** structured context-metric values.

**Tests:** `maxValidNumericRoundTripsExactly` / `maxValidNegativeNumericRoundTripsExactly` (exact, no
rounding), `positive1E12Rejected`, `scientific1E12NotationRejected`, `negative1E12Rejected`,
`scaleBeyond12Rejected`, `contextMetricValueBoundsEnforced`. Real-DB precision:
`OutboxPostgresIT.maxNumericRoundTripsExactlyOnRealPostgres` (**NOT_RUN**).

## P2 — legacy (V14 NULL `document_digest`) identity

**Defect (confirmed):** for a legacy row with `document_digest IS NULL`, `disposeExisting` fell back to
**records-checksum** equality to declare `IDEMPOTENT_NOOP`. Records-checksum equality does **not** prove the
original full document (envelope/provenance) was identical — the exact gap cycle-18 closed.

**Fix (fail-closed, non-destructive):** a re-import whose snapshotId collides with a NULL-digest legacy row
is `REJECTED_CONFLICT_LEGACY_NO_DIGEST` (HTTP **409**). No digest is fabricated for the old row; no
destructive back-fill. The operator imports under a fresh snapshotId. V15+ rows carry a real digest and keep
exact NO_OP/CONFLICT semantics. The UI shows an accurate flash message.

**Tests:** `AnalyticsImportHardeningTest.legacyNullDigestReimportIsConflictNotNoop` (service, legacy row
untouched) + `v15DigestRowStillGivesExactNoopAndConflict`;
`AdminSecurityEnabledTest.legacyNullDigestReimportReturnsConflictAtEndpoint` (HTTP 409 at the commit
endpoint).

## Adversarial review (performed inline — no filesystem-capable subagent role available)

Re-examined the four boundaries against the actual code:
- **P1-A:** `audit.record` (REQUIRED) joins `writeTx`; the NO_OP/rejection audits (`recordRejection`,
  REQUIRES_NEW; `record` with no active tx) commit independently. `commit()` has no surrounding tx, so the
  post-race read uses a fresh connection. Unrelated integrity errors re-thrown. **No regression.**
- **P1-B:** honest about CSRF-before-Basic ordering (a token-less chunked POST is 401, not 413); the
  bounded-stream 413 is proven directly in the filter unit test. **No regression.**
- **P1-C:** context-metric values share the same `dec()` fit-check; `USE_BIG_DECIMAL_FOR_FLOATS` prevents a
  double-rounding false pass. **No regression.**
- **P2:** only true V14 rows have NULL digest (`build()` always sets it); non-destructive. The new
  disposition is mapped to 409 in both the API and UI controllers (no fall-through to 400). **No regression.**

## Unchanged / still gated

SEC-01 DONE, UI-03 DONE (no regression). **UI-01 / UI-02 remain PARTIAL** — no real producer snapshot exists;
synthetic importer status is `CONSUMER_VERIFIED_SYNTHETIC`, runtime `NOT_VERIFIED`. RUL-03/RUL-05 PARTIAL;
Q-19 unresolved. All 23 assets NOT_AUTHORIZED; workers disabled; phase closure 0/8.

## Remaining human gates / risks

- `mvn verify -Pdocker-it` on a Docker host to execute the two new analytics ITs + the lifecycle races
  (runtime PESSIMISTIC_WRITE still NOT_PROVEN).
- Producer acceptance (11-point gate) still requires explicit `projecao-carteira` sibling authorization — not
  requested this cycle.
- Prohibited and untouched: push, projecao-carteira/ticker-scraper, live Brapi, real WAHA, recipients,
  OPERATIONAL_ACTIVATION, production DB/deploy, trading, LEVEL/false-confirmation/cooldown while Q-19 open.
