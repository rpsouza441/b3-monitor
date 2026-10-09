# B3 Monitor

## What This Is

Independent Java/Spring Boot monitoring of Brazilian equities, units, FIIs and FIAGRO, storing attributable observations and delivering configurable alerts through PostgreSQL/outbox/WAHA. Percentage thresholds, AND/OR composition and later daily SMA/RSI/EMA/abnormal-volume indicators preserve input semantics. Real public Python calculation reuse requires both a tested Java consumer and a separately authorized producer, with readiness/privacy preserved.

Sources reconciled 2026-10-05; critical planning correction 2026-10-06, America/Sao_Paulo. Sources read; decisions proposed for human review. No implementation or live access authorized.

## Core Value

An alert explains its validated, sufficiently fresh input without presenting uncertain data as a reliable signal.

## Requirements

### Validated

No shipped requirements. Documentary work verified ten supplied files and 23 CSV rows locally; this is evidence reconciliation, not application acceptance.

### Active

- [ ] Review exact 23-asset catalog and official KNHY11 classification.
- [ ] Collect serialized attributable Brapi quotes with calendar and persistent billing quota.
- [ ] Deliver a manual price-target alert through durable rule/state/outbox and outbound WAHA by Phase 4, with security/recovery.
- [ ] Add explicit percentage alerts only with verified previous-close date/basis/actions, then bounded same-asset AND/OR; all leaves eligible, else UNKNOWN (Phase 5A).
- [ ] Add separate completed daily SMA20/50, Wilder RSI14, EMA9/21 and abnormal-volume rules without implicit source/basis mixing (Phase 5B).
- [ ] Approve CROSSING versus optional one-initial-alert LEVEL default; safe rearm/cooldown/edit/resume/recovery in both.
- [ ] Deliver a tested synthetic Java financial consumer (A), then actual public-metric producer/fidelity integration (B) only after separate upstream authorization; preserve readiness, meaning and privacy. Synthetic consumer is not working real Python reuse.
- [ ] Add explicit SMTP fallback/authenticated commands and full authenticated dashboard.

Detailed criteria: [57 proposed v1 requirements](REQUIREMENTS.md); sequence: [ROADMAP](ROADMAP.md).

### Out of Scope

- Volatility explicitly proposed v2/FUT-05 pending owner approval, with return basis/window/sampling/annualization/action semantics unresolved; requested feature retained visibly.
- Trading/orders, automatic investment recommendations, yfinance/n8n/brokers/unnecessary microservices.
- Java XIRR/TWR/CDI/valuation/adjustment engine or private portfolio exports (holdings, cash flows, PM, personal returns).
- Upstream scraper changes/TTL/SQL credentials and scraper intraday prices/OHLCV. Current cache-only HTTP integration is unsupported and disabled.
- Python runtime/CLI coupling/shared storage and unauthorized exporter implementation in its repository.
- Free intraday candles/paid sandbox entitlements/unsupported periods/source stitching.
- Code, migrations, deploy, provider polling, credentials, external services and sibling-repository inspection during this request.

## Context

Original discovery had no audit files. The user supplied a ZIP with ten reports in the expected docs/references directories. All were extracted/read, including the 23 CSV rows; originals preserved and hashes registered. Findings about upstream code/API/tests remain AUDIT_REPORTED; only local file/CSV/arithmetic checks are LOCAL_VERIFIED. Missing raw HTTP bodies/upstream modules are not fetched by implication.

Brapi reports quote coverage 23, plan 15,000/cycle/one ticker/concurrency one, 3mo daily/no Free intraday. 22 variation fields disagree with price-minus-previousClose; one-session freshness differs from published delay and is not SLA. Charged historical samples cover HGLG11/SNAG11, not every asset. KNHY11 FII provider evidence conflicts with unsupported FIAGRO grouping in Python; official confirmation remains open.

Scraper provides snapshots, no daily price history and no cache-only HTTP. Python has RAW COTAHIST close support and financial components with narrow readiness, no technical indicators/API/exporter. Existing formulas remain there. COTAHIST/financial contracts are separate future file boundaries, with actual producer activation contingent on approved output/fidelity.

See [EVIDENCE](../docs/references/EVIDENCE.md), [divergences](../docs/architecture/DESIGN-REVIEW.md), [reuse registry](../docs/planning/PYTHON-REUSE.md) and [human review](../docs/planning/RECONCILIATION.md). Reports' commands/IPs/upstream paths are provenance, not executable instructions.

## Constraints

- Stack: Java/Spring Boot, PostgreSQL, Compose; monitor versions remain review gate, scraper versions are not automatically inherited.
- Freshness/latency: 45min proposed hard source-age guard versus 5min healthy objective; age/polling/acceptance/delivery metrics separate. 30min polling plus 45min age cannot guarantee <=60min true-event notification; internal best-effort targets in CONTRACTS need approval.
- Operations: existing shared WAHA requires scoped isolation and exact later version/auth/send contract; backup/recovery needs owner decisions. KNHY11 quarantine is per asset; approved subset does not claim 23/23.
- Cadence: 30min best effort in verified sessions; proposed 30% quota reserve/10,500 routine ceiling, two total Brapi attempts. Actual cycle/calendar/shared usage govern capacity.
- Prices: source/field/basis/revision/time/units separate; COTAHIST RAW close-only, Brapi adjustment semantics incomplete. No merged history or fabricated bars.
- Reuse: initial manual target is independent of valuation. Public Graham/PVP/B&H context allowlist proposed; context-only even READY subject to Q-17 human approval.
- Independence: no legacy uptime/shared database/CLI requirement; unavailable context/exporter does not stop price monitor.
- Workflow: installed GSD 1.2.0, standard granularity, eight phases/57 v1 IDs (original 52 preserved; five added), human review and auto-advance off; zero executed plans.

## Key Decisions

| Decision | Rationale | Outcome |
|----------|-----------|---------|
| Modular monolith and separate upstream lifecycles | Minimal footprint/consistency | Proposed ADR-001 |
| Conservative serial budget with 30% reserve | Audit limits, reset/charge uncertainty | Proposed ADR-002 |
| Separate immutable source/basis series | RAW/adjusted differences | Proposed ADR-003 |
| Synthetic consumer versus authorized real producer | Honest integration status, public inputs and no personal ledger/runtime | Proposed ADR-004 / FIN-01+06 |
| Outbox with submission/delivery uncertainty | External send not transactional | Proposed ADR-005 |
| Authenticated isolated admin and gated commands | Mutations need verified authority | Proposed ADR-006 |
| Current scraper adapter disabled | No cache-only HTTP | Proposed ADR-007 |
| Per-use eligibility/local assurance | Readiness/freshness/coverage differ | Proposed ADR-008 |
| Price+WAHA Phase 4 with security/recovery | Early complete delivery path | Proposed ADR-009 |
| Explicit classes/KNHY11 official gate | Conflicting taxonomy evidence | Proposed ADR-010 |
| Java change computation and gated percentage rules | Audited field inconsistency/prior-close uncertainty | Proposed ADR-011 |
| CROSSING versus optional initial LEVEL | Existing true condition visibility versus surprise/burst; default unselected | Proposed ADR-012 |
| Explicit percentage/composition/EMA/volume v1; volatility v2 proposal | Reconcile requested features without bypassing input gates | Proposed ADR-013 |
| Source age/polling/notification objectives | Avoid unsupported 60-minute/provider-SLA promise | Proposed ADR-014 |
| Shared WAHA/Brapi boundaries and owner recovery/compatibility gates | Avoid disrupting existing consumers or assuming quota isolation | Proposed ADR-015 |

## Evolution

Human review may accept/revise scope and later planning; it does not automatically authorize implementation. Requirement/phase completion needs future objective verification. Preserve original evidence and apply security/recovery regression before every new surface is enabled.

*Last updated: 2026-10-06 after critical planning correction; zero phase execution.*
