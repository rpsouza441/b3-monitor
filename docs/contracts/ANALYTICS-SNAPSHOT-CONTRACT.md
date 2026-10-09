# Analytics Snapshot — Producer Boundary (v1)

**Status:** consumer contract implemented (FIN-01); producer NOT built and NOT authorized (FIN-06 is a human
gate). **"consumer contract implemented" ≠ "projecao-carteira integration complete".** This document
specifies what a FUTURE, separately-authorized `projecao-carteira` exporter must produce so the b3-monitor
consumer accepts it. It changes nothing in `projecao-carteira` and authorizes no integration.

## Scope

The producer emits a **public, per-asset daily analytics snapshot** — SMA/RSI/EMA/abnormal-volume
indicators (IND-01/02/04/05) and FIN-02 contextual metrics (Graham fair value, FII P/VP) — for the trusted
catalog assets. It is a point-in-time export, not a live feed.

**Hard exclusion (FIN-04).** The snapshot MUST NOT contain any private-portfolio datum: no XIRR, holdings,
quantities, cash flows, cost basis, positions, broker/account identifiers, P&L or trade lists. The consumer
refuses a document containing any such key, by name, anywhere in the tree.

## Schema / version

- `schemaVersion` MUST be exactly `b3-monitor.analytics-snapshot/1`. Any other value is rejected (fail
  closed) — the producer and consumer version this contract in lockstep; a new producer capability requires
  a new consumer schema version, never a silent field addition.
- Encoding: **UTF-8 JSON**, one object document. No archive, no multi-part, no embedded script/expression,
  no remote URL, no filesystem path, no executable content. Strings are bounded (≤ 200 chars); the whole
  document ≤ 512 KiB; ≤ 500 records.

## Envelope fields (all required unless noted)

| field | type | semantics |
|---|---|---|
| `schemaVersion` | string | must equal `b3-monitor.analytics-snapshot/1` |
| `snapshotId` | string | producer-assigned LOGICAL id; the idempotency/conflict key |
| `producer` | string | producer name, e.g. `projecao-carteira` |
| `producerVersion` | string | producer build/version |
| `generatedAt` | ISO-8601 instant | producer wall-clock at generation |
| `marketAsOf` | ISO-8601 date | the completed trading date the analytics describe (never a future date) |
| `timezone` | string (opt) | IANA zone the market date is expressed in |
| `sourceId` | string (opt) | provenance/source id (e.g. `COTAHIST-RAW`) |
| `checksum` | 64-char lowercase hex | SHA-256 over the **canonical records encoding** (below) |
| `records` | array | one object per asset |

### Record fields

| field | type | semantics |
|---|---|---|
| `ticker` | string | MUST be in the trusted AssetCatalog; unknown ⇒ reject |
| `asOf` | ISO-8601 date | completed-bar date; never future |
| `indicators` | object (opt) | typed indicators, each nullable + paired with a `*Readiness` enum |
| `context` | array (opt) | FIN-02 metrics: `{name, value, units, readiness, quality}` |
| `quality` | string (opt) | bounded quality note |
| `status` | string (opt) | OK / PARTIAL / UNKNOWN |

Typed indicators: `sma20`, `sma50`, `rsi14`, `ema9`, `ema21`, `volumeRatio`, each with a
`<name>Readiness ∈ {READY, NOT_READY, PARTIAL, NOT_SUPPORTED}`. **Readiness is never silently "ready"**: a
warmup/insufficient-history indicator MUST be `NOT_READY`, not fabricated (IND-03).

## Canonical encoding + checksum

The checksum is computed over a **stable canonical encoding of the records only** (not the envelope, which
carries the checksum):

1. Sort records by `ticker` ascending.
2. For each record emit `ticker|asOf|` then each indicator as `value,readiness;` in the fixed order
   sma20, sma50, rsi14, ema9, ema21, volumeRatio, then `|`, then each `context` metric sorted by `name` as
   `name=value:units:readiness#`, then `status\n`.
3. Numbers are normalized (trailing zeros stripped, plain string); null indicators emit an empty value.
4. `checksum = lowercase-hex( SHA-256( UTF-8 bytes of the above ) )`.

The producer MUST compute and embed this exact checksum. The consumer recomputes it and **rejects on
mismatch**. This is also the preview→commit binding token: a body that changed between preview and commit
produces a different checksum and is refused.

## Determinism / atomicity expectations

- **Deterministic:** the same inputs MUST produce byte-identical canonical records and therefore the same
  checksum. Re-exporting the same `marketAsOf` from the same inputs is a no-op import on the consumer.
- **Atomic delivery:** the producer MUST deliver a complete document; the consumer never partially imports
  (validation is all-or-nothing, and a DB failure rolls the whole import back).
- **Idempotency / conflict:** same `snapshotId` + same `checksum` ⇒ idempotent (NO_OP). Same `snapshotId`
  with a **different** `checksum` ⇒ CONFLICT (the consumer refuses to overwrite; the producer must mint a
  new id for new content).

## Consumer validation / refusal rules (authoritative)

The consumer FAILS CLOSED on: unknown schema version; malformed JSON / wrong types / missing required
fields; a ticker outside the trusted catalog; a future `marketAsOf`/`asOf`; checksum mismatch; a reused
`snapshotId` with a different checksum; any forbidden private-portfolio key; dangerous string content
(`<script`, `javascript:`, `://`, `file:`, `${`, `#{`, path traversal, NUL); oversize document/record/string.

A validated synthetic import is recorded as **`CONSUMER_VERIFIED_SYNTHETIC`** — never "real integration".
Runtime verification against a REAL producer snapshot remains **NOT_VERIFIED** until a separately authorized
`projecao-carteira` export is imported (FIN-06).

## What importing does NOT do

Importing a snapshot is **context only**. It does not authorize an asset, enable a worker, trigger Brapi or
WAHA, change a rule decision, or generate a buy/sell recommendation. Imported analytics are never read into
a rule evaluation in v1 (FIN-04).

## Preview → commit token (cycle-17 hardening)

The preview step returns an **HMAC-SHA256 token** bound to the full authorization context, not merely the
content checksum. The token's claims are `purpose | schemaVersion | snapshotId | checksum | actor |
issuedAt | ttl`, signed with a server secret and verified with a constant-time compare. On commit the
token is accepted only if **all** of purpose (`b3-monitor:analytics-import:v1`), schema, snapshotId,
canonical checksum and actor match the live commit, and it has not expired (default TTL 900 s). Therefore:
a token minted for one actor cannot be committed by another; a token for payload A cannot commit payload B
(the checksum differs); a token for snapshot/schema A cannot commit snapshot/schema B; a tampered, expired,
wrong-purpose or malformed token is rejected fail-closed. The token carries **no secret and no raw
payload** — only the digest. Replay is bounded to idempotency: the same token re-presented for the same
snapshotId+checksum yields `IDEMPOTENT_NOOP`; it can never authorize changed content.

## Strict v1 parser rules (cycle-17 hardening)

The consumer parses defensively, enforcing bounds before trusting content: strict **duplicate-JSON-key
rejection**; **unknown top-level fields rejected** (strict v1 — a future/foreign field is never silently
ignored); JSON nesting depth ≤ 12; non-finite numbers (NaN/Infinity) rejected at parse and defensively
again; per-metric numeric **scale ≤ 12, precision ≤ 24, magnitude ≤ 1e12**; duplicate tickers in one
snapshot rejected (no implicit merge). Temporal invariants: `generatedAt` not in the future (≤ 5-min skew),
`marketAsOf` not after today, and each record `asOf ≤ marketAsOf` (marketAsOf is the upper bound).
`importedAt` is always the injected server clock, never from the payload.

## Current analytics context selection

The "current" context per ticker is chosen from COMMITTED snapshots by **highest `marketAsOf`**, with the
latest `importedAt` as a deterministic tie-break — never by import order alone. An older-`asOf` snapshot
imported later therefore does NOT replace newer-`asOf` context; a rejected/conflicted preview is never
current.

## Producer acceptance checklist (future, human-gated — NOT implemented)

Promoting the consumer from `CONSUMER_VERIFIED_SYNTHETIC` to real integration requires ALL of the
following, none of which is done in this milestone and none of which touches `projecao-carteira` now:

1. explicit sibling-change authorization recorded (the human gate to modify `projecao-carteira`);
2. the exporter emits exactly schema `b3-monitor.analytics-snapshot/1`;
3. a SHARED canonical-checksum golden test the exporter and this consumer both pass;
4. the exporter stamps a real `producerVersion`;
5. atomic artifact write/delivery (no partial file ever visible to the consumer);
6. the artifact is generated from ACTUAL `projecao-carteira` data (not a synthetic fixture);
7. b3-monitor imports that exact artifact through the ADMIN preview→commit path;
8. documented provenance (source, as-of, version) on the imported snapshot;
9. reconciliation of a sample of tickers against the producer's own output;
10. no synthetic flag on the imported snapshot;
11. runtime status is promoted to VERIFIED **only after** that evidence exists — never inferred from
    `producer = "projecao-carteira"` alone.

**"consumer contract implemented" ≠ "projecao-carteira integration complete."**

## Cycle-18 hardening (consumer correctness/integrity)

- **Full-document token binding.** The preview→commit token binds BOTH the records checksum AND a
  **full-document digest** = SHA-256 of the EXACT request bytes. An envelope-only change (producer,
  producerVersion, generatedAt, marketAsOf, timezone, sourceId, quality) OR a whitespace-only change after
  preview therefore invalidates the token. Idempotency/conflict is on full-document identity: same
  snapshotId + same document ⇒ NO_OP; same snapshotId + any changed field ⇒ CONFLICT.
- **Unambiguous token encoding.** Claims are base64url-per-field (a `|` in snapshotId/actor cannot confuse
  the parser); a configured-but-short HMAC secret fails startup; TTL is bounded [60 s, 24 h]; expiry math is
  overflow-safe.
- **Strict v1 at every level.** Exact allowed-keys at envelope, record, indicators and context-metric
  levels; duplicate context-metric names rejected; metric name nonblank; record status ∈ {OK, PARTIAL,
  UNKNOWN}; timezone a valid IANA ZoneId or null.
- **Numeric contract.** Validator scale ≤ 12, precision ≤ 24; storage is NUMERIC(24,12) — exact, no silent
  rounding; an out-of-scale value is rejected before the DB.
- **Lossless context.** FIN-02 metrics are stored in a structured child table (name/value/units/readiness/
  quality) — every validated field round-trips; nothing is truncated.
- **Canonical checksum fidelity.** Length-prefixed, collision-free encoding that includes record.quality and
  context-metric quality (previously omitted). A delimiter-like string cannot collide with the framing.
- **Per-ticker current selection.** The "current" context is chosen PER TICKER (latest valid across all
  snapshots by marketAsOf/importedAt/id), correct for partial snapshots. No invented staleness SLA.
- **HTTP body-size limit** enforced before body materialization; oversize ⇒ 413 with no payload echo.
