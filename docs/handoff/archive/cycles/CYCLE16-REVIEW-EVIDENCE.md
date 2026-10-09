# Cycle 16 — Review Evidence

Analytics-snapshot CONSUMER (FIN-01/FIN-03/FIN-04) + V14 provenance + ADMIN preview→commit importer +
UI-01/UI-02 progression + producer-boundary document.

`mvn test` → **231 passed, 0 failures, 0 errors** on **Spring Boot 4.1.1 / Java 21**, Flyway **V1–V14**
(was 216; +15 = 11 import + 4 import RBAC/UI). PostgreSQL IT = **NOT_RUN** (Docker absent). **No push.**
**No REAL producer snapshot was consumed** — all import evidence is synthetic (`CONSUMER_VERIFIED_SYNTHETIC`).

## Preflight (recorded)

- Branch `checkpoint/cycle7-reviewed`, HEAD at preflight `06492c6`, clean tree.
- `git branch -r` → only `origin/main`.
- Docker **not installed** (`docker --version` exit 1) → Postgres gate NOT_RUN.
- Baseline `mvn test` = 216 green. Flyway at V13 before this cycle → new migration **V14**.
- Seven protected inputs hashed; re-verified byte-unchanged at closure (handoff).

## Requirements anchor (not invented)

The contract is anchored to the LITERAL requirements, inventing no indicator semantics:
- **FIN-01** "versioned tested Java public-asset snapshot CONSUMER … status CONSUMER_VERIFIED_SYNTHETIC,
  never real Python integration";
- **FIN-03** quarantine unsafe/invalid imports + idempotency + conflicting ids;
- **FIN-04** imported metrics stay contextual, never enter rules; XIRR/holdings/cash-flows/cost-basis/
  identifiers are rejected;
- typed indicators **IND-01** SMA20/SMA50, **IND-02** RSI14, **IND-04** EMA9/EMA21, **IND-05** volume ratio;
  **FIN-02** contextual metrics (Graham, FII P/VP) with per-metric readiness/quality.

## 2 — Contract v1 (`AnalyticsSnapshotContract`)

- `schemaVersion = b3-monitor.analytics-snapshot/1`.
- Envelope: `snapshotId, producer, producerVersion, generatedAt, marketAsOf, timezone, sourceId,
  checksum, records`.
- Record: `ticker, asOf, indicators{sma20,sma50,rsi14,ema9,ema21,volumeRatio each + *Readiness},
  context[{name,value,units,readiness,quality}], quality, status`.
- Bounds: ≤ 512 KiB document, ≤ 500 records, ≤ 200-char strings, 64-hex checksum.
- Privacy: the typed record has NO field for private-portfolio data, and the validator refuses forbidden
  keys by name (FIN-04).

## Validation / idempotency rules (`AnalyticsSnapshotValidator`, fail-closed)

Rejects: unknown schema version; malformed JSON / wrong types / missing required fields; a ticker outside
the trusted catalog; a future `marketAsOf`/`asOf`; checksum mismatch vs the canonical records encoding; any
forbidden private-portfolio key (xirr/holdings/quantity/cashflow/cost_basis/position/broker/account/pnl/
portfolio/lots/trades); dangerous string content (`<script`, `javascript:`, `://`, `file:`, `${`, `#{`,
`<%`/`%>`, path traversal, NUL); oversize document/record/string. The **canonical checksum** (SHA-256 over
a stable, number-normalized records encoding) is the idempotency key and the preview→commit token.

## 3 — V14 provenance migration

`analytics_snapshot` (unique `snapshot_id`, schema/producer/version/generatedAt/marketAsOf/checksum/
recordCount/importedAt/importedBy/status, status CHECK = `CONSUMER_VERIFIED_SYNTHETIC`) + `analytics_context`
(FK, ticker, asOf, the six typed indicators + readiness, a bounded compact context string, quality, status;
cascade delete). No raw blob, no private column. The V13 audit CHECK is extended with `IMPORT_SNAPSHOT`. The
PostgreSQL IT (`ddl-auto=validate`) covers V1–V14, and new IT tests assert the analytics tables persist /
enforce the unique id / accept `IMPORT_SNAPSHOT`.

## 4 — ADMIN import: preview → commit (`AnalyticsImportService`)

- **preview** (`POST /api/admin/imports/preview`, `/admin/imports/preview`): validate-only, no persistence;
  returns schema/producer/snapshot/as-of/count/`canonicalChecksum`/errors + `wouldImport`.
- **commit** (`POST /api/admin/imports/commit`, `/admin/imports/commit`): re-validates the exact bytes;
  requires `expectedChecksum` (the preview token) to equal the freshly computed canonical checksum — changed
  content ⇒ `REJECTED_TOKEN_MISMATCH`; same id+checksum ⇒ `IDEMPOTENT_NOOP`; same id/different checksum ⇒
  `REJECTED_CONFLICT`; otherwise persist + append `IMPORT_SNAPSHOT` audit. It NEVER modifies
  OPERATIONAL_ACTIVATION, enables workers, triggers Brapi/WAHA, or modifies rules.
- **VIEWER** reads import history/analytics (bounded) but cannot preview/commit (POST under `/api/admin/**`
  is ADMIN-only). Browser mutation requires CSRF (chain-enforced).

## 5 — UI-01 (`/admin/analytics` + `GET /api/admin/analytics`)

Per-asset analytics context from the latest snapshot, **separate from quote freshness**. Shows producer/
version/schema/snapshotId/analytics-as-of/import-time/age + the typed indicators (each with its own
readiness) + FIN-02 context + quality. Distinguishes **analytics-stale** vs **analytics-missing** vs
**NOT_INTEGRATED** vs `CONSUMER_VERIFIED_SYNTHETIC`; quote staleness stays on `/admin/assets`. No rule
decision changes, no recommendation, no live-integration claim. **UI-01 achieved:** analytics context is now
inspectable and separate. **Missing:** real daily-indicator/Python values (producer not built) — still
NOT_INTEGRATED until FIN-06.

## 6 — UI-02 (`/admin/imports` + readiness)

Bounded import history (snapshotId/producer/version/asOf/importedAt/validation/checksum-prefix/actor/count),
viewer-readable. The readiness view gains an `analytics_consumer` component: **wiring READY**, operational
NOT_APPLICABLE, **runtime NOT_RUN/NOT_VERIFIED** — a synthetic import never marks runtime VERIFIED.
**UI-02 stays PARTIAL** — the literal acceptance (validated changes create audit revisions; pause stops
collection) is met for RULES, but catalog/policy administration via import is a consumer surface only; real
producer integration is unverified.

## 7 — Producer boundary document

`docs/contracts/ANALYTICS-SNAPSHOT-CONTRACT.md` specifies what a future authorized `projecao-carteira`
exporter must produce (schema/version, canonical encoding/checksum, as-of/null semantics, determinism,
atomic delivery, idempotency/conflict, consumer refusal rules) and states explicitly: **"consumer contract
implemented" ≠ "projecao-carteira integration complete"**. `projecao-carteira` was NOT edited.

## 8 — Tests (231 total; +15)

`AnalyticsImportTest` (11): valid preview→commit; unknown schema; malformed; unknown ticker; future as-of;
duplicate idempotent; same-id/different-checksum conflict; preview/commit token mismatch; oversize; forbidden
private key (FIN-04); audit written; synthetic status (never VERIFIED). `AdminSecurityEnabledTest` (+4):
VIEWER cannot preview/commit (403); ADMIN without CSRF denied (403); ADMIN+CSRF preview→commit succeeds (201
+ audit); import history & analytics context viewer-readable and separate from quotes (NOT_INTEGRATED with no
snapshot). `OutboxPostgresIT` (+2): V14 analytics tables persist/unique/cascade; audit CHECK accepts
IMPORT_SNAPSHOT. NOT_RUN (Docker absent).

## Human gates (unchanged)

push · modify projecao-carteira/ticker-scraper · live Brapi · real WAHA · recipients · OPERATIONAL_ACTIVATION
· production DB/deploy · trading · LEVEL/false-confirmation/cooldown while Q-19 is open. UNSELECTED
fail-closed; only explicit CROSSING operable; workers disabled.
