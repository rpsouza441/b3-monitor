# B3 Monitor — Market Data Review

## Critical correction — 2026-10-06

The current [critical review](../../docs/planning/CRITICAL-REVIEW.md), [contracts](../../docs/contracts/CONTRACTS.md) and [57-ID roadmap](../ROADMAP.md) supersede historical initialization/scope/integration claims below. Phase 4 stays absolute-price-only, with human-selected CROSSING or optional initial LEVEL (default UNSELECTED). Phase 5A percentage/AND-OR needs validated inputs, independently of history; 5B adds SMA/RSI/EMA9/21/finalized volume. Volatility is explicitly proposed v2 pending approval. Phase 7A synthetic consumer does not prove Phase 7B actual Python calculation export; separate producer authorization/fidelity and FIN-06 are required for real reuse. Shared WAHA/Brapi and owner recovery/compatibility gates remain open. Source age/polling/notification metrics are separate, with no guaranteed 60-minute notification. Historical baseline-only examples, source-absence statements and older sequencing below are not current defaults or working-integration claims.

## Current source-reconciled disposition — 2026-10-05

The original research below is a preserved pre-reconciliation record, superseded wherever it says audits absent/unknown or places all rules after history. Current authoritative [EVIDENCE](../../docs/references/EVIDENCE.md), [CONTRACTS](../../docs/contracts/CONTRACTS.md), [DESIGN-REVIEW](../../docs/architecture/DESIGN-REVIEW.md) and [ROADMAP](../ROADMAP.md) reflect ten fully read files and 23 CSV rows. Upstream findings are AUDIT_REPORTED, not runtime-verified.

- Brapi v2 stocks quote/history fields, 15,000 billing-cycle/one ticker/concurrency one, delta reset and sandbox separation now have report support. Reproduced sample reset/CSV variation arithmetic locally; previous-close date/basis/full adjustment method remain unknown.
- Proposed current quota policy is 30% reserve/10,500 routine and at most two Brapi attempts, superseding the historical 20%/three-attempt draft. Conditional audit scenario is 9,466 incl. 450 retry contingency, not an approved schedule.
- Catalog has 23 identities/provider classes; KNHY11 FII provisional needs official confirmation; quote coverage is not blanket historical coverage. Charged history samples only HGLG11/SNAG11, 64 bars including partial current day.
- ISO quote observation timestamp is not independently proven last-trade time. One session age is not SLA; x-brapi-stale=1 proposed ineligible even at ordinary age pending explicit policy.
- COTAHIST schema/annual readiness reported READY but security coverage PARTIAL; 245/010/PREULT/100 RAW close, adjusted NOT_READY, exporter absent. Full OHLCV/volume not proven. Keep close-only and separate vendor close/adjustedClose, no coalesce/concat or unsafe quote/SMA comparability.
- First price-target/outbound-WAHA monitor is Phase 4 with OPS/security/recovery. History/técnicos Phase 5 does not gate absolute price; all enabled inputs still require per-use eligibility/calendar/action evidence.

## Historical pre-reconciliation research (superseded status/policy/ordering)

**Date:** 2026-10-05, America/Sao_Paulo  
**Scope:** Planning only; quotes, quota, daily history, indicators, calendars and corporate actions.  
**Decision status:** All policies below are PROPOSED for human review.  
**Evidence status:** Provider capabilities are UNVERIFIED independently. No network access, API polling, sibling repository access, credentials or runtime changes were performed.

## Evidence and confidence

| Input | Evidence actually available | Confidence / limitation |
|---|---|---|
| Attached project discovery brief | Read in full | HIGH confidence about requested scope; not independent provider evidence |
| Repository README | Contains only project name | Provides no technical validation |
| Reports under `docs/references/` | Not supplied in this repository at review time | BLOCKER for provider contract verification |
| Ten referenced audit reports requested at supplied paths | Not available at those paths in the authorized repository, per current project evidence status | Layout, reuse boundaries and production readiness remain UNVERIFIED; filenames/content are not inferred |
| Brapi Free: 15,000 requests/cycle, one ticker/request, concurrency one, three months daily history, no intraday candles | Assertions in the brief attributed to a previous audit | BRIEF_ASSERTED; UNVERIFIED until sanitized reports are supplied |
| 23 initial assets returned quotes | Brief assertion; ticker identities and classes unavailable | Does not establish ongoing coverage or historical depth per instrument |
| Freshness varied from published delay; change fields inconsistent | Brief assertion from one observed session | Justifies conservative design; cannot establish a stable delay distribution |
| Quota calculations and indicator dependency counts below | Derived transparently from stated assumptions | HIGH confidence conditional arithmetic; not verified billing behavior |

The three-month historical window is not a verified number of bars. There is no verified current quote schema, timestamp unit, delay SLA, adjustment definition, historical finality marker, error charging policy, billing reset timestamp, rate limit per unit time, instrument identity mapping or trading calendar. Avoid inventing any of these from conventions.

## Proposed ownership and boundaries

- The Java monitor owns an explicit asset catalog, normalized quote observations, provider-specific daily series, indicator definitions/results, quota ledger, calendar versions and data eligibility decisions.
- The provider remains provenance for its payload values. Successful HTTP delivery establishes receipt only.
- Python owns its exported financial calculations and any independently generated official historical exports. COTAHIST historical reuse is a v1 priority under the updated planning direction, through a separate versioned historical-data contract. Java imports financial context through the financial snapshot contract; historical prices and financial calculations have separate readiness, provenance and eligibility.
- A scraper fundamentals snapshot must not become an intraday quote or OHLCV source. Do not change its TTL.
- Keep observation storage, validation, eligibility and rule evaluation separate within one application. A failed observation must not overwrite the last validated observation or silently refresh its timestamp.

## Quota and collection contract

### Conditional arithmetic

Let `A=23`, `P=30 minutes`, `R=scheduled collection rounds per trading day`, `D=trading days in the actual billing cycle`. Quote attempts before retries are `A × R × D`. Rounds mean scheduled observation instants; use a half-open session interval so opening/closing inclusivity does not add a hidden extra round. A separate closing collection must be counted explicitly.

| Illustrative schedule, not asserted exchange hours | Calculation | Quote requests |
|---|---|---:|
| 7 hours/day, 14 rounds, 22 trading days | 23 × 14 × 22 | 7,084 |
| 8 hours/day, 16 rounds, 23 trading days | 23 × 16 × 23 | 8,464 |
| 24 hours/day, 31 calendar days | 23 × 48 × 31 | 34,224 |

These are sizing examples. Exchange session lengths, eligible sessions per instrument, holidays, auction policy and provider billing-cycle boundaries must be confirmed. A calendar month must not be substituted for the provider's billing cycle.

With the asserted 15,000 allowance, 652 complete 23-asset rounds cost 14,996; another full round cannot fit. A proposed 20% protected reserve (3,000) leaves 12,000 planned attempts: at most 521 complete rounds costing 11,983, before historical collection and other consumers. The reserve is a local allocation policy, not an asserted provider guarantee.

The common roadmap sizing example uses the synthetic 8-hour half-open session: 8,464 quote calls + 23 × 23 = 529 daily historical calls + 23 bootstrap calls = **9,016 baseline attempts**, leaving 2,984 within the routine allocation for explicitly budgeted retries/diagnostics. Applying the same session to every day of a 31-day cycle yields 11,408 + 713 + 23 = **12,144** baseline attempts and already exceeds the routine allocation by 144. Neither example asserts real B3 session hours or a real calendar. Emergency-reserve use requires a reviewed explicit operational authorization/policy; retry and diagnostic work must not silently borrow it.

Example plan using the 7-hour scenario and assuming one separately charged historical request per asset: 23 bootstrap calls + 23 × 22 daily refresh calls = 529 historical calls. Quotes plus history = 7,613. A planning contingency equal to 10% rounded up adds 762 attempts, yielding 8,375, below the 12,000 planned allocation. Actual retries must also obey per-request caps and the persistent quota ceiling. Verify whether quote/history can share a charged request and whether a full three-month refresh is the only supported shape before adopting this estimate. If bootstrapping already replaces the first daily refresh, 529 is intentionally conservative.

### Proposed ledger and scheduler behavior

| Contract item | Proposed rule |
|---|---|
| Billing identity | Configure verified cycle start/end/reset semantics; cycle identity and limit are explicit records |
| Reset boundary | Recheck the actual dispatch cycle after queue waits; do not apply an old-cycle reservation to a new-cycle send without bounded transactional reconciliation |
| Charge accounting | Reserve one attempt transactionally before sending; conservatively consume it whenever sending might have occurred, including timeouts and ambiguous failures |
| Unknown outcome | Never refund based solely on lack of an HTTP response; reconcile only against verified provider accounting |
| Shared consumption | Dedicate the key to this monitor or explicitly allocate shared usage; manual tools and other services must count against the same cycle budget |
| Outstanding reservations | Include used and reserved units in ceiling checks; crashes after send cannot recreate an unused allowance |
| Hard capacity | Deny dispatch if used + reserved + requested would exceed the available allocation; preserve emergency reserve from ordinary scheduling |
| Projection | Recompute remaining planned rounds/history/retries against remaining allocation; expose predicted exhaustion before the next cycle |
| Admission | At most one active provider request across quotes, history, retries, admin actions and all application instances |
| Request throughput | Concurrency one does not imply one request per second. Minimum spacing and response-header semantics remain UNVERIFIED |
| Timeout/retry | Draft ceiling: maximum three total attempts per logical request (initial attempt plus at most two retries), not a retry entitlement. Only reviewed safe transient failures qualify; quota, round deadline, backoff/jitter and ambiguous-outcome accounting can reduce attempts to one or stop them entirely. Every retry requires new quota admission |
| 429 | Honor documented Retry-After when valid; stop shared collection until allowed; avoid a per-ticker retry storm |
| Authentication/plan rejection | Suspend collector and raise an operational alert; do not retry each of the 23 assets |
| Deadline | Drop expired polling work; skip or defer a new round while one is active; no catch-up burst after restart or downtime |
| Universe fairness | Rotate starting asset per round and expose per-asset last attempt/success and skips; failures must not starve later assets |
| Daily history | Separate budget/job from quote polls; proposed bootstrap and at most one scheduled refresh per eligible trading date, with bounded repair attempts |
| Calendar uncertainty | No unattended session polling or market-sensitive signal eligibility until the applicable calendar is validated |

An entire round takes the sum of 23 request durations, spacing and retries. Require a configured round deadline strictly below 30 minutes. Otherwise the cadence cannot be honored. Do not label all observations with the round start or claim a simultaneous 23-asset snapshot. If a future cross-asset rule is allowed, require a maximum timestamp skew and a coherent evaluation cutoff.

No scheduled job, HTTP client, quota ledger table or database migration is to be implemented during this planning milestone.

## Proposed quote observation contract

This is a conceptual field contract, not a claim that Brapi emits these names.

| Field group | Required semantics |
|---|---|
| Version / identity | `schemaVersion`, stable internal asset ID, provider symbol, requested symbol, provider-returned identity, exchange, instrument class, currency |
| Provenance | Provider and contract version, request/attempt/cycle IDs, payload hash or sanitized evidence reference, validation-policy version |
| Timing | `requestedAt`, `receivedAt` in UTC; exact original source timestamp value; parsed `sourceAsOf` when verified; timestamp meaning and unit; timestamp validity status |
| Trading context | `tradingDate` derived through verified mapping, exchange timezone, calendar version, applicable session ID and market phase |
| Values | Decimal quote price; previous-close value, its reference trading date and its price basis; currency and units; optional volume with explicit units |
| Price basis | `RAW`, `SPLIT_ADJUSTED`, `TOTAL_RETURN_ADJUSTED`, or `UNKNOWN`; provider definition/version; any adjustment factor/reference date |
| Validation | Structured reasons; no single ambiguous boolean. Identity, time, price, comparison basis, calendar and corporate-action statuses are independent |
| Freshness | `receivedAt - sourceAsOf` at ingestion and `evaluationAt - sourceAsOf` at rule time, when timestamp valid; transport age separately; policy/version and disposition |
| Derived change | Independent decimal change and percentage, reference observation/date/basis, calculation version, or null with a reason |
| Eligibility | Per use: display, absolute-price rule, percentage-change rule, historical indicator; accepted/degraded/quarantined and reason codes |

Never infer source quote freshness from `receivedAt`. Do not update the observation time when serving cached data. Preserve any provider change fields only as diagnostic inputs, never as rule operands.

### Validation and percentage change

Proposed core checks: exact catalog/provider identity mapping; expected currency and units; finite positive price; verified timestamp format/unit and meaning; no unexplained future time; valid trading-date/session interpretation; explicit price basis. Apply strict decimal parsing and documented rounding at presentation only. An absent optional volume can disable volume-dependent rules without automatically invalidating an otherwise verified quote.

Compute `absoluteChange = validatedPrice - validatedPreviousClose` and `percentageChange = 100 × absoluteChange / validatedPreviousClose` only if previous close is finite, positive, for the required prior trading date, and comparable under the same current adjustment basis. Two values both called raw are not automatically comparable across a split. Corporate actions or an unexplained reference-date mismatch must block the percentage-change rule even when an absolute-price rule remains eligible.

Disagreement with provider change fields is observable diagnostic evidence. It does not authorize replacing an invalid previous close with another provider's incompatible history. A local prior close can be used only through the same identity/date/basis validation contract, with provenance recorded.

### Freshness review

**Reject the assumption that the delay measured in one session is permanent.** The polling interval is a sampling interval; it is not a provider delay guarantee. Even with regular polling, quote age can grow by provider delay + scheduler skew + nearly one poll interval + transport time. Sequential collection creates a further cross-asset skew.

Proposed freshness is a versioned rule-specific policy with a supported timestamp meaning, market phase, maximum quote age during the required session, maximum clock skew, provider-lag evidence and a maximum observation age. Numerical thresholds remain review blockers until reports and synthetic boundary fixtures are available; do not silently default to 30 minutes.

- During trading, block market-sensitive rules when source time is missing, unparseable, implausibly future, outside the required session, stale for that rule, or has undocumented meaning.
- Outside trading, show the latest validated observation as an explicitly dated last-session value; a Friday observation need not be an ingestion error on Sunday. Closed-market state must not automatically reclassify old data as a current executable signal.
- Distinguish an unchanged price, a repeated timestamp, an illiquid asset and a failed collection. They are not equivalent. A repeated timestamp can remain displayable but must not manufacture a new event or prove live updates.
- Compare ages at evaluation time as well as ingestion. Disabling the collector must eventually make stored data ineligible for rules without deleting the last valid display value.
- Maintain per-asset lag and timestamp-regression metrics. Stop eligible evaluation on a changed timestamp contract until the adapter is reviewed.

## Proposed daily historical contract

Define a series key including asset, provider, interval `1d`, currency, OHLCV units, adjustment basis, adjustment-definition version and timezone/session-date mapping. Each bar records trading date, source timestamp (if supplied), first/last observation times, source revision/hash, raw and normalized values, provenance, calendar version and finality state.

Validate unique dates within a revision, monotonic normalized ordering, documented timestamp-to-date mapping, valid OHLC values (`low <= open/close <= high`, `low <= high`), nonnegative volume when present and correctly typed, and expected session coverage. Do not forward-fill missing bars into synthetic flat-price sessions. Never assume a UTC midnight timestamp encodes a Sao Paulo midnight; the provider may encode a date label rather than an instant.

Use explicit states `PROVISIONAL`, `FINAL_VERIFIED`, `FINALITY_UNKNOWN`, `QUARANTINED`. Current-session daily bars are provisional by default. A date in the past is not sufficient proof of finality.

### Finality gate

Before indicator evaluation, the daily bar must satisfy a documented provider finalization/revision contract or an explicitly reviewed operational confirmation policy. A proposed fallback policy can compare a completed-session bar in repeated post-close observations after a configured publication grace period; its assurance and limitations must be recorded. Repeated equality is evidence of stability, not proof that corrections can never occur. Calendar/time mapping and the grace period need source evidence. With neither verified finality nor an approved bounded confirmation policy, store the bar with `FINALITY_UNKNOWN` and suppress dependent technical alerts.

Preserve revisions instead of silently overwriting audit history. A revised input invalidates indicator results and rule assessments derived from that revision. Recompute dependent results, mark superseded evaluations, and avoid claiming that an already delivered alert was based on the corrected data. Alert correction/retraction behavior needs a rule-level decision.

### Raw/adjusted boundary

**Reject direct concatenation of adjusted and raw series.** Matching dates or ticker strings are insufficient. A comparable merge requires verified asset continuity, overlapping sessions, currency and units, timezone/date mapping, the same adjustment definition and reference date, and reconciled corporate actions. Different vendors' adjusted series may still use different event coverage or total-return methods.

Prefer one provider-specific eligible series for each indicator. Imported Python/B3 history is a separate series until compatibility is established. Do not multiply only close by an adjustment factor while leaving high/low/open unchanged for OHLC-dependent indicators. Volume adjustment also requires its own verified semantics. Unknown adjustment status blocks technical signals; a future reviewed raw-only rule may accept RAW series over an event-free window with an explicit discontinuity policy.

On a split, distribution or unexplained discontinuity: quarantine the affected comparison/window, seek bounded historical revision and reviewed event metadata, invalidate derived series as needed, then recompute with provenance. A large return alone does not prove a corporate action; preserve it as a quality warning, not an invented event. A sliding three-month provider window cannot be assumed to repair older adjustment history.

### COTAHIST reuse in v1

Prioritize reuse of the existing official B3 historical-ingestion capability in v1, contingent on the missing audits. Do not rewrite the Python ingestion/calculations in Java or require that project to run continuously. The proposed initial boundary is an independently generated, read-only, versioned historical JSON export imported by Java, distinct from the financial-context snapshot envelope. The exact export shape and validated reusable modules remain audit-dependent; no sibling repository inspection or export execution is authorized during planning.

Proposed historical export requirements: schema and producer version; asset identity and symbol validity; source identifier COTAHIST; original artifact identifier/checksum and revision; verified layout version and record mapping; interval and trading-date semantics; currency and price/volume units; adjustment basis and event metadata coverage; observation/reference/generation dates; per-series and per-bar readiness/quality; available date range, missing/unsupported records and documented limitations. The current repository does not independently verify COTAHIST's layout, fields, adjustment conventions or the existing ingestion's readiness. These required mappings describe a review contract, not factual claims about the source file.

Use imported eligible COTAHIST history as its own provider series for v1 daily-indicator inputs where audit evidence supports it. Brapi remains the proposed quote source and a separate recent daily series. Do not splice Brapi onto COTAHIST merely to extend it to the latest date, or substitute portfolio valuation exports for market history. Choose an eligible single series per indicator; disclose its latest completed trading date and gate quote comparisons on compatible basis and reviewed maximum age. A future reconciliation/normalization capability is optional; basic v1 reuse does not require merging providers.

Require offline sanitized fixtures proving layout-to-normalized mapping, instrument filtering, date/decimal/unit handling, duplicate/revision behavior, coverage gaps and adjustment semantics. Promote only the audited READY/acceptable-quality portions; PARTIAL, DIAGNOSTIC_ONLY and NOT_READY source products stay visible as context or diagnostic data. If validated COTAHIST reuse is not yet established, retain the explicit v1 blocker rather than silently replacing it with a new Java ingestion engine or declaring the capability a future milestone.

## Proposed technical-indicator eligibility

Examples below explain capabilities and dependency counts; they do not mandate this entire indicator list for the MVP.

| Indicator example | Mathematical minimum under the stated convention | Readiness caveat |
|---|---:|---|
| SMA N | N finalized closes | Daily-series crossing needs at least two eligible daily indicator outputs (N+1 closes for a daily close versus SMA N comparison). Sampled quote versus daily SMA crossing has a separate two-pair requirement below |
| Bollinger N | N finalized closes | Version multiplier, population/sample standard deviation and degenerate-band behavior |
| Wilder RSI N | N+1 finalized closes | Seed N price changes explicitly; recursive state and seed sensitivity need a reviewed warmup/convergence policy |
| ATR N using prior close | N+1 finalized OHLC bars with required prior close | Version true-range and Wilder initialization; consistent OHLC adjustments required |
| MACD 12/26/9 with SMA seeds | 34 finalized closes for first signal value | First slow EMA at close 26, nine MACD values through close 34; seed sensitivity persists after first output |
| SMA 200 | 200 finalized closes | Three calendar months cannot supply 200 daily trading bars; history extension/persistence is needed |

Mathematical computability is not full warmup. Specify indicator name/version, formula, input series ID/revisions, parameters, seed method, rounding, minimum bars, warmup policy, evaluation trading date and readiness/quality status. Require consecutive expected sessions for the selected window unless a separately reviewed missing-session policy allows otherwise. Zero denominator, flat-price RSI, insufficient bars and invalid numerics produce explicit statuses, not zero-valued fallback signals.

For EMA/Wilder indicators, a rolling three-month reseed can change results even if the latest price is unchanged. Persist sufficient compatible history or seed state with complete lineage, or clearly mark seed-sensitive computations until the agreed convergence gate is met. Do not assert a universal multiple of the period as guaranteed warmup. Gate with reproducible formula/seed fixtures and an explicit tolerance comparison against a longer reference series where authorized evidence exists.

Daily indicators use finalized daily inputs at a defined daily cutoff. A quote can support a separate absolute-price rule or an explicitly defined sampled-price versus daily-indicator rule. Repeated intraday quote polls must not create invented 30-minute OHLC candles or imply intraday candle indicators on the asserted Free plan.

Distinguish two crossing contracts. A daily-series crossing needs two eligible daily indicator outputs. A sampled quote versus daily SMA crossing needs two eligible sampled comparison pairs, each carrying quote identity/time/price/basis and daily SMA value/date/revision. Both pairs may use the same last completed daily SMA if the reviewed maximum age, calendar and quote-to-series compatibility gates hold; it does not inherently require a new SMA output between polls. Require an explicit maximum sample gap and policy for SMA-date/revision changes; absent that policy, a changed threshold or an ineligible interval establishes a new baseline rather than an unexplained crossing. The first eligible pair only initializes state. Define whether a change through equality counts as a crossing and version that policy with the rule.

Financial snapshots marked PARTIAL, DIAGNOSTIC_ONLY or NOT_READY remain contextual; passing a quote validation gate must not upgrade them to reliable fundamental signals. Historical portfolio valuation paths remain distinct from comparable market prices and technical indicator inputs.

## Calendar, asset identity and corporate actions

Propose a versioned exchange calendar with IANA timezone `America/Sao_Paulo`, instrument applicability, trading-date boundaries, holiday/special-session overrides, relevant market phases and source/reference version. Store instants in UTC while using the exchange-local trading date for sessions. Do not hardcode a constant UTC offset, weekday-only calendar or illustrative session hours from the quota examples.

Instrument classification (equity, unit, FII, FIAGRO) belongs to an explicit verified catalog; symbol suffix heuristics are not sufficient. Catalog changes must version symbols, validity intervals, exchange/currency and identity continuity. Rename, merger, liquidation, delisting or provider symbol reuse must not silently join histories.

Maintain separate metadata for corporate-action event date, effective trading date, adjustment factor, source, confirmed/unknown state and impacted series. No verified event feed is supplied. The initial milestone should prefer fail-closed rule eligibility around unresolved discontinuities rather than build an unvalidated corporate-action adjustment engine. Verified events/factors and a calendar source are review blockers for complete operational coverage.

## Conservative failure gates and objective verification

All proposed verification uses synthetic fixtures and offline examples. No API calls are authorized now.

| Scenario | Expected observable behavior |
|---|---|
| 23 tickers, one concurrent request, history and retry contend | One global admission path; no overlapping requests or duplicate reservations |
| Restart during ambiguous sent request | Its quota remains consumed/reserved conservatively; no free replay |
| Budget would enter reserve or exceed allowance | Dispatch denied; reason and remaining cycle allocation visible |
| Missed rounds after downtime | No catch-up storm; eligible next round only |
| HTTP 200 with wrong ticker/currency/nonpositive price | Observation quarantined; no affected rule event; last valid display value remains dated |
| Missing/incorrect-unit/future/regressing provider timestamp | Structured invalid/unknown time status; no fabricated current timestamp |
| Price unchanged but timestamp renewed / timestamp unchanged | Validity and freshness assessed separately; no synthetic crossing |
| Previous close from wrong session or split basis | Percentage change null with reason; no percentage rule evaluation |
| Weekend or special session | Versioned applicable calendar drives polling and market phase; closed value displayed with last trading date |
| Unknown calendar | Diagnostic storage allowed; scheduled session collection and market-sensitive signals blocked |
| Current daily bar or undocumented finality | Provisional/unknown status; dependent indicators and alerts suppressed |
| Missing/duplicate/out-of-order bar or adjusted/raw mixture | Series rejected/quarantined for the affected calculations; no gap filling or silent merge |
| Indicator has enough first-output bars but fails warmup | WARMING status; no alert promotion |
| Required history exceeds available depth | INSUFFICIENT_HISTORY; visible capability limitation, no shorter-period substitution |
| Provider revises finalized bar | Revision retained; dependents invalidated/recomputed with versioned evidence |
| Corporate action or unresolved discontinuity | Affected comparisons/indicators held until reviewed compatible series exists |
| Long sequential round | Stale task dropped; per-asset skew/skips exposed; no coherent snapshot claim |

Rollback for future phases: stop scheduled collection and rule eligibility, preserve normalized/raw sanitized evidence and quota accounting, restore the last reviewed adapter/policy/calendar/indicator version, and replay approved offline observations without dispatching external notifications. Never roll back quota usage or erase revisions to make a previous result appear current.

## Mandatory human-review blockers

1. Supply the sanitized Brapi audit and request/response fixtures; verify current plan constraints, endpoint charging and error accounting before enabling a collector.
2. Supply the 23 asset identities, classification evidence and verified historical availability per asset; quoted coverage alone is insufficient.
3. Specify provider timestamp format, units, time meaning, delay evidence and configurable rule freshness limits. One-session freshness is not a standing SLA.
4. Verify billing-cycle boundaries, any time-based rate limit, dedicated/shared key usage, reserve allocation and rounding/closing-round policy.
5. Select a verified session/calendar source and define auction/closing/special-session applicability.
6. Establish OHLCV adjustment definitions, previous-close basis, daily finality/revisions and a corporate-action/discontinuity policy.
7. Choose the MVP indicator/rule parameters, initialization, warmup, missing-session behavior and correction behavior. Long-window indicators remain unavailable until eligible history exists.
8. Supply the ten referenced reports at their authorized supplied paths and validate v1 COTAHIST historical reuse: existing ingestion readiness, layout/record mapping, units, dates, identity and adjustment semantics. Select the read-only export contract and eligible uses; maintain source separation and do not finalize missing audit-dependent decisions.

Planning can proceed around these fail-closed boundaries. Operational collection and trustworthy signals cannot be declared verified from the brief alone.

## Roadmap implications

**First: contracts and offline provider evidence.** Asset identities, quota accounting, timestamp/calendar mappings, adjustment and finality decisions are prerequisites. Retrieve the missing authorized reports and establish the v1 COTAHIST reuse boundary through audited offline fixtures. Do not enable runtime collection while blockers remain unresolved.

**Second: bounded quote collection and visibility.** Build serialized attempt admission, quote validation, independent changes and per-asset freshness before price rules. Preserve diagnostic/display-only observations and test crash/quota failure scenarios.

**Third: v1 official historical reuse, finalized daily series and indicator readiness.** Reuse the audited COTAHIST ingestion/export path, with its own provider series and explicit readiness. Build revision lineage, finality checks, compatible price bases, warmup and capability reporting before technical alert rules. Evidence gates this v1 work; extending or merging providers is not a prerequisite.

**Then: rule evaluation.** Rule eligibility consumes these contracts; it does not reinterpret missing timestamps, promote incomplete financial context or conceal insufficient history. Notifications must be triggered only by versioned eligible assessments.

## Sources and gaps

- User brief: `C:\Users\Rodrigo\.codex\attachments\1aeedccb-7cc6-4b6a-a480-55d2cce216cf\Texto colado.txt` (authorized attached request).
- Local `README.md` (project name only).
- Sanitized referenced audits, including the ten requested reports at supplied paths: absent from the authorized repository evidence. No external documentation, market calendar, provider API, corporate-action feed or proprietary datasets consulted.
- Derived arithmetic and formula dependency counts are planning reasoning, not claims about independently verified external systems.

Review of likely omissions: historical endpoint charging, late corrections, symbol identity changes, inactive/illiquid assets, shared token consumers, multi-instance concurrency, timezone/date-label distinction, corporate-action effects on previous close and volume, restart accounting, quota reset during a queued attempt, evaluation-time aging and daily indicator reseeding are explicitly covered. Numerical operational policies and external contract claims still require authorized evidence.
