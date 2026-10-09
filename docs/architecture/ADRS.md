# Architecture decision records

Status of ADR-001–015 (critical revision 2026-10-06): **PROPOSED — SOURCES RECONCILED, HUMAN APPROVAL PENDING**. Audit claims use AUDIT_REPORTED; no upstream runtime verified. Stack/independence/access limits are user instructions; proposals below remain reviewable. [Evidence](../references/EVIDENCE.md), [divergences](DESIGN-REVIEW.md), [approval packet](../planning/RECONCILIATION.md).

## ADR-001 — Modular Java monitor

**Context:** B1 quotes, S3 KEEP SEPARATE and P3 INDEPENDENT support distinct responsibilities and lifecycle.
**Proposal:** One Spring Boot modular application, PostgreSQL and Docker Compose, outbound WAHA first; later SMTP. No broker/n8n/trading/Python sidecar/shared legacy storage.
**Consequences:** Rule/state/outbox atomic boundary; upstream downtime cannot stop price monitoring. Separate releases/formulas/storage remain with their owners. Versions still need review (Q-14).

## ADR-002 — Budgeted serialized provider access

**Context:** B1/B3/B4 report 15,000/billing-cycle, one ticker/request/concurrency one; sandbox 20/60 separate; 40 HTTP yielded 25 debits. Reset is delta seconds; cycle start/shared usage absent. Old draft 20% reserve/three attempts differed from B3 recommendation.
**Proposal:** Persistent reservations and conservative attempt accounting, proposed 30% protected reserve/10,500 routine ceiling. Brapi at most two total attempts (initial+one safe repeat), only timeout/429/500/503 within quota/deadline/backoff; parameter/plan errors not retried. Forecast includes history/backfill/manual/shared usage; scraper Brapi classification/retries make sharing possible but account/token topology is UNKNOWN. Owner must provide dedicated account allowance or enforceable shared allocation/reservation/global concurrency coordination; a separate key alone is insufficient. 5% contingency is sizing, not permission to retry.
**Consequences:** Sample Date+delta reproduces 2026-11-03 18:07:54 BRT but is not future configuration. 8h/23-session scenario costs 9,466 with 1,034 routine headroom; real calendar/cycle/token allocation gates activation. Never refund ambiguous dispatch or silently borrow reserve.

## ADR-003 — Immutable, separate price series

**Context:** B1/B4 adjustedClose differs from close; P2 §5 COTAHIST 245/010/PREULT/100 is RAW, schema READY/security PARTIAL, adjusted NOT_READY. Python close support does not prove full OHLCV exporter.
**Proposal:** Separate source/field/basis/method/revision series: COTAHIST RAW close-only, Brapi adjustedClose vendor-adjusted with incomplete methodology, Brapi close UNKNOWN until its basis verified. No automatic concatenation/coalesce/adjustment engine. Choose one eligible series for each indicator. Current daily bar provisional, finality needs explicit assurance policy.
**Consequences:** HIS-04 independently validates proposed close export with bounded atomic lifecycle; actual exporter absent. Adjusted SMA versus observed quote cannot activate without comparable basis. Raw windows across unresolved events are ineligible; equal present prices do not establish compatible history. Optional COTAHIST unavailability does not block Phase 4 or another separately eligible history source.

## ADR-004 — Independent read-only financial snapshots

**Context:** P1/P2/P3 enumerate implemented formulas with narrow readiness; no API/package/exporter and private portfolio inputs. Formula readiness does not establish export readiness.
**Proposal:** Deliverable A/FIN-01 is a versioned/tested Java consumer, CONSUMER_VERIFIED_SYNTHETIC when fixtures pass. Deliverable B/FIN-06 is an actual Python exporter blocked until separate upstream authorization; real public-input canonical-calculation goldens/fidelity and Java import are required for INTEGRATION_VERIFIED_REAL. Full Phase 7/v1 real reuse cannot be completed by synthetic DTOs; only formal human scope revision can change that gate. Proposed versioned asset-only JSON, registry in [PYTHON-REUSE](../planning/PYTHON-REUSE.md). Initial numeric allowlist exactly Graham PARTIAL (BRL/security), P/VP snapshot READY/history PARTIAL (ratio), B&H PARTIAL (0–100, separate coverage), with public-input lineage only. Other numerical fundamentals need explicit registry extension. Bazin/common-core FII only state/limitation with no usable value. Preserve per-metric units/as-of/source/quality/coverage/version/price semantics/local assurance.
**Consequences:** Financial context excluded from automatic rules even READY (Q-17 human scope approval). XIRR/TWR personal, CDI tied to flows, holdings/PM/custody excluded even when “per asset”. Java does not port finance formulas or call Python; producing/fidelity-testing real export needs separate upstream authorization. COTAHIST bars use separate history contract and eligibility.

## ADR-005 — Transactional outbox and explicit uncertainty

**Context:** No supplied audit establishes WAHA edition/version/send/auth/receipts; external send is outside PostgreSQL transaction.
**Proposal:** Commit evaluation/state/logical event/outbox atomically; lease/fence attempts after commit. Submission ACCEPTED distinct from delivery UNKNOWN/CONFIRMED; ambiguous execution UNKNOWN_OUTCOME. No blind resend/fallback on lost receipt/lease/crash. Only verified contract evidence can confirm or permit idempotent retry.
**Consequences:** Logical dedup, not promised external exactly-once. Early outbound contract Q-09 required for Phase 4 activation; fake adapter verification remains possible. SMTP arrives Phase 6 with explicit late-success/duplicate policy Q-10. Recipient acceptance never substitutes for delivery evidence.

## ADR-006 — Authenticated, isolated administration

**Context:** Early target/recipient/pause configuration and later imports/commands mutate monitoring.
**Proposal:** Minimal private authenticated admin initially; audited revisions/CSRF/session gates, isolated monitor PostgreSQL and scoped access to the existing shared WAHA instance, with credentials/session/routes/network/rate/pause isolation; monitor Compose does not manage/reset shared service or other consumers, externally injected secrets/redacted logs. Later webhook verifies transport and independently trusted session/sender/action/ownership with durable replay protection.
**Consequences:** One-admin/private model subject to Q-11; unsupported commands disabled. Phase 4 security/recovery controls apply again before later surfaces enable; no public ingress or credentials created during planning.

## ADR-007 — Complementary scraper disabled under current contract

**Context:** S1/S2/S3 establish no cache-only HTTP operation. GET/raw refreshes on miss/24h expiry; stock as-of may be response time, nulls become zero and FII can retain old value under new stamp.
**Proposal:** Keep optional fundamentals capability specified but current adapter disabled (FIN-05). No HTTP ticker/raw calls, SQL/shared database/credentials workaround, TTL/mapper changes or scraper-price history/quote promotion.
**Consequences:** Unsupported status is now evidence-backed rather than UNKNOWN. Future sanctioned artifact/non-refreshing operation needs separate scope/provenance/units review. Monitor is independent; report recommendations for new endpoint/SQL do not authorize those changes.

## ADR-008 — Per-use evidence before signal eligibility

**Context:** One-session 27–209s quote age vs ~30min published delay/30s header; stale header with ordinary age; implementation/readiness/date/coverage are distinct claims. Upstream Python full suite had setup errors.
**Proposal:** Independent readiness/quality/age/coverage/basis/local-assurance dimensions and per-use gates. Preserve source/server/receipt clocks and stale flag; proposed default x-brapi-stale=1 blocks signals pending approved contract/policy. Unknown affected input suppresses trigger/rearm; no parent status promotion.
**Consequences:** No delay SLA/test assurance inferred from reports. Absolute price may be eligible while prior-close change/history comparison is unavailable. Event-affected manual targets need review/new baseline; no automatic rescaling or Java adjustment engine. 45min/2min/rearm defaults require approval (Q-12).

## ADR-009 — Deliver a vertical price monitor first

**Context:** Old rules/messages phases 6/7 followed history/context, though B1 supports independent threshold-price monitoring and neither upstream supplies alert engine/WAHA.
**Proposal:** Phase 4 completes Brapi→validated price→manual BRL target→PostgreSQL episode/outbox→outbound WAHA. Preserve all original 52 IDs/eight phases; five explicit additions bring v1 to 57. Phase 5A percentage/AND/OR follows validated inputs independently of history; 5B history/SMA/RSI/EMA/volume, 6 SMTP/commands, 7A consumer/7B authorization-blocked real producer, 8 full UI. Volatility is an explicit v2 proposal pending approval. Bring OPS-01–04 and SEC-03 into initial-release gate and repeat for added surfaces.
**Consequences:** Minimal authenticated configuration/status/pause exists early; complete dashboard later. No indicator/exporter/scraper dependency for initial price alerts. Fewer features initially, same safety/integrity obligations. Separate human approval Q-18 and later implementation/live-activation authorization still required.

## ADR-010 — Explicit instrument types and KNHY11 quarantine

**Context:** B2/B4 show 6 stocks/3 units/13 FIIs/1 FIAGRO; P1 §2 incorrectly or insufficiently groups KNHY11 as FIAGRO without ticker evidence. Python ACAO/FII are broader canonical classes.
**Proposal:** [Catalog](../planning/ASSET-CATALOG.md) preserves provider types and explicit producer→monitor mapping. KNHY11 FII is provisional using provider subtype/name; no FIAGRO promotion. Seek sanitized official identity/category evidence locally (Q-15), no external calls under present scope.
**Consequences:** Exact ticker list gap closed, official KNHY11 decision open. Asset stays QUARANTINED_CLASSIFICATION_PENDING until resolved; others reviewed independently. A named approved subset can satisfy downstream identity gates only by explicit human dependency/scope approval; do not mark full CAT-01/Phase 1 complete or claim 23/23. No claim of complete 23-asset activation, suffix inference or invented ISIN/CNPJ/date mapping.

## ADR-011 — Recalculate changes in Java with compatible operands

**Context:** B2 arithmetic reproduces 22 absolute mismatches above R$0.02 and 7 internal-percent mismatches at 0.15pp. BPAC11 price 82.92/previous 82.93 versus provider +16.9/+25.6%.
**Proposal:** Decimal delta=price−previousClose, percent=100×delta/previousClose. Store operands, formula/policy and source field diagnostics. Validate identity/BRL, positive denominator, prior trading date, basis/actions; unknown semantics yields unavailable with reason, no fabricated previous date or cross-source replacement.
**Consequences:** Example computed −0.01 BRL/~−0.012058% proves arithmetic only. SCHEMA calling previousClose safe does not prove dated action compatibility. Absolute-price MVP need not wait for change eligibility; explicit RUL-06 percentage alert ownership is Phase 5A, gated by verified previous-close date/basis/actions and Phase 4 state/outbox; no activation follows merely from correct arithmetic.

## ADR-012 — CROSSING and optional one-time initial LEVEL

**Context:** Original BASELINING proposal silently suppressed an already-true creation/edit/resume. This misses an existing condition the operator may expect to hear about; immediate initial alerts instead risk bursts and edit/resume abuse.
**Proposal:** Phase 4 implements both CROSSING (eligible observed FALSE→TRUE only) and LEVEL (optional one initial TRUE episode, then same rearm as CROSSING). Persist initial-consumption/lifecycle/episode/latch/false-confirmation/cooldown; atomic logical event/outbox. Explicit per-create/edit/resume opt-ins; ordinary resume/restart never replays a latched episode. Edit changes revision but preserves cooldown/rate controls; restore uncertainty quarantines. UNKNOWN/gaps/corrections cannot mint initial tokens.
**Consequences:** Default **UNSELECTED** until owner approves default/per-rule mode and initial policy (Q-19). Both modes miss excursions between polls; LEVEL is not repeated periodic level spam. [CONTRACTS](../contracts/CONTRACTS.md) defines exact transitions, initial token when first eligible FALSE, rearm and dispatch expiry.

## ADR-013 — Explicit full rule scope and indicator allocation

**Context:** Original user requested absolute/percentage thresholds, technical indicators and combined conditions. Prior v1 had only absolute, SMA/RSI with no explicit percent or AND/OR deliverable.
**Proposal:** RUL-06/07 Phase 5A: decimal percent with proven previous-close date/basis/actions; bounded same-asset AND/OR (8 leaves/depth4 proposed) with all inputs eligible or root UNKNOWN, coherent cutoff/timeframes/skew and all-leaf lineage. Independent from history. Phase 5B IND-04/05: daily EMA9/21 stable SMA seed/recurrence and finalized Vt/previous20-mean volume with known units. Keep Phase 4 absolute only. Volatility explicitly proposed v2/FUT-05 pending owner approval.
**Consequences:** EMA/volume add requested trend/activity without inventing Python indicators; library/history/unit/finality assurance still mandatory. Volatility needs return basis/window/frequency/annualization/actions and can neither be replaced by RSI nor omitted without an owner decision (Q-22). Full composition acceptance includes both lifecycle modes and whole-expression rearm; conservative UNKNOWN-for-OR proposal is Q-21.

## ADR-014 — Distinct age, cadence and notification objectives

**Context:** Source age <=45min plus 30min polling cannot guarantee real-event-to-notification <=60min; conservative age+next-poll budget is up to75min before queue/transport, and missed excursions/provider visibility have no bound.
**Proposal:** Separate source/server/receipt/eval/commit/attempt/acceptance/confirmed clocks and poll lateness/skips. Keep proposed45min hard guard distinct from healthy5min age target. Proposed targets:95% age<=5min,99% admitted slots<=1min late,95% eligible intents commit→ACCEPTED<=2min and source→ACCEPTED<=10min. Denominators include failed/unknown/expired intents; report all due slots, suppressions, degraded states and insufficient samples.
**Consequences:** Internal best-effort Q-24 objectives, not provider or recipient-delivery SLAs. Objective breach degrades; hard unknown/stale/basis/calendar/expiry failure suppresses trigger/rearm/send. Owner chooses freshness/coverage trade-off or stronger validated service requirements; no silent45min relaxation or fabricated latency.

## ADR-015 — Shared operational boundaries and explicit owner gates

**Context:** User identifies existing shared WAHA. Audits do not prove its edition/version/auth/send topology. S1 scraper calls Brapi for classification with retries; account quota sharing possible, not established. Backup/version choices remain open.
**Proposal:** SEC-02/NOT-02 gate scoped WAHA session/credentials/routes/network/rate/pause and later exact outbound adapter contract, fake adapter first. No global start/stop/logout/reset/config of unrelated consumers. QUO-02 needs owner-confirmed account allocation or enforceable coordination with scraper; monitor-local serial dispatch cannot enforce global sharing. KNHY11 quarantine independent. OPS-02 requires owner-approved monitor-only backup destination/encryption/key custody/retention/RPO/RTO/restore authority. SYS-01 requires pinned JDK/Boot/build/PostgreSQL/library/license/API and numerical golden compatibility.
**Consequences:** Insufficient isolation/unknown budget blocks only relevant live admission; shared service changes/separate infrastructure require separate authorization. Scraper Java21/Boot3.5.3 is provenance, not an approved stack. No external services, credentials, dependency installation or sibling inspection in this task (Q-13/14/25–27).
