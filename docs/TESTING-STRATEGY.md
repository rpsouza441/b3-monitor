# Testing and acceptance strategy — planning only

No tests implemented/executed against an application. Current checks validate documentary source integrity, CSV arithmetic and planning structure. Ten reports/23 CSV rows were read; upstream tests remain AUDIT_REPORTED and were not reexecuted. Future scenarios use synthetic data/recipients; source examples are evidence, not current production prices.

## Layers

1. Contract checks: [catalog](planning/ASSET-CATALOG.md), reconciled provider field/date/unit mapping, public snapshot registry/status/coverage and inbox/outbox identities. Official KNHY11/producer/WAHA gaps stay gates.
2. Independent arithmetic fixtures: percentage changes and daily SMA/RSI/EMA and finalized-volume ratio against hand-calculated golden outputs, seed conventions and declared tolerance. Existing Python finance formulas are tested by producer evidence/export fixtures, never Java reimplementation.
3. Database/transaction integration: uniqueness, rule edit races, leases/fencing, rollback, persistent quota admission and atomic rule/outbox commit using an isolated synthetic PostgreSQL environment during later implementation.
4. Adapter simulations: HTTP success with invalid body, 429/auth failures/timeouts, delayed/reordered delivery, SMTP acceptance/failure and forged webhooks. No production sessions or API calls.
5. First synthetic end-to-end, Phase 4: offline quote -> per-use eligibility -> manual BRL target -> rule episode -> atomic outbox -> simulated outbound WAHA -> authenticated status/audit. No daily indicator/exporter prerequisite. Phase 5A tests dated compatible percentage and AND/OR independently of history; 5B extends eligible SMA/RSI/EMA/volume through that engine; Phase 6 SMTP/commands, Phase 7A consumer and separately authorized7B real calculation export and Phase 8 complete dashboard add their own end-to-end/regression evidence.
6. Recovery/security: crash points, backup restore, consumption high-water reconciliation, quarantined uncertain sends, isolation and redaction.

## Required failure scenarios

| Area | Cases | Pass condition |
|------|-------|----------------|
| Catalog | 23 verified rows, duplicate/reused ticker, unit/FII/FIAGRO ambiguity | Exact unique mapping; unsupported classification remains unresolved |
| Quota | Quotes/history/retries compete; crash after reservation/send; reset while queued; shared-budget uncertainty | Concurrency <=1, every potential charge counted, routine/reserve ceilings enforced |
| Cadence/calendar | Holiday, special session, unknown calendar, long round, downtime | No off-session/unknown polling or catch-up burst; skipped work observable |
| Quote | Wrong asset/currency, nonfinite/nonpositive price, stale/future/missing timestamp, repeated price/time | Invalid/unknown data cannot trigger/rearm; last display value stays explicitly dated |
| Change | Wrong/unknown previous date, split mismatch, denominator <=0, provider-field disagreement; BPAC11 arithmetic example | Null-with-reason or ~−0.012058% for compatible 82.92/82.93; provider +25.6% diagnostic only |
| History | Provisional/late-corrected/missing/duplicate bars, units, raw/adjusted mix, actions; close-only COTAHIST | No fabricated gaps/OHLCV, coalesce or incompatible inputs; unknown quote/adjusted-SMA basis blocks comparison |
| Indicators | Warmup boundary, flat RSI, seed recurrence, rolling reseed drift | Versioned independent golden result or NOT_READY; no shorter-period substitute |
| Financial import | Unsupported schema, private/extra fields, stale READY, PARTIAL child, duplicate ID/conflicting digest | Bundle atomicity, no privacy body persistence, preserved statuses, no rule operands |
| Rules | Repeated input, unknown inputs, restart, edit during evaluation, pause/resume, two-false rearm | One logical episode; no UNKNOWN rearm or ordinary-resume replay; optional initial LEVEL is explicitly selected, consumed once and rate-limited |
| Outbox | Commit crash, timeout-after-send, lost confirmation, lease expiry, late confirmation/fallback race | State/intent atomic; ambiguity visible; no blind resend or false delivery claim |
| Commands | Invalid transport auth, forged sender/session, duplicate/old events, unknown grammar | No unauthorized/replayed mutation; ack follows durable receipt |
| Restore | Provider charges/sends after backup snapshot, unavailable reconciliation | Workers disabled; allowance conservative; potentially sent intents quarantined |

## Verification evidence per phase

Record requirement IDs, fixture provenance, expected/actual outcome, policy/contract version, lineage IDs and failures. Keep phases pending until objective gates pass; source-document availability alone does not validate running behavior. Validate rollback paths in the same synthetic environment, preserving immutable evidence and conservative quota accounting. A future manual acceptance check must inspect what the dashboard explains, not merely that an HTTP status succeeds.

## Draft release criteria

All ten reports and the 23-asset CSV reconciled; blocking contract/policy questions resolved; every v1 requirement verified; zero unresolved critical security/data-integrity findings; synthetic end-to-end/restart/restore scenarios pass; operational pause/recovery documented. Any live integration validation requires a separately authorized environment and access scope. No deployment is authorized by this document.

Phase 4 minimal release uses the applicable early subset with every enabled control verified: catalog/admin, quote/quota/calendar/action policies, manual price state/outbox, outbound WAHA, pause/metrics/uncertainty, encrypted backup/restore and security. Missing history/context/inbound/SMTP must be unreachable/explicitly unavailable, not bypass a gate for something enabled. Verify stale-header suppression, two-attempt Brapi cap, protected 30% reserve, provider-vs-local age, both CROSSING initial suppression and opted-in LEVEL initial token, repeated false observations and timeout-after-send. New surfaces rerun OPS/SEC before enablement. Official KNHY11 uncertainty prevents its activation/full-23 coverage claim; unsupported real exporter prevents actual context/COTAHIST activation.

## Added critical acceptance scenarios (proposed; none executed)

| Requirement / surface | Synthetic scenario and expected result | Separate evidence gate |
|-----------------------|-----------------------------------------|------------------------|
| RUL-06 / MKT-04 | Signed positive/negative thresholds; proposed strict equality FALSE; previousClose<=0/date unknown/basis/action conflict suppress percent but eligible price works; day rollover resets comparison continuity | Verified previous trading-close date/basis/actions before enabling |
| RUL-07 | AND/OR all-eligible truth tables and bounded AST; true OR with UNKNOWN leaf => UNKNOWN, no event/rearm; complete immutable leaf lineage; stale one leaf/edit/restart cannot short circuit gates | Approved AST bound and coherent cutoff/max-age/skew/basis/timeframe policy |
| RUL-03/05 | CROSSING first TRUE => no event; LEVEL opted-in first TRUE => one token/episode/outbox; concurrent duplicate first samples => one; first FALSE consumes initial opportunity; two distinct false/hysteresis then TRUE => next episode | Human-selected mode/initial/rearm/cooldown default |
| Lifecycle/recovery | Repeat TRUE/cooldown expiry/gap/restart => no extra episode; edit invalidates unsent old intent/carries cooldown; ordinary resume no replay; explicit LEVEL resume only after ended episode; lost state => quarantine | Durable token/lifecycle/latch keys and owner recovery policy |
| IND-04 / SYS-01 | EMA9/21 SMA-seeded hand goldens/recurrence; same-date cross; seed replay after correction; rolling-window reseed rejected; insufficient convergence labeled LIMITED_HISTORY | Pinned library/API/decimal/license and series/seed compatibility |
| IND-05 |21 finalized same-unit daily volumes; denominator previous20 excludes Vt; partial day/missing/unit conflict/zero denominator suppress | Proved finalized volume-unit source; no volume from close-only COTAHIST |
| FIN-01 versus FIN-06 | Synthetic importer passes => CONSUMER_VERIFIED_SYNTHETIC only; no real producer => Phase7/real integration still blocked; private fields reject atomically | After separate upstream authorization only: actual public canonical-calculation producer goldens/fidelity/versioned export and Java import |
| MKT-03 / OPS-01 | Simulated45min age+30min next poll demonstrates no60min promise; recheck hard ceiling at dispatch; slow queue/unknown receipt suppress/expire or show degraded/unknown | Approved targets/denominators/window; exact adapter acceptance/receipt semantics |
| SEC-02 / NOT-02 | Scoped fake session/credential/route/pause cannot mutate unrelated consumer; unsupported receipts stayUNKNOWN; synthetic adapter never proves actual WAHA | Exact owner-provided shared version/auth/send/isolation contract |
| QUO-02 | Unknown scraper share => freeze; same account/different tokens still shares allocation; competing shared reservations/concurrency cannot exceed approved budget | Owner account allocation/coordination evidence |
| CAT-01 | KNHY11 quarantine excludes only it; other valid approved assets admitted; no full23/Phase1 completion claim | Explicit named subset/dependency approval and later official KNHY11 evidence |
| OPS-02 | Restore token/episode/cooldown/high-water under approved storage/key/retention/RPO/RTO, workers disabled | Owner decisions; shared WAHA state never reset/restored |

Fixtures must contain independently calculated expected values/state transitions, not mirror library output. Fixture success is future application acceptance; current documentary validation only checks that these obligations exist, are owned and do not have unsafe prerequisite gaps.
