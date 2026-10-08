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
