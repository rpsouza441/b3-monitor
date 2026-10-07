# Roadmap: B3 Monitor

## Overview

**Status: SOURCE-RECONCILED PLANNING PROPOSAL — HUMAN REVIEW PENDING.** Ten reports and 23 CSV rows read; hashes, capability limits and remaining questions recorded in [EVIDENCE](../docs/references/EVIDENCE.md). An MVP reliability vertical IS implemented and under test (`src/`, `mvn test` = 123 green, Flyway V1–V8, Spring Boot 3.3.13) — but implementation is NOT phase approval: no executable GSD plan is approved, no phase is completed, no requirement is marked complete merely because code exists, all 23 assets remain `NOT_AUTHORIZED`, and automatic advancement remains disabled. The PostgreSQL/Testcontainers integration suite is written but NOT_RUN (no Docker on the build host).

Keep eight phases and all original 52 v1 IDs; add RUL-06, RUL-07, IND-04, IND-05 and FIN-06 for 57 v1 requirements. First delivery after Phase 4: Brapi → validation → manually configured absolute-price rule → PostgreSQL/state/outbox → outbound WAHA. History, Python context, SMTP and commands do not gate this path. Integrity, authentication, quota, uncertainty, observability and recovery do gate it. A synthetic working monitor is distinct from approved live activation; the WAHA contract and other early policies remain prerequisites for the latter.

## Phases

- [ ] **Phase 1: Evidence Reconciliation and Asset Identity** - Review reconciled audits, identities and policies; approve the documentation boundary.
- [ ] **Phase 2: Independent Monitor and Secure Persistence** - Establish only the secure durable foundation needed by the initial monitor.
- [ ] **Phase 3: Recent Quotes, Calendar and Quota** - Validate serialized observations with persistent admission and per-use eligibility.
- [ ] **Phase 4: Minimum Price Alerts and WAHA** - Deliver the first complete price-alert flow with recovery and security gates.
- [ ] **Phase 5: Percentage, Combined and Technical Rules** - Add gated percentage/AND/OR rules first, then daily SMA/RSI/EMA/abnormal-volume conditions without source mixing.
- [ ] **Phase 6: SMTP Fallback and Authorized Commands** - Extend channel routing and authenticated replay-resistant commands.
- [ ] **Phase 7: Financial Consumer and Authorized Producer** - Verify the Java consumer separately from the blocked, separately authorized real Python producer; preserve financial meaning.
- [ ] **Phase 8: Complete Dashboard and Release Review** - Expose the full evidence and repeat release checks for added surfaces.

## Dependency Gates

Edges: 1 → 2; 1 + 2 → 3; 2 + 3 → 4; 2 + 3 + 4 → 5; 2 + 4 → 6; 1 + 2 → 7; 4 + 5 + 6 + 7 → 8. Candidate delivery sequence: 1 → 2 → 3 → 4 → 5 → 6 → 7 → 8. Phases 6/7 can be planned independently once prerequisites hold; no automatic parallel execution is authorized.

Phase 4 does not require historical bars or financial exports. Phase 5 can use a separately eligible Brapi daily series while COTAHIST export is unavailable; HIS-04's contract/lifecycle acceptance can use synthetic bundles, but actual COTAHIST activation requires producer fidelity/coverage evidence. Phase 7A can verify consumer behavior with synthetic public metrics (FIN-01–05); Phase 7B/FIN-06 requires separate producer authorization and real sanitized calculation exports. A synthetic importer does not complete FIN-06 or prove real integration. Full Phase 7/v1 financial reuse stays blocked unless the owner formally revises scope; missing producer does not block Phase 4. FIN-05 is satisfied by proving the present interface unsupported and keeping it disabled; no scraper changes/SQL workaround. Full live coverage is never claimed for an unavailable path.

All phases require human-approved scope and later implementation authorization. KNHY11 is an independent asset quarantine: approve a named valid subset without claiming 23/23 coverage. CAT-01/full-catalog completion remains pending; a reviewed subset may satisfy downstream identity gates only through an explicit scope/dependency approval, never a forced GSD completion or automatic advancement. Early release gates apply to currently enabled surfaces; later history/import/webhook/UI surfaces must pass those same controls before enablement. Existing requirements are retained. EMA9/21 and abnormal volume are explicit v1 proposals; volatility is explicitly proposed v2 (FUT-05), pending human scope approval rather than silently omitted. The original delivery request remains authoritative.

## Phase Details

### Phase 1: Evidence Reconciliation and Asset Identity

**Goal**: The operator can review all audit evidence and the exact 23-row catalog, resolve identity conflicts and approve source-dependent planning choices.
**Depends on**: Nothing (first phase); ten source documents are now present/read, human approval and remaining identity evidence pending.
**Requirements**: GOV-01, GOV-02, CAT-01, CAT-02
**Success Criteria**:

  1. All ten original files, hashes, section citations and 23 parsed rows are inspectable; AUDIT_REPORTED and LOCAL_VERIFIED are distinguished. Missing raw bodies/upstream source are not presented as verified. (GOV-01)
  2. Specification/ADRs/divergences and one owner for each of 57 IDs are reviewed; approval records identify scope without enabling auto-execution. (GOV-02)
  3. Catalog has 23 unique requested/returned identities and explicit classes; KNHY11 FII proposal requires official confirmation before its activation, without inventing a FIAGRO mapping. (CAT-01)
  4. Dated identities and synthetic rename/delisting/ticker-reuse scenarios preserve original observation attribution. (CAT-02)

**Plans**: TBD — documentation reconciled, human review pending; no executable PLAN.md.

**Failure scenarios**: Classification/identity disagreement, missing official evidence, report claims promoted to runtime verification. Keep affected activation pending. File presence alone does not complete this phase.
**Rollback considerations**: Supersede a catalog/ADR proposal by reviewed revision, preserving original audit hashes and divergence history.

### Phase 2: Independent Monitor and Secure Persistence

**Goal**: The operator can start a minimal isolated monitor with durable state and authenticated configuration.
**Depends on**: Phase 1 reviewed scope; explicit later implementation authorization, versions and private-access policy.
**Requirements**: SYS-01, SYS-02, SYS-03, SEC-01, SEC-02
**Success Criteria**:

  1. Pinned Java/Spring Boot/PostgreSQL/build/indicator-library versions, licenses and an API/decimal/seed compatibility matrix pass offline fixtures before enablement; one application and PostgreSQL have explicit module ownership and atomic rule/outbox capability; neither legacy runtime/shared database nor broker is required. (SYS-01)
  2. Restart preserves observations, revisions/audits, quota and rule state; duplicate identities fail. (SYS-02)
  3. Approved synthetic monitor/PostgreSQL Compose composition starts, health-checks and restarts in isolation. The existing shared WAHA is an external dependency: scoped session/credentials/routes/network/budgets are evidenced, and no other consumer is started, stopped, reset, logged out or reconfigured. Inadequate isolation blocks live use, not fake-adapter tests. (SYS-03, SEC-02)
  4. Minimal authenticated admin supports later catalog/target/recipient/pause setup; anonymous/non-admin, expired-session and CSRF mutations fail, and secrets/logs are redacted. A complete dashboard is not a prerequisite. (SEC-01, SEC-02)

**Plans**: TBD — no runtime or migrations created.
**UI hint**: yes

**Failure scenarios**: State loss, partial transactions, network exposure, unauthorized mutation/secret leakage; workers remain disabled.
**Rollback considerations**: Pause workers, revert compatible versions/config and use approved restore strategy; preserve evidence and uncertain quota consumption.

### Phase 3: Recent Quotes, Calendar and Quota

**Goal**: The operator can inspect attributable Brapi quote eligibility and conservative persistent quota usage.
**Depends on**: Phases 1 and 2; approved calendar/session, quote-age/stale/action policies and allocated billing budget.
**Requirements**: MKT-01, MKT-02, MKT-03, MKT-04, MKT-05, MKT-06, QUO-01, QUO-02, CAL-01, CAL-02
**Success Criteria**:

  1. A synthetic 23-asset capacity cycle and named approved live subset use one ticker/request, concurrency one and 30-minute eligible slots; KNHY11 remains independently quarantined; unknown/closed/special sessions obey versioned calendar policy. (MKT-01, CAL-01)
  2. v2 results/data identity, BRL, positive decimal price, ISO market/source/receipt times and trading date validate; malformed/stale/future/wrong-identity data cannot trigger/rearm. x-brapi-stale=1 is degraded and signal-ineligible until explicitly reviewed. (MKT-02, MKT-03)
  3. Change uses 100×(price−previousClose)/previousClose with compatible dates/basis/actions; BPAC11 fixture gives approximately −0.012058%, not +25.6%. Unknown previous-close date/basis disables change, independently of eligible absolute price. (MKT-04, CAL-02)
  4. Duplicate/expired slots skip visibly without catch-up bursts; rotation prevents starvation; auth failures stop collection, plan errors are not retried, and transient retries/backoff are bounded. (MKT-05, MKT-06)
  5. Every potential quote/history/manual/retry charge shares pre-dispatch reservations, ceilings and reset reconciliation, including owner-confirmed account allocation/coordination if ticker-scraper shares Brapi. Different tokens alone do not prove separate quota. Proposed 30% reserve/10,500 routine ceiling and two total Brapi attempts survive crash/competing workers/shared-token uncertainty. Sample reset is a delta, not a fixed future date. (QUO-01, QUO-02)

**Plans**: TBD — offline fixture/policy agreement first; no provider requests now.

**Failure scenarios**: HTTP-success error bodies, undocumented timestamps, stale header despite young quote, unknown previous-close date, split/distribution, queued reset, timeout-after-dispatch. Eligibility is per use; ambiguity counts conservatively.
**Rollback considerations**: Pause, preserve attempts/reservations/quotes, revise mappings without refunding potential charges or replaying missed slots.

### Phase 4: Minimum Price Alerts and WAHA

**Goal**: The operator can configure a manual price target and inspect one durable, explainable logical alert sent through a verified outbound WAHA adapter, with safe recovery.
**Depends on**: Phases 2 and 3; reviewed shared-WAHA isolation and exact outbound version/send/auth contract, recipients, approved CROSSING/LEVEL and initial/rearm/rate/expiry policies, owner-approved encrypted backup/RPO/RTO and early release policies.
**Requirements**: RUL-01, RUL-02, RUL-03, RUL-04, RUL-05, NOT-01, NOT-02, NOT-04, NOT-05, OPS-01, OPS-02, OPS-03, OPS-04, SEC-03
**Success Criteria**:

  1. Authenticated configuration supports manual BRL price above/below targets. The typed rule registry rejects arbitrary code/financial operands and rejects percentage, composition and technical activation until the relevant Phase 5 eligibility gates. Ineligible input yields UNKNOWN without trigger/rearm. (RUL-01, RUL-02)
  2. CROSSING baselines without an initial alert and requires an eligible observed transition; LEVEL can consume one explicitly opted-in initial token when already true. Default remains UNSELECTED pending human approval. Create/edit/resume, two distinct eligible false confirmations/hysteresis, revision races, persisted initial-consumption/episode IDs and cross-revision cooldown pass both-mode fixtures; restart, gaps and resume cannot mint duplicate episodes. (RUL-03, RUL-05)
  3. Eligible transition commits evaluation/state/signal/outbox atomically. Crash/lease/timeout-after-send scenarios yield one logical intent and explicit external uncertainty without blind resend. (RUL-04, NOT-01)
  4. Outbound WAHA ACCEPTED is recorded separately from delivery UNKNOWN; only proven receipt evidence confirms. Versioned/redacted messages show source/as-of/quality/condition/event ID; rate/expiry/dead-letter controls bound storms. SMTP and inbound commands remain disabled. (NOT-02, NOT-04, NOT-05)
  5. Minimal admin/logs distinguish source age, 30-minute poll cadence, scheduling delay and source/evaluation→WAHA acceptance/delivery timings; proposed best-effort objectives, degraded states and denominator/exclusion counts are measurable. They expose suppression, quota/skips, state/outbox attempts, pause and manual uncertain-outcome review; no 60-minute or provider SLA is claimed. Later import/webhook metrics are mandatory when those surfaces are added. (OPS-01)
  6. Synthetic encrypted monitor-only backup/restore meets owner-approved destination/encryption/retention/RPO/RTO and restore authority; shared WAHA state is not restored/reset; workers start disabled, independent quota high-water/potential sends reconcile and uncertain restored intents quarantine. End-to-end quote→price-rule→outbox→simulated-WAHA, restart/outage/retention/recovery walkthroughs pass. (OPS-02, OPS-03, OPS-04)
  7. Admin/session/CSRF, provider/WAHA isolation/auth, catalog/config parsing, redaction and backup checks pass with no unresolved critical findings; absent webhook/financial imports are unreachable. Their future enablement requires SEC-03 regression checks first. Live activation requires a separately authorized environment and verified outbound contract. (SEC-03)

**Plans**: TBD — first vertical delivery target; no executable plan or real message sent.
**UI hint**: yes

**Failure scenarios**: Target crossed before baseline, stale/future input, edited rule race, repeated false sample, send timeout, accepted without receipt, restore replay/underreported quota. Never resolve uncertainty by repeated sends.
**Rollback considerations**: Disable dispatch/rules, retain episodes/outbox/attempts and quarantine possibly sent intents; rollback cannot recall external messages or reset provider usage.

### Phase 5: Percentage, Combined and Technical Rules

**Goal**: The operator can first activate verified percentage and price/percentage AND/OR rules, then eligible daily SMA20/50, RSI14, EMA9/21 and abnormal-volume rules through the existing outbox path.
**Delivery split**: 5A (RUL-06/07) follows Phase 3 per-use input gates and Phase 4 state/delivery, independently of history/exporters. 5B adds HIS/IND inputs and technical leaves; unvalidated capabilities are rejected at enablement. Whole Phase 5 completion requires all 11 owners. Volatility is proposed v2/FUT-05, with its unresolved definition retained.
**Depends on**: Phases 2, 3 and 4. 5A needs verified previous-close date/basis/actions for percentage and validated leaves for composition; it does not wait for history. 5B additionally needs reviewed finality, series selection, seed and action/basis compatibility. COTAHIST activation separately requires producer export/fidelity/coverage.
**Requirements**: HIS-01, HIS-02, HIS-03, HIS-04, IND-01, IND-02, IND-03, RUL-06, RUL-07, IND-04, IND-05
**Success Criteria**:

  1. Daily data has explicit source/field/units/basis/revision/date; invalid OHLCV quarantines. A close-only series declares absent OHLC/volume rather than inventing them. Current day is provisional; missing/unverified sessions cannot silently enter calculations. (HIS-01, HIS-02)
  2. Brapi adjustedClose, Brapi close with uncertain adjustment and COTAHIST RAW remain separate; no concat/implicit coalesce. Corrections/actions invalidate dependent lineage; quote-versus-adjusted-SMA crossing remains disabled without comparability evidence. (HIS-03)
  3. COTAHIST 245/010/PREULT/100 and PARTIAL per-security coverage map to a proposed independent close export; bounded whole-bundle validation, ID/digest repeats/conflicts, atomic activation, immutable supersession/rollback pass synthetically. Export is not claimed existing; unavailable producer leaves this series disabled while other verified series remain usable. (HIS-04)
  4. SMA20/SMA50 independent golden fixtures pass at approved precision; Wilder RSI14 passes 15-close seeding, flat/no-gain/no-loss and recurrence. Missing/incompatible history returns NOT_READY. (IND-01, IND-02)
  5. Percent above/below signed thresholds uses Java arithmetic and verified previous trading close/date/basis/actions. Equality, date rollover, denominator, corrections and both-mode/outbox fixtures pass; absent previous-close evidence keeps percentage disabled while eligible price works. (RUL-06)
  6. Same-asset AND/OR uses a bounded typed AST (proposed 8 leaves/depth 4), explicit parentheses and all-leaf lineage; runtime any ineligible leaf yields UNKNOWN even for OR. All-eligible truth tables, coherent cutoff/max-skew/basis policies, editing/restart and whole-expression episode/rearm fixtures pass. (RUL-07)
  7. EMA9/21 uses alpha=2/(N+1), SMA seed, stable recurrence/seed lineage, precision and independently reviewed warmup/convergence; short input is labeled LIMITED_HISTORY. EMA9-versus-EMA21 compares same-date/source/basis outputs and ordered pairs; flat/gap/correction goldens pass; no moving-window reseed. (IND-04)
  8. Abnormal volume compares finalized daily Vt with the preceding 20 finalized daily volumes, excludes Vt from the mean, requires known compatible units/21 bars and rejects provisional/missing/incompatible/nonpositive-denominator data. Close-only COTAHIST cannot supply volume. (IND-05)
  9. Results show formula/input revisions, required/actual bars and readiness; no rolling reseed or unsupported period approximation. Eligible SMA/RSI/EMA/abnormal-volume leaves activate in the Phase 4 typed engine only after basis/age/sample-gap/equality tests and baseline reset; expired/incompatible data never triggers/rearms. (IND-03; consumes RUL-01–05)

**Plans**: TBD — COTAHIST remains v1 priority with separate capability gate.

**Failure scenarios**: Unknown adjustment methodology/volume units, provisional/duplicate/gap bars, raw action discontinuity, moving seed, missing exporter, oversized/private/conflicting history bundle. No claim of 23-asset history from two measured series.
**Rollback considerations**: Disable series/indicator selection, restore prior activation pointer preserving revisions/invalidations/alerts; no upstream changes or automatic stitching.

### Phase 6: SMTP Fallback and Authorized Commands

**Goal**: The operator can extend routing with explicit SMTP ambiguity policy and enable only authenticated bounded commands.
**Depends on**: Phases 2 and 4; verified offline webhook/principal/event/replay contract, SMTP policy and SEC-03 checks for new surfaces. History/context not prerequisites.
**Requirements**: NOT-03, CMD-01, CMD-02, CMD-03
**Success Criteria**:

  1. Definite WAHA failure and unknown outcome follow separately approved fallback rules; races/late success/SMTP acceptance retain one logical event and visible potential cross-channel duplicate, without blind retry. (NOT-03)
  2. Transport auth and trusted sender/session/action ownership independently validate; forged/oversized/unauthorized inputs cause no mutation. (CMD-01)
  3. Duplicate/stale/out-of-order events cannot repeat mutations; durable inbox receipt precedes acknowledgement and audit/reply are idempotent. (CMD-02)
  4. Only status, quote and pause/resume of owned existing rules are supported; no shell/SQL/trading/arbitrary expressions or recipient/admin changes. New surfaces pass security/metrics/recovery regression before activation. (CMD-03; consumes OPS-01–04, SEC-03)

**Plans**: TBD — unavailable webhook semantics keep commands disabled, without blocking outbound price alerts.
**UI hint**: yes

**Failure scenarios**: Forged sender, replay/echo, callback reorder, fallback race, SMTP uncertain handoff. Acceptance is not delivery.
**Rollback considerations**: Disable commands/fallback, retain inbox and channel attempts; pause potentially sent intents for reconciliation.

### Phase 7: Financial Consumer and Authorized Producer

**Goal**: The operator can inspect approved public asset outputs with original readiness while upstream projects remain independent.
**Depends on**: Phases 1 and 2; agreed asset-only schema/registry and synthetic consumer fixtures; actual context activation requires separately authorized producer output.
**Requirements**: FIN-01, FIN-02, FIN-03, FIN-04, FIN-05, FIN-06
**Success Criteria**:

  1. Deliverable A: versioned/tested Java consumer imports synthetic public asset-only snapshots with Python offline. CONSUMER_VERIFIED_SYNTHETIC never means working real calculations/integration. No runtime call/shared DB/Java finance formulas. (FIN-01)
  2. Registry preserves Graham PARTIAL, P/VP snapshot READY/history PARTIAL and B&H PARTIAL, each with units/as-of/generation/coverage/limitations and separate local assurance. Bazin/quality FII unavailable state has no usable number; unknown contracts fail closed. (FIN-02)
  3. Bounded whole-bundle validation rejects private/extra/unsupported/conflicting values before partial activation; identical exporter/ID/digest is no-op; revisions remain inspectable. New import surfaces pass security/metrics/recovery checks before enablement. (FIN-03)
  4. Even READY metrics are contextual, with no financial-rule namespace; XIRR/TWR/CDI portfolio, holdings/cash flows/PM are excluded. The operator explicitly reviews this scope choice. (FIN-04)
  5. Audit proves current scraper has no cache-only HTTP operation and can refresh on GET/raw. Adapter remains disabled with reason; no SQL/credential workaround, TTL change, legacy edit or price promotion. Future artifact support needs separate review. (FIN-05)
  6. Deliverable B: actual Python producer/exporter changes are blocked until separate authorization for projecao-carteira; sanitized public-input fixtures prove canonical formula fidelity, readiness/units/dates/coverage/versions, and real artifacts pass the Java consumer. INTEGRATION_VERIFIED_REAL requires both FIN-01 and FIN-06; authorization alone or a synthetic bundle cannot satisfy it. No portfolio files are accessed. (FIN-06)

**Plans**: TBD — [reuse registry](../docs/planning/PYTHON-REUSE.md), no upstream implementation.
**UI hint**: yes

**Failure scenarios**: Fresh file hides old metrics, parent READY masks child PARTIAL, personal “per-asset” returns, missing producer, snapshot refresh side effects/false as-of. Preserve labels and independence.
**Rollback considerations**: Disable import/adapter, restore prior context pointer preserving evidence; quotes/rules/delivery continue independently.

### Phase 8: Complete Dashboard and Release Review

**Goal**: The operator can inspect all enabled data/rule/channel surfaces and review the complete v1 release evidence.
**Depends on**: Phases 4, 5, 6 and 7; synthetic UI acceptance can proceed without a producer, but full v1 financial integration/release cannot be claimed without FIN-06 or an explicit reviewed scope revision. Inherited operations/security gates rerun for all added surfaces before activation.
**Requirements**: UI-01, UI-02, UI-03
**Success Criteria**:

  1. Dashboard distinguishes dated market values/current eligibility, completed daily/warmup and qualified Python context; unavailable imports/scraper remain explicit. (UI-01)
  2. Catalog/rule/policy/import changes validate and create audit revisions; pause stops subsequent claims; auth/CSRF checks cover the complete UI. (UI-02)
  3. Logical alerts expose independent channel acceptance/confirmation/uncertainty/dead-letter and exact lineage; full-surface end-to-end/restore/security/retention regression and review have outcomes before v1 activation. (UI-03; consumes OPS-01–04, SEC-03)

**Plans**: TBD — no implemented UI or release validation.
**UI hint**: yes

**Failure scenarios**: Stale appears live, missing context appears READY, accepted appears delivered, new surface bypasses early controls. Release stays pending.
**Rollback considerations**: Pause affected surfaces and revert reviewed versions/pointers while preserving evidence and conservative quota/delivery state.

## Progress

| Phase | Plans Complete | Status | Completed |
|-------|----------------|--------|-----------|
| 1. Evidence Reconciliation and Asset Identity | 1/4 | In Progress|  |
| 2. Independent Monitor and Secure Persistence | 0/TBD | Not started | - |
| 3. Recent Quotes, Calendar and Quota | 0/TBD | Not started | - |
| 4. Minimum Price Alerts and WAHA | 0/TBD | Not started | - |
| 5. Percentage, Combined and Technical Rules | 0/TBD | Not started | - |
| 6. SMTP Fallback and Authorized Commands | 0/TBD | Not started | - |
| 7. Financial Consumer and Authorized Producer | 0/TBD | Not started | - |
| 8. Complete Dashboard and Release Review | 0/TBD | Not started | - |

Coverage: 57 v1 IDs exactly once, phase counts 4 + 5 + 10 + 14 + 11 + 4 + 6 + 3. Original 52 IDs preserved; five new owners added; FUT-05 is an explicit v2 proposal outside this count. Additional cross-phase references are dependencies, not duplicated owners. Documentation reconciliation does not check off GOV/CAT requirements or complete a phase; remaining review/identity/runtime criteria are open.

Human review must address [RECONCILIATION](../docs/planning/RECONCILIATION.md) and [questions](../docs/planning/OPEN-QUESTIONS.md). Later GSD discussion/planning and implementation require an agreed scope; this proposal authorizes no code, migration, provider call, credential/sibling access, message, commit or deployment.
