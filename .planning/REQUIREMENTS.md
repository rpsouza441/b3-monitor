# Requirements: B3 Monitor

**Defined:** 2026-10-05; **critical correction:** 2026-10-06. **Status:** 57 proposed v1 IDs, all Pending; human approval outstanding. **Core Value:** Explainable alerts from eligible data.

Each ID has one owning phase. Cross-phase references consume that requirement, without a second owner. Phase 4 stays absolute-price-only with approved CROSSING/LEVEL policy; Phase 5 adds independently gated percentages/composition and daily technicals. Phase 7 distinguishes synthetic Java consumer from authorization-blocked real Python producer. Operations/security apply before each surface enables and are repeated at full-v1 review. No acceptance tests have been executed against an application.

## v1 Requirements

### Evidence and asset catalog (Phase 1)

- [ ] **GOV-01**: Reconcile all three sanitized audits into a cited evidence register. Acceptance: missing findings remain UNKNOWN; each accepted capability has report path/section and review status.
- [ ] **GOV-02**: Maintain specification, ADRs, risk review and phase gates under installed GSD. Acceptance: every v1 ID has one phase and one objective criterion; automation cannot bypass human review.
- [ ] **CAT-01**: Maintain all 23 documentary identities with per-asset activation status. Acceptance: validated unique/datable identity/type mappings activate independently; KNHY11 remains QUARANTINED_CLASSIFICATION_PENDING until official evidence, without blocking other eligible assets. Full 23-active coverage remains unfulfilled until every asset is approved; no suffix/FIAGRO inference.
- [ ] **CAT-02**: Preserve identity changes and historical mappings. Acceptance: synthetic rename, delisting and ticker reuse do not overwrite old observations or mix instruments.

### Foundation and security (Phase 2)

- [ ] **SYS-01**: Use a Java/Spring Boot modular monolith and PostgreSQL with a verified compatibility matrix. Acceptance: pin JDK/Boot/build/PostgreSQL and chosen indicator-library versions/licenses; verify decimal behavior, SMA/RSI/EMA seeding and abnormal-volume units/gap/correction conventions against independent golden fixtures and documented API compatibility before technical activation; no inherited scraper-version/library assumption, broker or Python runtime dependency.
- [ ] **SYS-02**: Persist observations, policy revisions and audit events durably. Acceptance: restart preserves provenance, rule state and quota reservations; duplicate logical identities are rejected.
- [ ] **SYS-03**: Deploy the eventual system through Docker Compose. Acceptance: synthetic local composition health/start/restart checks pass; no production contact or unrequested services.
- [ ] **SEC-01**: Authenticate administration and authorize mutations. Acceptance: unauthenticated/non-admin requests fail; CSRF and session expiry are tested using synthetic identities.
- [ ] **SEC-02**: Isolate monitor storage and outbound WAHA access, including an existing shared instance, and inject secrets outside source control. Acceptance: scoped session/credentials/recipient routes, network boundary, monitor-owned rate/attempt budget and pause controls are documented/tested; no start/stop/logout/session-reset/config mutation of unrelated WAHA consumers; impossible credential/session isolation requires owner-approved separate boundary before activation.

### Market collection and calendar (Phase 3)

- [ ] **MKT-01**: Poll one ticker at a time on 30-minute eligible slots for the approved asset subset. Acceptance: maximum concurrency 1 in synthetic 23-asset capacity case, quarantined KNHY11 excluded from live admission, no unknown/off-session calls; record scheduled/actual slot time, lateness/skips and per-asset intervals; proposed healthy-operation objective 99% admitted due slots start within 1 minute, never a provider SLA or catch-up entitlement.
- [ ] **MKT-02**: Preserve provider provenance, source/receipt timestamps and trading date. Acceptance: v2 results/data mapping preserves original ISO regularMarketTime and requestedAt, local receipt time and calendar derivation; provider time is not proven last-trade time; missing/malformed/future timestamps retain explicit reasons.
- [ ] **MKT-03**: Validate quote identity, fields and age separately from polling/notification latency. Acceptance: source_age=evaluationAt-sourceTime is checked per use and again before dispatch; unknown/future/stale/over-approved-age input cannot trigger/rearm/send; x-brapi-stale=1 suppresses absent approved exception; proposed 5-minute healthy age objective versus 45-minute hard age ceiling are different policies. Age+poll+queue makes a 60-minute true-event notification guarantee invalid; observe degraded/unknown/suppressed reasons.
- [ ] **MKT-04**: Calculate percentage change locally from compatible price/previous close. Acceptance: 100*(price-previousClose)/previousClose uses stored decimal operands and verified prior date/basis/actions; provider change fields remain diagnostic; BPAC11 audit arithmetic is approximately -0.012058%, not +25.6%; unknown previous-close semantics or split yields unavailable change without fabricated dates.
- [ ] **MKT-05**: Keep collection idempotent and fair under backlog. Acceptance: repeated slot cannot duplicate work; delayed slots are skipped visibly instead of burst replay; asset priority/rotation is explicit.
- [ ] **MKT-06**: Surface provider degradation without fallback fabrication. Acceptance: 401/403 disables collection, plan/parameter 400 does not retry, 429 respects verified backoff, timeout/500/503 may get at most one safe repeat subject to quota/deadline; unmeasured error paths remain explicit simulations and all outcomes attributable.
- [ ] **QUO-01**: Reserve and account for every provider attempt durably. Acceptance: timeout, retry, crash and competing workers cannot exceed configured cycle ceiling or concurrency; ambiguous consumption counts conservatively.
- [ ] **QUO-02**: Protect reserve and forecast total actual billing consumption across all token consumers. Acceptance: proposed monitor allocation never exceeds verified account remaining minus protected reserve/external allocation; ticker-scraper classification may call Brapi with its own retries, but token sharing is UNKNOWN. Dedicated token/account allowance or owner-provided shared ledger/reservation/concurrency coordination is mandatory; monitor-local concurrency alone cannot enforce a shared account ceiling. Unknown allocation freezes admission.
- [ ] **CAL-01**: Use a versioned per-instrument/session calendar. Acceptance: holiday, special session, closed day and unknown calendar fixtures suppress unauthorized collection; UTC/market-date boundary tests pass.
- [ ] **CAL-02**: Apply corporate-action compatibility gates. Acceptance: synthetic split, distribution and unknown event suspend incompatible change/indicator/rule comparisons until reviewed.

### Minimum price alerts, WAHA and early operations (Phase 4)

- [ ] **RUL-01**: Configure a bounded typed rule registry and initial manual absolute-price conditions. Acceptance: Phase 4 enables BRL price above/below a user target with immutable revision/units/eligibility; rejects code and financial operands; rejects percentage/composition/technical activation until Phase 5 RUL-06/07 and IND-03–05 verify their respective semantics.
- [ ] **RUL-02**: Evaluate only eligible, time-compatible inputs. Acceptance: stale/unknown/missing/future/corporate-action inputs cause UNKNOWN evaluation and cannot trigger/rearm.
- [ ] **RUL-03**: Persist CROSSING and LEVEL lifecycle, dedup, rearm and cooldown. Acceptance: approved mode/initial policy is mandatory; CROSSING requires eligible observed false-to-true continuity and never initial-true alert; LEVEL may emit one approved initial-true episode atomically. Both persist initial-consumed/lifecycle/episode IDs, require configured distinct eligible false confirmations/hysteresis, and do not repeat on identical samples, long gaps, cooldown expiry or restart; unselected default disables activation.
- [ ] **RUL-04**: Commit evaluation, state transition and outbox atomically. Acceptance: crash at any transaction boundary produces either no transition or one logical alert with matching state.
- [ ] **RUL-05**: Explain and safely revise/pause/resume rules under the approved initial policy. Acceptance: edits create immutable revision and clear comparison history; only explicit approved LEVEL-on-edit may issue one new initial check, without bypassing rule/recipient cooldown/rate limits. Ordinary resume retains initial/episode consumption and cannot replay prior true episodes; optional approved LEVEL-on-resume requires explicit lifecycle reinitialization and an ended prior episode. Restore preserves state or quarantines uncertainty rather than treating recovery as creation.
- [ ] **NOT-01**: Deliver with persistent bounded outbox attempts. Acceptance: claims/leases recover after worker crash and duplicate logical alert keys stay unique; external exactly-once delivery is never promised.
- [ ] **NOT-02**: Distinguish WAHA handoff, confirmation and uncertainty using the later verified outbound contract. Acceptance: owner-supplied edition/version/engine/send/auth/session/receipt/idempotency semantics are mapped offline before live activation; fake acceptance verifies adapter state handling only. Shared-instance/session isolation remains a separate gate; ACCEPTED does not mean delivery, ambiguous execution is UNKNOWN_OUTCOME and unsupported receipts remain UNKNOWN.
- [ ] **NOT-04**: Prevent notification storms and allow controlled recovery. Acceptance: per-recipient rate limits, expiration, dead-letter visibility and audited retry operate on synthetic messages.
- [ ] **NOT-05**: Version and redact message content. Acceptance: alert includes source/as-of/quality/condition and event ID while excluding credentials/private portfolio data.
- [ ] **OPS-01**: Expose per-asset freshness and best-effort collection/notification objectives, not a 60-minute guarantee. Acceptance: metrics cover source/server/receipt/evaluation/commit/attempt/accepted/confirmed times, slot lateness/skips, source age p50/p95/p99, stale/unknown/eligibility reasons, provider failures/quota, outbox age and accepted/confirmed delay separately. Proposed healthy targets: 95% quote ages <=5min, 99% due admitted slots <=1min late, 95% eligible alert commits-to-ACCEPTED <=2min and source-to-ACCEPTED <=10min; denominator/exclusions and degraded/suppressed counters are explicit.
- [ ] **OPS-02**: Back up and restore monitor database/policies within owner-approved storage, retention, encryption, RPO/RTO and restore authority. Acceptance: draft daily/RPO24h/RTO4h values remain unapproved; synthetic isolated restore tests approved values, immutable evidence and durable initial/episode/cooldown state; workers stay disabled until quota/delivery reconciliation. Monitor backup/restore never resets a shared WAHA instance or other consumers.
- [ ] **OPS-03**: Reconcile quota and delivery after restore. Acceptance: pre-restore ledger high-water data prevents undercount; restored outbox cannot resend blindly; unresolved consumption/delivery stays quarantined.
- [ ] **OPS-04**: Document recovery, retention and per-delivery release gates. Acceptance: Phase 4 quote-to-price-rule-to-simulated-WAHA end-to-end/restart/outage/restore checks pass; later surfaces repeat applicable gates before enablement and full v1 review; lineage is never silently destroyed.
- [ ] **SEC-03**: Complete security review for every enabled runtime surface before activation. Acceptance: Phase 4 admin/session/CSRF, catalog/config parsing, isolated provider/WAHA, redaction and encrypted backups pass; disabled financial imports/webhooks are unreachable; history/financial parsing, webhook replay and complete UI repeat required checks before later enablement.

### Percentage, combined and technical conditions (Phase 5)

- [ ] **HIS-01**: Store daily market data as separate attributable series with explicit field availability. Acceptance: OHLCV validates ordering/units/volume when supplied; audited COTAHIST close-only history leaves unsupported OHLC/volume absent and cannot masquerade as candles; source/basis/revision/date and deterministic duplicates persist.
- [ ] **HIS-02**: Evaluate only completed, sufficiently verified daily bars. Acceptance: today's provisional bar, missing sessions and unresolved finality never silently enter indicators.
- [ ] **HIS-03**: Prevent implicit concatenation across source or adjustment basis. Acceptance: RAW/ADJUSTED/UNKNOWN series remain separate; corrections invalidate derived inputs with a lineage record.
- [ ] **HIS-04**: Specify independent official COTAHIST historical reuse through the Python boundary and safe consumer lifecycle. Acceptance: audit-reported 245-character layout, market 010, PREULT/100, RAW close, annual schema READY/security PARTIAL and missing adjusted/export capability map explicitly; synthetic bounded whole-bundle validation, ID/digest repeats/conflicts, atomic activation, corrections/rollback pass; actual series stays disabled until producer fidelity/coverage is proven, without duplicate parser or upstream changes.
- [ ] **IND-01**: Calculate SMA20 and SMA50 from eligible daily closes. Acceptance: independent synthetic golden fixtures and missing-bar/warmup gates pass with documented decimal precision.
- [ ] **IND-02**: Calculate RSI14 using specified Wilder seeding. Acceptance: golden fixtures, 15-close minimum seed, no-loss/no-gain/flat cases and subsequent recurrence pass.
- [ ] **IND-03**: Version indicator readiness and gate technical-rule activation. Acceptance: insufficient/incompatible history returns NOT_READY with required/actual bars, unsupported periods reject and Wilder seed never silently rolls; Phase 5 activates daily SMA/RSI/EMA/abnormal-volume leaves and sampled quote/SMA or quote/EMA comparisons through the Phase 4 typed rule engine only with verified timeframe/basis/age/sample-gap/equality and revised-baseline tests.

- [ ] **RUL-06**: Deliver percentage-change threshold alerts after validated inputs, in Phase 5. Acceptance: signed decimal percentage-above/below conditions use MKT-04 lineage with verified previous-close trading date, compatible price basis and corporate actions; missing proof disables this rule independently of absolute rules. Golden threshold/equality/invalid-denominator/event/date-rollover/correction fixtures and CROSSING/LEVEL lifecycle/outbox tests pass; raw provider change fields cannot serve as operands.

- [ ] **RUL-07**: Deliver bounded AND/OR condition composition in Phase 5. Acceptance: explicit same-asset AST, proposed max 8 leaves/depth 4, typed operands and parentheses/canonical revision; all referenced capabilities must be validated before enablement. At runtime any ineligible/UNKNOWN leaf makes the whole expression UNKNOWN, even an otherwise true OR branch; eligible TRUE/FALSE leaves follow AND/OR truth tables. Coherent cutoff, timeframes/bases/ages/skew, complete leaf lineage, whole-expression dedup/rearm/cooldown and short-circuit-bypass/edit/restart fixtures pass.

- [ ] **IND-04**: Deliver daily EMA9 and EMA21 in v1 Phase 5, subject to source/library compatibility. Acceptance: version alpha=2/(N+1), seed SMA of first N eligible completed closes, stable replayed seed lineage and reviewed warmup/convergence; recursive golden/flat/gap/correction fixtures pass. Short-history result is labeled LIMITED_HISTORY, not equivalent to long seeded series; no silent rolling reseed. EMA9 versus EMA21 uses same-date/source/basis outputs and ordered eligible pairs for observed crossing. Eligible daily EMA level/crossing operands activate only through IND-03/RUL-07 gates.

- [ ] **IND-05**: Deliver completed-daily abnormal-volume comparison in v1 Phase 5. Acceptance: configurable threshold on ratio V[t]/mean(V[t-20..t-1]) uses 21 completed daily bars, same source/explicit volume units and coverage, excludes current volume from denominator and suppresses missing/unit-conflicted/nonpositive denominator. Golden/window/partial-session fixtures pass; partial intraday cumulative volume is never compared to completed full days as a valid signal. No COTAHIST close-only volume inference.

### SMTP and inbound commands (Phase 6)

- [ ] **NOT-03**: Provide SMTP fallback with explicit routing policy. Acceptance: definite failure and uncertain WAHA outcomes follow separately reviewed rules; uncertainty cannot cause automatic repeated sends or hidden duplicates.
- [ ] **CMD-01**: Authenticate webhook transport and separately authorize principals/actions. Acceptance: bad credentials, disallowed sender/session, forged sender and oversized messages produce no commands.
- [ ] **CMD-02**: Make webhook handling replay-resistant and durably acknowledged. Acceptance: duplicate/out-of-order/stale events cannot repeat a mutation; response acknowledges durable receipt rather than execution success.
- [ ] **CMD-03**: Support only status, quote and pause/resume-existing-rule commands. Acceptance: strict grammar and ownership checks pass; no arbitrary SQL/shell/expression, trading or recipient/admin changes through messages.

### Financial consumer and authorized producer (Phase 7)

- [ ] **FIN-01**: Deliver A: a versioned tested Java public-asset snapshot consumer. Acceptance: schema/registry/privacy/units/as-of/readiness/idempotency/atomic-activation tests use synthetic fixtures; its status is CONSUMER_VERIFIED_SYNTHETIC, never real Python integration. No financial formulas, CLI/shared storage or sibling access. FIN-06 separately owns producer and real-result fidelity; absent producer keeps actual integration unavailable.
- [ ] **FIN-02**: Preserve per-metric readiness, quality, coverage, units and time semantics from the approved reuse registry. Acceptance: Graham PARTIAL, FII P/VP snapshot READY/history PARTIAL and B&H PARTIAL keep exact scopes; Bazin/quality FII state-only records have no usable value; unknown/company_score contracts and unsupported schema fail closed, without parent-to-child promotion.
- [ ] **FIN-03**: Quarantine unsafe/invalid imports and ensure idempotency. Acceptance: oversized input, wrong asset/unit, incompatible schema, conflicting IDs and malformed values cannot partially promote an export; repeats are no-ops.
- [ ] **FIN-04**: Keep all imported financial metrics contextual in v1, explicitly subject to human scope approval. Acceptance: financial namespace cannot enter rules even for READY values; per-asset XIRR still counts as private portfolio output and is rejected along with holdings, cash flows, cost basis and identifiers; partial/stale labels persist.
- [ ] **FIN-05**: Document the optional scraper fundamentals boundary and disable unsupported integration. Acceptance: audit-reported absence of cache-only HTTP and GET/raw refresh-on-read is recorded; current adapter stays disabled, with no TTL/source edits, SQL/credential workaround or cached-price promotion; a future independent artifact/operation needs separate provenance review.

- [ ] **FIN-06**: Deliver B: actual validated public-asset Python producer/exporter and real calculation reuse, conditionally in v1 Phase 7. Acceptance: separate explicit upstream authorization is recorded before producer work; canonical projecao-carteira calculations generate versioned asset-only artifacts from public allowlisted inputs without reading/exporting private portfolio files; approved calculation/readiness/units/as-of/lineage and sanitized producer golden/fidelity evidence survive Java import. INTEGRATION_VERIFIED_REAL requires both FIN-01 and this evidence. Status stays BLOCKED_EXTERNAL_AUTHORIZATION/PRODUCER_ABSENT now; synthetic imports cannot complete FIN-06 or full Phase 7.

### Complete dashboard (Phase 8)

- [ ] **UI-01**: Show assets, freshness, daily indicators and Python context. Acceptance: synthetic stale/unknown/partial data is visually distinguished and never displayed as live/ready.
- [ ] **UI-02**: Administer catalog, rules, policies and imports. Acceptance: validated changes create audit revisions; pause switches immediately stop subsequent collection/delivery claims.
- [ ] **UI-03**: Show logical alerts and channel outcomes separately. Acceptance: accepted/confirmed/uncertain/dead-letter outcomes, cause and attempt lineage are inspectable.

## v2 Requirements

- **FUT-01**: Expanded scraper capabilities only under a separately approved scope; no TTL changes or price polling in this milestone.
- **FUT-02**: Additional adjusted historical sources only after methodology/compatibility verification; no source mixing by default.
- **FUT-03**: Other expanded technical indicators beyond v1 SMA20/50, RSI14, EMA9/21 and completed-daily volume require verified data and separately approved future scope.
- **FUT-04**: Portfolio-level contextual analytics only under a separately approved privacy contract; never duplicate Python calculations.

- **FUT-05**: Volatility is explicitly proposed v2, pending human scope approval. Candidate close-to-close return standard deviation needs approved window, sampling/annualization, price-return versus total-return definition, gaps/actions and adjustment policy. Those risk semantics/history are not validated in the package; do not silently equate RSI or abnormal volume with volatility. Future milestone owns implementation; no current v1 phase or automatic activation.

## Out of Scope

See PROJECT.md: trading, yfinance, n8n, brokers/microservice proliferation, Java valuation, scraper modernization, live service access during discovery, and personal financial records.

## Traceability

All statuses are Pending. Source availability/reconciliation does not mark a requirement implemented or approved.

| Phase | Requirement IDs | Status |
|-------|-----------------|--------|
| 1 | GOV-01, GOV-02, CAT-01, CAT-02 | Pending |
| 2 | SYS-01, SYS-02, SYS-03, SEC-01, SEC-02 | Pending |
| 3 | MKT-01, MKT-02, MKT-03, MKT-04, MKT-05, MKT-06, QUO-01, QUO-02, CAL-01, CAL-02 | Pending |
| 4 | RUL-01, RUL-02, RUL-03, RUL-04, RUL-05, NOT-01, NOT-02, NOT-04, NOT-05, OPS-01, OPS-02, OPS-03, OPS-04, SEC-03 | Pending |
| 5 | HIS-01, HIS-02, HIS-03, HIS-04, IND-01, IND-02, IND-03, RUL-06, RUL-07, IND-04, IND-05 | Pending |
| 6 | NOT-03, CMD-01, CMD-02, CMD-03 | Pending |
| 7 | FIN-01, FIN-02, FIN-03, FIN-04, FIN-05, FIN-06 | Pending |
| 8 | UI-01, UI-02, UI-03 | Pending |

**Coverage:** 57 unique v1 IDs; 57 owned exactly once; 0 unmapped/duplicate/extra; 0 implemented. Evidence-backed proposals, producer gaps and human approval are recorded in docs/planning/RECONCILIATION.md. No requirements removed by reordering.

