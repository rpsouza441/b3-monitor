# Specification: B3 Monitor v1

Status: CRITICALLY REVISED PROPOSAL 2026-10-06 FOR HUMAN REVIEW. User-requested features are reconciled explicitly; audit facts remain separate from proposed formulas, operational targets and policies. All ten reports and 23 CSV rows read; [review packet](RECONCILIATION.md) and [reuse registry](PYTHON-REUSE.md) distinguish facts, gaps and proposed scope. See [open questions](OPEN-QUESTIONS.md), [requirements](../../.planning/REQUIREMENTS.md), [architecture](../architecture/ARCHITECTURE.md) and [contracts](../contracts/CONTRACTS.md). No implementation authorized.

## Product workflow

1. An authenticated administrator imports an evidence-backed catalog and approves a versioned session calendar and provider policies.
2. A scheduler reserves quota for a 30-minute collection slot. One worker queries one ticker at a time, validates the response, stores an immutable observation and reports rejected/stale observations.
3. Phase 4 first delivery uses manual BRL price targets and independently eligible absolute quotes; it requires neither history nor Python metrics. Minimal authenticated configuration/status/pause, observability, encrypted backup/restore and security gate this delivery.
4. Enabled rules consume compatible eligible inputs. Evaluation and outbox insertion commit together; duplicate evaluations cannot emit duplicate logical alerts.
5. Phase 4 delivery submits to a verified outbound WAHA adapter and exposes accepted, confirmed, failed or uncertain outcomes. SMTP arrives in Phase 6; commands stay disabled until their separate auth/replay contract is proven.
6. Phase 5A adds percentage and AND/OR after validated inputs; 5B adds separate completed daily SMA/RSI/EMA/volume conditions. Phase 7A verifies a Java synthetic snapshot consumer; 7B requires separate authorization and real Python producer/fidelity evidence; Phase 8 expands the complete dashboard. Missing exporter/context never disables the price monitor.
7. Authorized WhatsApp principals can request status, quotes and pause/resume an existing rule after webhook authentication and replay checks.

## Users and boundaries

Proposed v1 has one operator/administrator and configured notification recipients, with no public registration or multi-tenant product. Recipient routing and command authorization are distinct permissions. Public webhook exposure is a future deployment decision, not a reason to expose WAHA itself. The monitoring application does not issue orders or modify the Python portfolio project.

## Observable behaviors

- Asset type is sourced explicitly; ticker suffixes alone do not establish FII/FIAGRO/unit classification.
- A stored quote may be visible as stale/invalid/unknown while remaining ineligible for rules.
- Price change is computed as `100 * (price - previousClose) / previousClose` only when both operands have validated trading-date, basis and corporate-action compatibility and previousClose is positive. Otherwise change is unavailable with a reason.
- An observation outside its freshness policy cannot trigger or rearm a rule. HTTP 200 is insufficient.
- Explicit v1 proposal: SMA20/50, Wilder RSI14, EMA9/21 and finalized daily abnormal volume (Vt/previous20 mean). EMA addresses requested short/long trend signals with stable seed/convergence assurance; volume adds requested activity signals only where finalized compatible units/history exist. Neither is Python reuse: audited technical calculations are absent there. Volatility is proposed v2/FUT-05 because return basis/window/sampling/annualization/actions are undefined and require distinct risk/data validation; owner must approve this scope choice or add validated v1 acceptance. Unsupported/insufficient inputs remain NOT_READY; no intraday candles are implied.
- Phase 4 conditions: quote price above/below a manually configured BRL threshold. It is not computed from Graham, P/VP or portfolio weights. Percentage thresholds activate in Phase 5A only with verified prior-close date/basis/actions; bounded same-asset AND/OR requires every leaf validated and a coherent cutoff. Any UNKNOWN leaf makes the whole composition UNKNOWN even OR. Phase 5B technical types require history/basis/age/seed/unit gates. All conditions have revision, comparison precision and whole-rule rearming policy.
- A sampled crossing means a crossing observed between eligible evaluations; excursions between polls can be missed. The UI and message communicate this limitation.
- Imported financial metrics are context only in v1, including READY metrics. PARTIAL, DIAGNOSTIC_ONLY, NOT_READY, UNKNOWN or degraded data always retain their labels.
- An alert records its rule revision, exact market inputs, formula/indicator version, calendar revision, logical event ID and quality gate decision.
- WAHA acceptance and SMTP server acceptance are observable submission states; neither is represented as confirmed recipient delivery.

## Proposed policy defaults for review

| Policy | Draft | Activation gate |
|--------|-------|-----------------|
| Timezone | America/Sao_Paulo, UTC persisted instants | Confirm calendar and provider timestamp interpretation |
| Quote cadence | 30 minutes, half-open verified sessions | Verify calendar and quota scenario |
| Routine budget | 10,500/cycle (70%); protected reserve 4,500 (30%) | Audit CONSUMO recommendation; approve allocation, actual billing boundaries/shared consumers |
| Quote age ceiling | 45 minutes hard eligibility guard; 5-minute healthy objective; future skew 2 minutes | Separate source-age policies requiring approval; cannot guarantee notification within 60 minutes |
| Best-effort latency | 99% admitted slots <=1min late; 95% eligible intents commit→ACCEPTED <=2min and source→ACCEPTED <=10min; 95% evaluated quote ages <=5min | Q-24: approve denominator/window/minimum samples; unknown/failed/skipped outcomes stay visible; no provider/delivery SLA |
| Unknown required timestamp/calendar/basis | Ineligible for the affected use | Resolve with evidence; unknown previous-close/history basis does not invalidate a separately eligible observed absolute-price quote |
| Initial-alert mode | UNSELECTED: human must choose default/per-rule CROSSING or LEVEL; LEVEL initial create/edit/resume are explicit opt-ins | RUL-03/05, ADR-012; no silent initial suppression default or automatic activation |
| Rule rearming | Two distinct eligible false evaluations, same revision/basis; optional hysteresis | Confirm per-rule policy and fixture results |
| Brapi retry | At most 2 total attempts (initial + one repeat) for reviewed timeout/429/500/503, backoff/budget/deadline bounded | No parameter/plan-error retries; 5% contingency is sizing, not permission |
| Messaging retry | Bounded adapter-specific attempts only with definite pre-send failure or verified idempotency | Unknown execution/acceptance without receipt never justifies blind resend |
| Provider stale flag | x-brapi-stale=1: degraded and signal-ineligible by proposed default | Header semantics unknown even with young timestamp; explicit reviewed exception required |
| Context refresh | Java consumer synthetic first; actual authorized Python asset-only export separately | FIN-01 synthetic acceptance is distinct from FIN-06 real-result fidelity and integration |
| Backups | Daily; draft RPO 24h, RTO 4h before Phase 4 activation | Encryption/storage/retention/high-water and synthetic restore reviewed; repeat with later surfaces |

45 minutes is a proposed maximum tolerated age, not an estimate of Brapi delay. Combined with 30-minute polling, an age-plus-detection budget can reach 75 minutes before queue/send delay; this is not an alert guarantee, because stale observations may be suppressed and true event/visibility time is unknown. Real-event-to-recipient <=60 minutes is not guaranteed; metrics distinguish source age, polling, admission lateness, intent→acceptance and independently confirmed delivery. See the exact denominators/degraded/suppression rules in CONTRACTS. A slower provider may cause no alerts; show that condition rather than relaxing the gate silently. Timestamp precision and expected market sessions must be known before applying this ceiling. Metric freshness is per-metric reference-date policy and cannot inherit quote-age settings.

## Acceptance and release boundary

Each roadmap phase has measurable gates, failure scenarios and rollback considerations. Requirement checkboxes stay open until later implementation and verification. Passing discovery review authorizes only the next explicitly agreed planning stage. The application remains inactive until source evidence, policy approval, synthetic end-to-end checks and restore/security gates are met under a separately authorized implementation request.

The first synthetic end-to-end is quote → validation → manual price episode → transactional outbox → simulated WAHA. No indicator or financial exporter prerequisite. Before any live activation, resolve the outbound WAHA contract, calendar/asset/policies/version/backup gates for enabled surfaces. Later history/import/webhook surfaces must repeat corresponding security and recovery checks before enabling. All-v1 release also requires their full review.

## Initial-alert behavior for approval

CROSSING observes eligible FALSE→TRUE after distinct false confirmations; already-true creation/edit/resume suppresses initial notification. LEVEL can consume one opted-in initial token if first eligible predicate is TRUE. It then latches and rearms exactly like CROSSING; it is not repeated periodic notification. Both preserve episode/initial-consumption/cooldown across restart and unknown gaps. Edit creates a revision and invalidates unsent old intents; optional LEVEL-on-edit respects carried cooldown. Ordinary resume never replays TRUE episodes; approved LEVEL-on-resume needs an ended prior episode. Restore with lost state quarantines rather than guesses. The default remains pending owner approval (Q-19). [Full state contract](../contracts/CONTRACTS.md) defines atomic token consumption, gap handling and duplicate protection.

## Dependency and release boundaries

Existing shared WAHA needs scoped session/credentials/routes/network/rate budget and owner-provided exact outbound version/auth/send semantics; monitor isolation does not authorize resetting or controlling other consumers. Brapi may share an account budget with ticker-scraper; owner allocation/coordination is required, and token separation alone is insufficient. KNHY11 stays independently quarantined; a specifically approved valid subset can deliver the price MVP without claiming full 23-asset coverage or forcing Phase 1 completion.

Backup destination/encryption/key custody/retention/RPO/RTO/restore authority and Java/Boot/build/PostgreSQL/indicator-library compatibility remain approval/evidence gates. Draft daily/RPO24h/RTO4h is not approved. Missing verified previous-close provenance blocks percentages; missing history/unit/library assurance blocks only affected technical leaves. Phase 7 real financial reuse/full-v1 acceptance is blocked by absent producer and separate upstream authorization. A synthetic UI/consumer can be tested earlier; no working-real-integration claim until FIN-06 is satisfied. None of these proposals authorize implementation or external access.
