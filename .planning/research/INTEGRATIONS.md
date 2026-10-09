# Integration Research and Contract Proposal

## Critical correction — 2026-10-06

The current [critical review](../../docs/planning/CRITICAL-REVIEW.md), [contracts](../../docs/contracts/CONTRACTS.md) and [57-ID roadmap](../ROADMAP.md) supersede historical initialization/scope/integration claims below. Phase 4 stays absolute-price-only, with human-selected CROSSING or optional initial LEVEL (default UNSELECTED). Phase 5A percentage/AND-OR needs validated inputs, independently of history; 5B adds SMA/RSI/EMA9/21/finalized volume. Volatility is explicitly proposed v2 pending approval. Phase 7A synthetic consumer does not prove Phase 7B actual Python calculation export; separate producer authorization/fidelity and FIN-06 are required for real reuse. Shared WAHA/Brapi and owner recovery/compatibility gates remain open. Source age/polling/notification metrics are separate, with no guaranteed 60-minute notification. Historical baseline-only examples, source-absence statements and older sequencing below are not current defaults or working-integration claims.

## Current source-reconciled disposition — 2026-10-05

Historical detailed proposals below are preserved, superseded where they say audits absent, readiness unenumerated, cache-only unknown or commands in Phase 7. Current [reuse registry](../../docs/planning/PYTHON-REUSE.md), [contracts](../../docs/contracts/CONTRACTS.md), [ADRs](../../docs/architecture/ADRS.md), [questions](../../docs/planning/OPEN-QUESTIONS.md) and [roadmap](../ROADMAP.md) govern.

All ten files/23 CSV rows read. Python no API/exporter is a reported capability gap; independent schema below is a proposal, not a source interface. Exactly three initial public numeric candidates: Graham PARTIAL, P/VP snapshot READY/history PARTIAL, B&H PARTIAL, with public-input lineage. Other numeric fundamentals need explicit registry extension; Bazin/quality FII state-only. company_score/g0 lack initial numerical contract, Gordon forbidden. Personal XIRR/TWR/PM/cash flows/holdings/CDI portfolio remain excluded even when “per asset” or READY. Context-only scope requires human review.

COTAHIST audited close-only RAW export proposal separate from context; 245/010/PREULT/100, annual/schema READY/security PARTIAL, no adjusted series/exporter. Source authorization/fidelity remains prerequisite for actual data, not for the independent Phase 4 price monitor. Scraper no cache-only HTTP is established: current GET/raw may scrape, so adapter disabled with no SQL/credential/legacy workaround. Upstream recommendations are not execution authorization.

Outbound WAHA begins Phase 4 with atomic outbox, uncertain execution, auth/isolation/rate/pause/security/restore. No supplied audit verifies its adapter/version. SMTP and inbound command contracts arrive Phase 6 with new-surface SEC/OPS checks before enablement. Acceptance≠delivery; absent receipt≠proof of safe resend. Historical generic enums/fixture IDs are proposal vocabulary, not requirement ownership or source readiness.

## Historical pre-reconciliation detailed proposals (superseded status/ordering)

**Project:** B3 Monitor  
**Prepared:** 2026-10-05  
**Scope:** Independent Python financial exports, official COTAHIST historical reuse, complementary scraper fundamentals, WAHA/SMTP notifications and commands  
**Status:** PROVISIONAL v1 planning proposal; audit-dependent mappings are not finalized; no implementation, migrations, provider calls or external-system access

## Evidence and confidence

The authorized repository initially contained `README.md` with only the project name. The user subsequently reported copying ten source documents, but the parent agent's exact local path checks still found every expected document absent. The initial brief and subsequent user clarification are the available domain evidence for this review; no contents of those ten documents have been read. No sibling repository, Python source, scraper source, credentials, live WAHA session, database, network service or external documentation was inspected.

The latest user clarification makes official COTAHIST historical reuse and complementary scraper fundamentals v1 design priorities. All three projects retain operational independence, and no legacy project modification is authorized or proposed. Missing evidence leaves producer schemas, coverage, readiness and adapter mappings provisional; it does not remove these priorities from the design.

| Statement | Evidence available | Confidence / treatment |
|-----------|--------------------|------------------------|
| Python contains partially or fully validated XIRR, TWR, CDI, valuation and B3 history ingestion | User brief; audit/source absent | Unverified implementation capability; require sanitized evidence and synthetic fixtures |
| Scraper provides snapshots/fundamentals and is not an OHLCV history service | User brief; audit/source absent | Scope constraint accepted; underlying capability not independently verified |
| Official COTAHIST reuse and complementary scraper fundamentals are v1 priorities | Subsequent user clarification | Authoritative scope direction; actual outputs, fields and available cache-only operations remain unverified |
| Some calculations are PARTIAL, DIAGNOSTIC_ONLY or NOT_READY | User explicitly requires these states to remain distinct | Contract requirement; actual exporter status vocabulary remains unverified |
| WAHA accepts sends, supports webhooks and exposes a reliable delivery-status mechanism | Only WAHA use is required by brief | Exact API, event IDs, authentication, statuses and lookup/idempotency capabilities unknown |
| Proposed import and outbox patterns address the identified failure cases | Design analysis, not product-specific verification | Recommended design; prove with acceptance tests before activating integrations |

Recommendations below are contracts to agree and verify. They do not assert existing Python, scraper or WAHA functionality. The missing reports must be reviewed before freezing producer mappings or provider adapters. No current documentation claims or vendor API shapes are introduced.

## Decisions

1. Keep a single Java/Spring Boot application with PostgreSQL, including import staging, the notification outbox and webhook inbox. Do not add a broker or a permanently running Python sidecar.
2. Prefer an independently generated explicit, asset-only JSON export under Python-owner control, using existing authorized outputs or a separately operated export boundary. Initially an authenticated administrator imports an explicitly selected file. The monitor neither launches Python nor reads the portfolio repository or its private inputs. This proposal requires no modification to the legacy Python application; the available export mechanism must be established from the missing documents.
3. Preserve immutable financial snapshots as contextual information. All financial snapshot metrics remain ineligible for market-rule signals in the first milestone, including those marked READY.
4. Do not implement XIRR, TWR, CDI or valuation formulas in Java. Java may validate representation, identity, dates, units and schema, and display exporter-provided results and qualifications.
5. Include complementary scraper fundamentals in the provisional v1 design through a cache-only boundary. Consume already cached, explicitly verified fundamentals without refreshing the scraper cache, changing its TTL, modifying legacy code or treating its current-price field as an intraday quote. If an existing operation cannot meet this contract, record the blocked capability rather than invent one or modernize the scraper.
6. Treat WAHA request acceptance as distinct from confirmed delivery. Manage retries and fallback through an explicit delivery state machine that represents uncertain outcomes.
7. Authenticate webhook transport and authorize the derived sender separately. Record an inbound event once before executing a bounded, allowlisted command.
8. Include a separate, independent COTAHIST history-export/import boundary in the provisional v1 design. Preserve official source provenance and price adjustment semantics; enable history use only after the source layout, exporter fidelity, instrument coverage and calculation suitability are verified. Do not transfer the contextual-only financial-metric policy into a blanket ban on verified historical bars, or use history approval to upgrade financial diagnostics.

## Ownership and boundaries

| Owner | Owns | Does not own |
|-------|------|--------------|
| Python project owner | Formulas, validated formula versions, input selection, source provenance, readiness assertions, existing COTAHIST ingestion/output evidence, independently operated export boundary | Java alert evaluation, Java import activation, monitor availability; no required legacy changes |
| Java monitor | Asset catalog, schema validation, import audit, immutable storage, contextual display, rule state, outbox/inbox | Recalculation or repair of portfolio metrics; automatic approval of producer reliability |
| Administrator | Authorized file selection, exporter approval, asset mapping, freshness policies, recipient/command allowlists | Override that relabels a diagnostic result as validated |
| WAHA adapter | Mapping verified WAHA API/event semantics into application delivery states | Claiming acceptance proves recipient delivery |
| SMTP adapter | Recording verified SMTP handoff result and uncertain outcomes | Claiming SMTP handoff proves inbox receipt |
| Provisional v1 scraper adapter | Reading approved cached fundamentals with original metadata | Fetching new pages, changing TTL as a side effect or modifying the scraper |

Sensitive portfolio inputs, holdings, transactions, cash flows, account identifiers, investor identity and portfolio-level performance are outside the export/import boundary. XIRR/TWR are named existing capabilities, not required imports: most portfolio results require private cash flows or holdings and are excluded from the asset-only first contract. Existing CDI calculations remain in Python; an asset-only contract must not silently widen to a benchmark/portfolio namespace. A separately approved public-benchmark contract can be considered later.

## Proposed Python JSON snapshot contract

### Version and lifecycle

Use `schema_version: "1.0.0"`. This is a proposed contract version, not an existing exporter format. JSON Schema and a short semantic specification must be agreed before implementation. Pin the supported version; reject unsupported major/minor versions until explicitly enabled. Permit additive information only under an approved, bounded extension namespace. Reject undeclared top-level fields so accidental private exports are caught. Never silently infer a schema from an old producer file.

An envelope represents one coherent export generation. A bundle may contain several asset snapshots. Each metric has its own reference date and provenance because financial observations inside one export can have different as-of dates. Activation is atomic for the validated bundle. Invalid bundles stay in a quarantine state and do not replace previously active snapshots.

### Envelope fields

| Field | Type / rule | Purpose |
|-------|-------------|---------|
| `schema_version` | Exact supported semantic-version string | Decode using an agreed contract |
| `export_id` | Stable opaque identifier within approved exporter namespace | Idempotency; retain across a resend of the same export |
| `revision` | Proposed optional nonnegative producer revision within an immutable export series; exact support requires contract agreement | Correction ordering, never import arrival order |
| `supersedes_export_id` | Proposed optional reference to a prior accepted export from the same approved producer and compatible scope | Explicit correction lineage; reject unknown target, self-reference, cycles and scope mismatch |
| `exporter` | Approved producer ID, application version, calculation-definition version | Producer attribution and calculation traceability |
| `generated_at` | RFC 3339 instant with offset, stored in UTC | Generation time, never the observation date |
| `reference_date` | ISO calendar date; envelope's declared as-of date | Bundle context; metric dates remain authoritative |
| `scope` | Literal `ASSET_PUBLIC_CONTEXT` | Explicit privacy and use boundary |
| `source` | Structured source ID/name, dataset ID/revision if available, observation/retrieval time when known | Trace public upstream evidence |
| `readiness` | `READY`, `PARTIAL`, `DIAGNOSTIC_ONLY`, `NOT_READY` | Exporter assertion; preserve exact state |
| `data_quality` | Status `VALIDATED`, `WARNINGS`, `INVALID`, `UNKNOWN`, with reason codes and bounded explanation | Quality assertion separate from readiness |
| `coverage` | Requested/exported asset IDs, supported classes, observation periods, omitted metrics, completeness flag | Explain what was and was not exported |
| `limitations` | Required bounded array; may be empty only when exporter explicitly asserts no known limitation | Preserve qualifications |
| `assets` | Bounded list, no duplicate identity within bundle | Asset-scoped observations only |

Producer-supplied status text is not evidence of validation by itself. An approved mapping must associate each metric definition and calculation version with its sanitized validation evidence. If evidence is absent, retain the original assertion and label local assurance `UNVERIFIED`. Preserve import receipt time, authenticated importing administrator, content digest and assurance separately from producer claims.

### Asset and metric fields

| Field | Type / rule | Purpose |
|-------|-------------|---------|
| `asset_identity` | Venue `B3`, exact source ticker, instrument class; optional ISIN and source-specific ID | Resolve against the monitor's catalog; never classify solely by ticker suffix |
| `reference_date`, `generated_at` | Date and instant with explicit meaning | Retain asset-level dates when they differ from the envelope |
| `source`, `readiness`, `data_quality`, `coverage`, `limitations` | Required at asset level | Never hide partial coverage behind a bundle status |
| `metrics[].metric_code` | Registry-controlled name | Avoid names with ambiguous formulas |
| `metrics[].metric_id` | Proposed stable identity in the approved export-series namespace; required if metric correction references are used | Resolve a supersedes target without guessing from metric code or arrival order |
| `metrics[].definition_version` | Approved immutable definition/version | Identify financial meaning without reimplementing formula |
| `metrics[].revision`, `metrics[].supersedes_metric_id` | Proposed optional correction revision and prior stable metric identity; only an agreed registered schema may accept these fields | Equal-reference-date replacement must be explicit, same asset/definition/source/basis, cycle-free and conflict checked; otherwise retain conflict pending review |
| `metrics[].value` | Finite JSON number, parsed as decimal; explicit `null` only with missing-value reason | Preserve numerical result; zero is not a substitute for absence |
| `metrics[].unit` | Controlled code, such as `BRL_PER_UNIT`, `PERCENT`, `FRACTION`, `MULTIPLE`, `SHARES` | Eliminate percent/fraction, total/per-share and currency ambiguity |
| `metrics[].currency` | Required ISO currency where unit carries monetary value | Reject implicit BRL assumptions |
| `metrics[].reference_date`, `metrics[].period` | As-of date and observation period where applicable | Distinguish period returns from instantaneous measures |
| `metrics[].source` | Producer/upstream attribution and dataset revision | Mixed sources remain explicit |
| `metrics[].readiness`, `metrics[].data_quality`, `metrics[].coverage`, `metrics[].limitations` | Required metric-specific qualifications | Expose differing status inside one asset |
| `metrics[].price_semantics` | Required for prices and price-derived metrics; explicit `NOT_APPLICABLE` otherwise | Prevent incompatible comparisons |

`price_semantics` declares `RAW`, `ADJUSTED` or `UNKNOWN`; for adjusted values it also declares adjustment methodology/version and covered corporate-action types. Missing documented methodology produces UNKNOWN semantics, not presumed comparability. Units and split-adjustment bases must be compatible before comparing derived values. Imported financial prices never overwrite the monitored quote or technical-history stores.

JSON Schema alone cannot prove these semantics. The importer needs cross-field checks for finite decimals, enum values, date relationships, class/metric eligibility, currency requirements, duplicate identities, status/value consistency and unit-compatible interpretation. Limits on document bytes, nesting, text lengths and asset/metric counts are fixed before enabling uploads. Duplicate JSON keys are rejected. Any stored source URLs are inert provenance text, not URLs to fetch.

### Synthetic example

This example demonstrates the intended shape only. `SYNTH3`, its metric and all values are synthetic. No formula or existing Python field is asserted. Repeated nested metadata is deliberate in this draft so status inheritance cannot accidentally upgrade a metric.

```json
{
  "schema_version": "1.0.0",
  "export_id": "synthetic-export-001",
  "exporter": {
    "id": "approved-python-exporter",
    "application_version": "synthetic-1",
    "calculation_definition_version": "synthetic-1"
  },
  "generated_at": "2026-10-05T12:00:00Z",
  "reference_date": "2026-09-30",
  "scope": "ASSET_PUBLIC_CONTEXT",
  "source": {"id": "synthetic-public-fixture", "dataset_revision": "1"},
  "readiness": "DIAGNOSTIC_ONLY",
  "data_quality": {"status": "UNKNOWN", "reason_codes": ["SYNTHETIC"]},
  "coverage": {
    "requested_assets": ["B3:SYNTH3"],
    "exported_assets": ["B3:SYNTH3"],
    "supported_classes": ["EQUITY"],
    "observation_period": {"start": "2026-09-30", "end": "2026-09-30"},
    "omitted_metrics": [],
    "complete": true
  },
  "limitations": ["Synthetic fixture; contextual display only"],
  "assets": [{
    "asset_identity": {"venue": "B3", "ticker": "SYNTH3", "instrument_class": "EQUITY"},
    "reference_date": "2026-09-30",
    "generated_at": "2026-10-05T12:00:00Z",
    "source": {"id": "synthetic-public-fixture", "dataset_revision": "1"},
    "readiness": "DIAGNOSTIC_ONLY",
    "data_quality": {"status": "UNKNOWN", "reason_codes": ["SYNTHETIC"]},
    "coverage": {"observation_period": {"start": "2026-09-30", "end": "2026-09-30"}, "omitted_metrics": [], "complete": true},
    "limitations": ["Synthetic fixture"],
    "metrics": [{
      "metric_code": "SYNTHETIC_PRICE_CONTEXT",
      "definition_version": "1",
      "value": 10.25,
      "unit": "BRL_PER_UNIT",
      "currency": "BRL",
      "reference_date": "2026-09-30",
      "period": {"start": "2026-09-30", "end": "2026-09-30"},
      "source": {"id": "synthetic-public-fixture", "dataset_revision": "1"},
      "readiness": "DIAGNOSTIC_ONLY",
      "data_quality": {"status": "UNKNOWN", "reason_codes": ["SYNTHETIC"]},
      "coverage": {"observations": 1, "complete": true},
      "limitations": ["Cannot be used for quote freshness or alert evaluation"],
      "price_semantics": {"basis": "RAW", "adjustment_method": "NOT_APPLICABLE"}
    }]
  }]
}
```

### Import sequence and idempotency

1. Accept only an authenticated administrator's explicitly provided file. No broad directory discovery, sibling mounts, cron invocation of Python or recursive filesystem scans. For a later local inbox, require an explicitly approved directory, reject symlinks/path traversal and accept only files atomically moved into that directory.
2. Validate size and format, compute a digest, resolve approved exporter identity and record receipt metadata. A checksum establishes content integrity, not producer authenticity. Initial manual import relies on administrator authorization; signing is a later decision if transport becomes automated.
3. Use `(approved_exporter_id, export_id)` as the unique import key. Same key and identical digest returns the existing import without side effects; same key and different content is a conflict and stays quarantined. Equivalent semantic content with a new export ID is stored as a separate producer observation but must not duplicate rule/notification effects. Snapshot imports generate no signal events in the initial milestone.
4. Validate the entire bundle, exact catalog mapping, allowed metric registry, required status/quality/units and privacy allowlist. Record failed validation without logging the rejected body. Do not persist accidental private payloads as ordinary audit attachments.
5. Atomically save immutable accepted snapshots and update eligible display pointers. Null/missing metrics remain explicit. `INVALID` values are excluded from usable display context; diagnostic/not-ready results may appear only in an explicitly qualified diagnostic view. Never relabel exporter status during import.
6. Prefer the latest supported reference date for each catalog asset/metric/definition/source/price-semantics series, not whichever file arrives last. For equal dates, only an explicit producer revision/supersedes relation selects a replacement; otherwise keep the conflict visible pending review. Do not merge conflicting metric definitions or sources into one silent latest-value field.
7. Retain superseded records and activation lineage. Rollback restores prior display pointers in one transaction; it does not delete historical evidence or rewrite exporter status.

### Freshness, dates and diagnostic treatment

Keep three clocks: source reference date/observation period, producer generation timestamp and monitor import timestamp. A newly generated export containing last year's financials is still old. Reference dates use their declared calendar meaning; generation/import instants use UTC with the original timezone metadata where applicable. Configure acceptable age per metric and source; do not borrow the intraday quote freshness threshold for quarterly financial data.

Represent local assurance, staleness, readiness, data quality and coverage independently. A READY result can be stale or poorly covered; a fresh result can be diagnostic. An envelope with READY cannot improve a PARTIAL child. Display metric-level and enclosing qualifications together and apply the most restrictive usability result without changing the source fields.

There is no initial path from imported valuation/performance fields to rule operands, ranking, buy/sell recommendations or alert messages framed as reliable signals. A future use requires a separately specified metric, methodology review, verified evidence, compatibility tests and explicit scope approval. Historical portfolio valuations are especially unsuitable as present-day signals because they may depend on private holdings, dated assumptions, incomplete data or methodology intended for diagnostics.

## Independent COTAHIST history contract: provisional v1

The user prioritizes reuse of official B3 COTAHIST history through the existing Python project's capabilities, while preserving that project's independence. Prefer a separately generated, versioned, public-asset history artifact from existing authorized outputs. The monitor imports an explicit artifact; it does not execute legacy ingestion, require continuous Python availability, reach into sibling directories, change financial formulas or modify the legacy project. Whether the existing outputs can support this boundary remains audit-dependent. A standalone export/normalization utility is a proposal for later separately authorized work, not an assertion that an exporter already exists.

Keep this history contract distinct from the financial-context snapshot contract. Its proposed envelope carries schema version, export ID, producer/version, official source/dataset identity, original archive/file digest where available, declared coverage, generation timestamp, importer assurance, readiness, quality and limitations. Per-series metadata carries catalog identity, verified instrument/market discriminator, observation range, calendar/trading-date convention, currency, units, and explicit RAW/ADJUSTED/UNKNOWN semantics with adjustment methodology and corporate-action coverage where applicable. Per-observation fields contain only values actually supported by the verified producer output, with trading date and quality/missingness. These are proposed normalized contract fields, not claims about the official COTAHIST record layout.

Do not assert archive periods, field offsets, numerical scaling, current format, supported instrument classes, adjustment status, complete OHLCV availability or validated exporter coverage until the documents establish them. In particular, distinguish traded quantity from monetary turnover and preserve each unit. Do not fabricate OHLC values from a close-only export or map turnover into share volume. Lack of a verified market/instrument discriminator blocks ambiguous identity mapping.

Stage and validate files using the same privacy, size, schema, digest and idempotency controls as financial imports. Add series-specific checks for duplicate trading dates, conflicting revisions, declared coverage versus delivered records, actual field availability, exact decimal scaling and verified identity. Reject invalid OHLC relationships only when those OHLC fields are present and their meaning is documented. Missing market sessions remain explicit coverage gaps, with the approved calendar distinguishing holidays from missing observations. Import receipt/generation time does not turn an old history endpoint into a current market observation.

Store COTAHIST and Brapi observations with separate source and adjustment identities. Neither provider silently overwrites the other. Do not concatenate overlapping/raw/adjusted series merely to reach indicator warm-up length. An explicit series-selection/combination policy must first verify comparable definitions, units, adjustment basis, market identity, overlap consistency, coverage and corporate-action behavior. Unknown semantics or failed overlap checks leave both sources visible but ineligible for a combined indicator series. Preserve original rows and validation lineage; never repair legacy prices or create adjustment factors by inference.

Approved historical bars may support daily indicators only for the compatible fields and consecutive/declared coverage required by that indicator, after audit and fixture gates pass. A successful file import, READY assertion or official-source label alone is insufficient. Historical ingestion is distinct from alert evaluation: importing/backfilling an artifact does not emit past alerts, and subsequent live evaluation records the selected series/version. No intraday bars are inferred from daily history. No XIRR/TWR/CDI/valuation calculation is implemented in Java.

If Python is offline or an artifact cannot be produced, the monitor retains approved previously imported history, reports the coverage endpoint and blocked capability, and continues its independently available quote/rule paths subject to their own data-sufficiency gates. Rollback switches the active history version back to an approved predecessor and invalidates/recomputes only affected technical-indicator caches; immutable source records and signal lineage remain intact. Rollback does not rerun alerts or recalculate financial formulas.

## Complementary scraper fundamentals contract: provisional v1

Accept only approved cached fundamentals through an existing verified operation that has no refresh-on-read behavior, or through an explicitly generated independent public-context artifact from existing sanctioned outputs. Preserve source, underlying observation date, cache-generated time, expiry metadata, readiness, coverage and units. Never turn retrieval time into market freshness. Serve a cache miss as unavailable; it must not trigger scraping. The brief does not verify that either operation already exists. Establish that mapping from the missing reports; if neither boundary is available without legacy modification, keep the v1 integration explicitly blocked pending a scope decision rather than silently marking it ready or removing the design priority.

Map verified fundamentals into the same qualification/units/privacy model as financial context, preserving the scraper as a distinct producer. Do not recreate the Python valuation engine in Java, replace its calculation definitions with undocumented scraper fields or merge conflicting source observations into a silent latest value. Label unavailable, stale, partial and diagnostic context explicitly. Adapter mapping and supported metrics remain provisional.

No OHLCV series is inferred from snapshots. No current-price field is promoted into live quote collection. No cache TTL, scraper internals or historical provider semantics are changed. Scraper unavailability does not stop independently supported quote/indicator/alert paths. Rollback disables the adapter or restores previous context pointers, retaining import lineage; it does not change the scraper.

## Notification outbox and delivery contract

### Transaction boundary

Persist a signal event, rule-state transition and logical notification outbox record in the same PostgreSQL transaction. Uniqueness is based on the rule's occurrence/rearm cycle and intended recipient; it cannot be based only on asset/condition text, which would suppress legitimate future rearmed alerts. Channel attempts reference the one logical notification. A recipient/channel key prevents independently scheduled retries/fallbacks for the same occurrence from creating new logical notifications.

Dispatch after commit. Claim due attempts using leases with bounded concurrency. Record attempts, timestamps, retry reason and sanitized external message ID. Never hold a database transaction open during provider network calls. If a worker crashes after sending and before recording the result, lease recovery must treat the send as uncertain rather than unconditionally send again.

### Proposed states

The table is a convenience view of transport and delivery dimensions, not one destructive state field. Canonical CONTRACTS.md retains transport ACCEPTED independently while delivery is UNKNOWN or CONFIRMED. `DELIVERED` below is a presentation alias for evidence-backed delivery CONFIRMED; absent receipt never erases accepted handoff. UNKNOWN_OUTCOME concerns ambiguous send execution/status and is distinct from an accepted send awaiting confirmation.

| State | Meaning | Allowed next action |
|-------|---------|---------------------|
| `PENDING` | Durable intent, no active attempt | Claim for dispatch |
| `IN_FLIGHT` | Worker owns a leased attempt | Send once; record result |
| `ACCEPTED` | Provider accepted request under verified API semantics | Await delivery evidence or configured status timeout |
| `DELIVERED` | Verified status/event confirms delivery semantics | Stop retries/fallback for this logical notification |
| `RETRYABLE_FAILURE` | Verified failure and safe retry classification | Bounded backoff with jitter |
| `PERMANENT_FAILURE` | Verified invalid recipient/auth/config/etc. | No automatic send loop; surface incident; fallback if policy permits |
| `UNKNOWN_OUTCOME` | Timeout, lost response, process crash or conflicting status leaves uncertainty | Reconcile by verified provider lookup/event; apply explicit uncertainty policy |
| `EXHAUSTED` | Retry/time budget exceeded | Terminal delivery incident/fallback decision |

These are application states; do not assume WAHA emits any of these names. The mapping is a capability-gated adapter decision. Expose provider acceptance, delivery confirmation and unknown outcomes separately on the dashboard. SMTP acceptance likewise means relay handoff, not inbox delivery.

### Ambiguous outcomes, retry and fallback

Provider idempotency keys and message-status lookup are unverified. Require a sanitized capability contract before relying on either. If an idempotency feature is verified, reuse the same logical key across retries. Otherwise there is no credible end-to-end exactly-once guarantee across the application and provider.

For the initial minimal policy, retry a definitively rejected/transient send only when the adapter proves retry safety. An unknown send outcome enters `UNKNOWN_OUTCOME` and is not automatically resent on WhatsApp. Reconcile within a bounded window when a verified event/lookup exists. After that window, atomically schedule at most one SMTP fallback per logical alert if the configured fallback policy allows uncertain-outcome fallback. This may reach the user through both channels; the UI/audit must disclose that risk, and both messages share a stable alert reference. Never describe this as cross-channel exactly-once delivery.

Concurrent failure callbacks, worker retries and fallback schedulers all lock/update the same logical record before creating a channel attempt. Before dispatching a fallback, recheck whether verified delivery has already arrived. Late WhatsApp delivery cancels a still-pending fallback; it cannot retract an already accepted email. Late/out-of-order callbacks never downgrade DELIVERED, resurrect a terminal attempt or create another fallback. Duplicate external events have no additional effect.

Set bounded attempt counts, timeouts, backoff and provider/session-specific concurrency. Transport-auth failures should open an observable incident rather than consume the entire retry budget repeatedly. Use synthetic providers for tests; do not send real notifications during planning or acceptance development.

## WAHA webhook and command boundary

Transport authenticity and end-user authorization solve separate problems. A reachable webhook URL or allowlisted sender text does not prove the sender is authentic. Restrict WAHA/admin interfaces to the intended private networks; the application webhook uses a verified supported authentication mechanism. If cryptographic event signatures exist, verify the raw body according to the pinned adapter contract. If only a configured secret/reverse-proxy mechanism is supported, use that verified mechanism and document its security boundary. Neither feature is currently verified.

Keep credentials out of URLs/logs/export files. Apply payload limits, rate limits and strict input schemas before dispatch. Derive the principal from verified event sender fields; never trust a user-supplied command argument, display name or chat subject as an identity. Include session identity and, where applicable, group participant identity. Initially accept direct-message commands only; group commands require a separate participant authorization contract. Separate recipient allowlists from command/admin permissions.

Persist a webhook inbox record with a unique `(verified_session_id, provider_event_id)` when stable IDs are confirmed. If IDs are unavailable, approve a deterministic fingerprint of immutable authenticated event fields and retain it for the agreed replay horizon. A payload hash alone can mistake legitimate identical commands for duplicates; timestamp-only keys can miss replays. The missing event contract is an implementation gate, not a reason to guess field names.

Respond promptly after durable receipt, then process asynchronously in the same application. A replay receives a successful acknowledgement for the already-recorded event without executing again. Provider event timestamps help identify old events but do not substitute for inbox deduplication; define acceptable age and delayed-delivery behavior from verified provider semantics. Outbound echoes, self-sent messages, delivery receipts and unrelated event types cannot enter the command parser.

The proposed phase-7 command set is small: status and quote lookups plus authorized pause/resume of an existing rule owned by the authenticated principal, aligned with CMD-03/SPEC. Status may include rule state; there is no separate rule-status grammar. These pause/resume commands remain disabled until transport authentication, principal mapping, ownership/permission checks, replay protection and rule-transition semantics have passed their gates. Every command uses a normalized grammar, fixed handler and principal permission check. No shell execution, arbitrary URLs, file paths, SQL fragments, automatic trading, secrets, private portfolio inspection or commands forwarded to Python. Rule creation/deletion, threshold/recipient changes, global configuration and broader mutations remain in the authenticated dashboard; pause/resume grants no additional mutation authority.

Command parsing and execution are independent of webhook receipt. Link results to inbox ID and principal; enqueue replies through the normal outbox. For pause/resume, recheck ownership and allowed current state during processing; commit the inbox execution result, allowed rule-state transition and reply intent in one transaction. A repeated pause of an already paused rule or resume of an already enabled rule has no new state effect, and command replay cannot execute again or enqueue another reply. Concurrent changes use the rule engine's approved concurrency/transition policy; pause/resume cannot bypass its deduplication/rearming safeguards, invent historical alerts or change rule parameters. Avoid sending an explanation to an unauthorized sender, which could turn the application into a reply amplifier.

## Risks and minimal solutions

| Architecture risk | Minimal solution | Review gate |
|-------------------|------------------|-------------|
| An absent audit becomes implied proof of exporter readiness | Mark producer claims unverified; require sanitized audit plus synthetic fixture for each approved metric | Audit review before integration mapping is frozen |
| Export accidentally includes holdings/private cash flows | Public asset-only scope, approved fields, strict rejection and log redaction | Privacy fixture tests and independent contract review |
| Java recreates formulas while normalizing imported data | Restrict to schema/semantic validation; preserve units/results; no formula implementations | Review confirms no XIRR/TWR/CDI/valuation code |
| Fresh generation hides stale financial observations | Separate reference/generation/import dates and metric-specific age policies | Stale-reference fixture remains visibly stale |
| Raw and adjusted observations are silently mixed | Explicit semantics/methodology, distinct storage series and compatibility checks | Mixed-basis fixture rejected for combined use |
| Export ingestion becomes runtime coupling to portfolio app | Manual explicit file first, autonomous Python generation, no process launch/mount | Monitor remains functional with Python absent |
| Snapshot scraper is used as live quote/history service | Provisional v1 cache-only adapter; no refresh-on-read, TTL or legacy changes | Cache miss creates no network scraping work |
| COTAHIST reputation substitutes for verified exported data | Separate versioned history boundary; evidence-based layout/scaling/coverage/identity and adjustment mapping | Producer fidelity, overlap and corporate-action fixtures before indicator eligibility |
| New v1 integrations create operational coupling | Explicit artifacts/cache-only reads, retained imported history/context and unavailable states | Python/scraper offline tests preserve supported monitor paths |
| WAHA HTTP success is displayed as delivered | Separate ACCEPTED and DELIVERED using verified adapter mapping | Accepted-without-receipt stays unconfirmed |
| Crash/timeout sends duplicate alerts | Durable outbox, attempt leases, UNKNOWN_OUTCOME state, no blind resend | Post-send crash/timeout scenarios |
| Fallback races cause repeated email/WhatsApp messages | Single logical notification, channel uniqueness, transactional fallback transition | Concurrent callback/retry/fallback tests |
| Forged/replayed webhook executes commands | Verified transport authentication, principal allowlist, durable inbox and bounded grammar | Forgery, replay, echo and permission tests |

## Acceptance and failure tests

All test data is synthetic. Gates below verify application behavior without requiring live portfolios or real delivery.

| ID | Scenario | Required observable result |
|----|----------|----------------------------|
| INT-01 | Import valid supported asset-only export | One audited immutable import; exact decimals/units/dates/statuses preserved; no signals/outbox events |
| INT-02 | Resend same exporter/export ID and digest | Same recorded result; no new snapshots or side effects |
| INT-03 | Same exporter/export ID with changed body | Explicit conflict/quarantine; active pointers unchanged |
| INT-04 | Unsupported version, duplicate keys, malformed/truncated JSON, too-large/deep input | Whole bundle rejected safely; previous usable context retained |
| INT-05 | Holdings, account, transactions or cash-flow fields appear | Privacy rejection; payload contents absent from logs/audit errors |
| INT-06 | PARTIAL/DIAGNOSTIC_ONLY/NOT_READY/UNKNOWN-quality result in a READY envelope | Child qualifications retained; no promotion; contextual/diagnostic treatment only |
| INT-07 | READY and fresh-generation metric has old reference date | Visible stale status determined from reference date; no rule eligibility |
| INT-08 | Missing value, JSON NaN/infinity, wrong unit/currency or unknown metric | Missing stays missing; malformed values or unsupported semantics rejected; no zero substitution |
| INT-09 | Raw/adjusted/unknown basis or different adjustment methods | Separate series; incompatible comparisons blocked; live quote/history stores unaffected |
| INT-10 | Older export arrives after newer; equal-date conflicting revision arrives | No date regression; unresolved tie remains visible until explicit supersession/review |
| INT-11 | Python stops or no export arrives for a month | Core monitor continues; financial context ages visibly; no automatic Python launch |
| INT-12 | Roll back an accepted import activation | Previous pointers restored; immutable imports/audit remain; no recalculation or notifications |
| INT-13 | Asset catalog mismatch, renamed ticker or unmapped instrument class | No guessed identity/class mapping; quarantine and explicit catalog review |
| INT-14 | Decimal encoding/roundtrip at agreed precision boundaries | Exact accepted decimal survives exporter/importer/storage path; presentation rounding does not mutate stored result |
| INT-15 | Provisional v1 scraper returns cache miss/expired item | Unavailable/stale state; no refresh request, TTL mutation or legacy change |
| INT-16 | Scraper lacks a verified cache-only operation or sanctioned artifact | Integration remains explicitly blocked/provisional; no assumed endpoint or modernization |
| HIS-01 | Supported synthetic COTAHIST-derived export with verified mapping | Exact values/units/identity/trading dates/provenance preserved; versioned history stored; no backfill alerts |
| HIS-02 | Missing/unknown layout mapping, scaling, market discriminator or price adjustment semantics | Quarantine or ineligible series; no guessed record layout/classification/scale |
| HIS-03 | RAW/ADJUSTED/UNKNOWN series or incompatible provider overlap | Distinct sources/bases retained; combined indicator series blocked |
| HIS-04 | Export supplies close only or monetary turnover without traded quantity | Fields stay limited and correctly unit-tagged; no fabricated OHLC or share volume |
| HIS-05 | Duplicate date, conflicting revision, coverage gap or corporate-action discontinuity | Idempotent identical rows; explicit conflict/gap; affected indicator eligibility follows verified policy |
| HIS-06 | Python/scraper offline; latest independent artifact unavailable | Approved prior imports retained with coverage/staleness shown; other sufficient-data monitor paths continue |
| HIS-07 | History-version rollback after indicator calculation | Prior approved series restored; affected technical caches invalidated; lineage retained; no historical alert replay |
| NOT-01 | Rule transaction fails before commit | Neither rule-state transition nor outbox intent survives |
| NOT-02 | WAHA accepts without delivery evidence | ACCEPTED/unconfirmed; never displayed as DELIVERED |
| NOT-03 | Send times out after fake provider accepts | UNKNOWN_OUTCOME; no blind WhatsApp retry; policy-bounded reconciliation/fallback |
| NOT-04 | Worker crashes immediately after send and before saving response | Expired lease becomes uncertain; does not unconditionally resend |
| NOT-05 | Proven transient rejection / permanent auth failure | Bounded safe retry for transient case; incident/controlled fallback for permanent failure |
| NOT-06 | Duplicate/out-of-order success/failure webhook | One transition; DELIVERED cannot be downgraded; no extra fallback |
| NOT-07 | Two workers/fallback schedulers race | One claimed attempt per lease; at most one scheduled SMTP fallback per logical alert |
| NOT-08 | WhatsApp delivers after fallback is queued / after SMTP accepts | Pending fallback cancelled; already accepted email retained with explicit possible dual delivery |
| NOT-09 | Same rule legitimately rearms then triggers again | New occurrence produces new logical alert; previous occurrence remains deduplicated |
| SEC-01 | Forged transport authentication, unauthorized sender or spoofed display name | Rejected/no command execution/no reply amplification; sanitized security event |
| SEC-02 | Authenticated allowed event replayed many times | One inbox execution and one reply intent; duplicates acknowledged without side effects |
| SEC-03 | Group participant, different session, outbound echo or receipt appears | Correct principal/type filter; no bypass via chat ID or self-message |
| SEC-04 | Command contains shell/URL/path/SQL or unknown/oversized grammar | Bounded parse rejection; no execution or external fetch |
| SEC-05 | App acknowledges webhook then crashes before handling | Durable inbox resumes processing; command side effect and reply remain unique |
| SEC-06 | Secrets/private payload fragments included in error-producing fixture | Logs and admin error views show only safe metadata/reason codes |
| SEC-07 | Phase-7 principal pauses/resumes owned rule after all gates pass | Only permitted enablement state changes; parameters/recipients unchanged; transition and unique reply committed with inbox execution |
| SEC-08 | Pause/resume targets another owner's rule, nonexistent rule, broader mutation or command while feature is disabled | No rule mutation; authorization/feature gate enforced during processing |
| SEC-09 | Pause/resume is replayed or races with dashboard/state transition | One command execution; bounded idempotent state transition under approved concurrency policy; no invented historical alerts or dedup/rearm bypass |

## Suggested phase dependencies and rollback

1. **Evidence and contracts:** Obtain authorized sanitized source documents/audits, approve separate financial-context/history schemas and metric registry, establish scraper cache-only boundary, and verify pinned WAHA event/send semantics. Record uncertainties; no capability is inferred from the brief. Producer mappings remain provisional until the reported documents are available and reviewed.
2. **Core persistence/rules:** Establish stable asset identities and durable rule occurrence IDs. These precede snapshot mapping and reliable alert deduplication.
3. **Notification delivery:** Build/test transactional outbox and fake adapters; then wire verified WAHA/SMTP mappings. Webhook inbox and authentication precede command enablement. Disable dispatch/commands to roll back; retain durable intent/audit and resolve uncertain sends before re-enabling.
4. **Independent COTAHIST history reuse, v1:** Design/implement the staged historical boundary after producer fidelity, identity and adjustment evidence are approved and catalog/persistence exist. Verified historical availability precedes any indicators that require it. Rollback active history selection and affected technical caches without deleting evidence or replaying alerts.
5. **Complementary financial context and scraper fundamentals, v1:** Implement explicit independent financial export imports and the approved cache-only/artifact scraper boundary after evidence/contract approval. Keep both projects operationally independent and unchanged. Rollback adapter activation/context pointers; independently supported core paths continue. This is a v1 priority with capability gates, not wholesale deferral.
6. **Optional future public benchmark work:** Requires a separate asset-versus-benchmark contract and scope approval; do not smuggle portfolio inputs into any v1 artifact.

These are dependency groups, not a mandatory single execution sequence: notification work and the independently gated v1 integrations can proceed in parallel after their shared persistence/catalog foundations. Missing producer evidence blocks final mapping/activation, not the explicit planning of either v1 priority.

## Unresolved questions requiring evidence

- Which exact Python metrics, definitions, classes and statuses did its audit validate? Which results can be independently exported using only public asset inputs?
- What independently generated schema can be supported from existing authorized Python outputs without modifying the legacy application? Does it preserve decimals, adjustment metadata and source observation dates?
- Which official COTAHIST layout/version, numerical units/scales, market/instrument identities, date ranges, field availability and price semantics did the audits verify? What is the validated coverage for the 23 initial catalog assets?
- Can an independent history artifact preserve official source-file identity/digests and support fixture-based fidelity checks without changing or continuously operating the legacy Python project?
- What validation evidence justifies each READY assertion? Do portfolio-specific outputs require private inputs even when only an aggregate result is exported?
- Which WAHA edition/version/engine is intended, and what authenticated webhook mechanism, stable event IDs, idempotency and delivery/status lookup capabilities are actually supported?
- What exactly does each WAHA status prove, and what is the SMTP fallback policy for unknown outcomes? Accepting possible cross-channel duplicate receipt must be explicit in the reviewable policy.
- What recipient/session identities and direct-message principals are authorized? What is the necessary event replay-retention horizon?
- Does the scraper already expose a genuine cache-only, non-refreshing operation or sanctioned export output? If not, which independent boundary satisfies the v1 priority without legacy changes? Keep the answer provisional until evidence exists.
- Which source/methodology-specific age policies and revision/supersession semantics are appropriate for the approved financial metrics?

## Sources

- User-provided **B3 Monitor — Project Discovery and GSD Preparation** brief, supplied in this session on 2026-10-05. Requirements are authoritative; assertions about audited external systems are not independently verified.
- Subsequent user clarification, relayed by the parent agent: official COTAHIST historical reuse and complementary scraper fundamentals are v1 priorities; all three projects remain operationally independent; no legacy modifications. User reports ten copied source documents, while the parent's exact local checks still show them absent.
- Local `README.md`, reviewed 2026-10-05; project name only.
- Expected source documents: unavailable at review time according to the parent's exact local checks. No document contents or audit findings beyond the user's summaries can currently be cited; no filesystem search outside the authorized repository was performed.

No internet, external documentation, sibling repository or live service was accessed. This is bounded local contract/risk research, not a survey of independently verified provider capabilities.
