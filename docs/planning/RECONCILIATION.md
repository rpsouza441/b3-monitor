# Critical planning correction — human review packet

Updated 2026-10-06. This supersedes the planning proposal's missing functional/lifecycle/integration/latency details; it does not supersede audit evidence or approve implementation. Earlier reconciliation read all ten reports and23CSV rows, recorded original hashes, and distinguished AUDIT_REPORTED from LOCAL_VERIFIED. Those source files remain unchanged. [Evidence](../references/EVIDENCE.md), [catalog](ASSET-CATALOG.md), [reuse registry](PYTHON-REUSE.md).

## Exact documentary changes and requirements

All original52 v1 IDs retained;57 now, all Pending, one owner each. Added **RUL-06** percentage alerts, **RUL-07** bounded AND/OR, **IND-04** EMA9/21, **IND-05** finalized daily abnormal volume, **FIN-06** actual separately authorized Python producer/exporter. **FUT-05** volatility is an explicit v2 proposal pending scope approval, outside the57count. Revised 14 existing v1 IDs: CAT-01, SYS-01, SEC-02, MKT-01/03, QUO-02, RUL-01/03/05, IND-03, NOT-02, OPS-01/02 and FIN-01. FUT-03 explicitly lists the retained v1 indicators; existing date/basis/privacy/outbox requirements remain binding.

PROJECT/REQUIREMENTS/ROADMAP/STATE, ADR-004/009/011 and affected operational ADRs, new ADR-012–015, CONTRACTS, SPEC, OPEN-QUESTIONS and this packet align the criteria. Supporting architecture, Python reuse, testing, overview/research and validation documents avoid stale integration/default/count claims. [Fresh critical review](CRITICAL-REVIEW.md) checks remaining dependencies/disagreements rather than certifying runtime behavior.

## Functional scope and revised phase ownership

| Phase | Owners | Delivery / necessary gate |
|-------|--------|---------------------------|
| 1 | 4 | GOV/CAT evidence and reviewed identities; KNHY11 individually quarantined. A named valid subset needs explicit downstream scope approval; full23 coverage not claimed |
| 2 | 5 | Java/PostgreSQL durable secure foundation, pinned compatibility matrix and shared WAHA isolation plan |
| 3 | 10 | Quote/calendar/action/quota per-use eligibility, source-time metrics and owner account allocation |
| 4 | 14 | Single-leaf absolute-price MVP, both approved lifecycle modes, PostgreSQL/outbox→WAHA; OPS/security/backup before enablement |
| 5 | 11 | 5A percentage/AND-OR after Phase3 input gates and4 state; no history prerequisite. 5B eligible SMA/RSI/EMA9/21/volume requires series/seed/unit/finality assurance |
| 6 | 4 | SMTP ambiguity/fallback and separately authenticated/replay-safe commands |
| 7 | 6 | A synthetic Java financial consumer; B authorization-blocked real Python producer/fidelity. Full real reuse cannot complete on A alone |
| 8 | 3 | Full dashboard and repeated release controls; synthetic UI can proceed, full-v1 integration claim requires FIN-06 or formal scope revision |

EMA9/21 and volume are v1 proposals because they implement requested trend/activity signals through bounded eligible daily inputs; this does not establish Python/library/data readiness. Volatility is explicitly proposed v2 because return basis, window, sampling/annualization and action semantics are unspecified and require separate risk/history assurance. Owner must accept or revise this deferral. Technical formula/unit fixtures are required before enabling, not assumed complete.

Percentage requires independently verified previous trading close date, positive denominator, compatible basis/security/actions. Provider change fields are diagnostics only. Composition is same-asset typed AST, proposed8leaves/depth4, all leaves eligible at enablement and runtime or rootUNKNOWN evenOR. This conservative completeness choice and mixed-time comparison bounds need approval.

## Initial-alert semantics and trade-offs

CROSSING suppresses initially true conditions and alerts on an observed eligible transition; this avoids initial bursts but may miss an existing condition. LEVEL optionally consumes one initial TRUE token, then uses the same episode latch/false-confirmation/hysteresis/cooldown as CROSSING. It reveals existing conditions with possible initial bursts. Default remains **UNSELECTED** pending Q-19; no automatic CROSSING default.

The [state contract](../contracts/CONTRACTS.md) specifies atomic initial consumption/event/outbox, unique logical/recipient keys, distinct input confirmations, edit revision and stale-intent invalidation, cross-revision cooldown, ordinary resume without replay, explicit LEVEL resume only after prior episode ended, and recovery quarantine when state is uncertain. Gaps/corrections/cooldown expiry do not mint episodes; no external exactly-once promise.

## Actual Python calculation reuse versus synthetic consumer

A/FIN-01: versioned tested Java consumer, CONSUMER_VERIFIED_SYNTHETIC for artificial fixtures. B/FIN-06: actual authorized public-input Python producer/exporter, calculation/schema/producer versions and sanitized independent goldens/fidelity followed by Java import. Only A+B can establish INTEGRATION_VERIFIED_REAL for proved metrics/assets. B currently PRODUCER_ABSENT / BLOCKED_EXTERNAL_AUTHORIZATION. No upstream changes/private portfolio access authorized; no claim a DTO makes calculations work.

Graham PARTIAL (BRL/security), FII P/VP snapshotREADY/historyPARTIAL (ratio), B&H PARTIAL (0–100 with coverage) retain original readiness/meaning. Bazin/common-core state-only no usable numbers. XIRR/TWR/holdings/PM/cash flows/account data excluded. No conversion into investment recommendations; FIN-04 context-only remains human scope decision. Full Phase7/real financial v1 reuse blocked until B or formal human scope revision; independent price MVP unaffected.

## Freshness and latency correction

Age of source observation, nominal30min polling, scheduling lateness and notification acceptance/confirmed-delivery times are separate. A45min age guard plus30min next-poll allowance already gives a conservative75min budget before processing/queue, while revalidation can suppress stale intents and provider visibility/true event time is unknown. This cannot guarantee <=60min notifications and does not promise75min delivery.

Proposed internal best-effort goals:95% evaluated quote ages<=5min;99% admitted slots<=1min late;95% eligible intents commit→ACCEPTED<=2min and source→ACCEPTED<=10min. Exact7-day window/min30 samples and denominators/failures/skips/unknowns/degraded states are in CONTRACTS; targets need Q-24 approval.45min is a hard eligibility proposal, not delay SLA. Receipt absence remainsUNKNOWN; no invented provider SLA or silently excluded failed alerts.

## Operational blockers and owner decisions

- Existing shared WAHA: scoped session/credentials/routes/network/rate/pause; do not start/stop/logout/reset/configure unrelated consumers. Exact edition/version/engine/outbound auth/send/response/receipt/idempotency contract is a later live gate. If isolation cannot be enforced, separately approve an alternative boundary.
- Brapi sharing with ticker-scraper is possible from classification/retry evidence, not confirmed. Owner account allocation or shared ledger/global admission/concurrency coordination needed; token separation/local serial worker insufficient.
- KNHY11 remains independent classification quarantine; official evidence needed for its activation, not to stop other explicitly reviewed valid assets.
- Owner must approve monitor backup destination/encryption/key custody/retention/high-water/RPO/RTO/restore authority. Draft daily/RPO24h/RTO4h is not approved; shared WAHA state outside monitor restore.
- Pin/verify JDK/Boot/build/PostgreSQL/library/license/API/decimal/seed/correction compatibility; scraper stack not inherited.
- Live price activation additionally requires calendar/action/manual target/stale/age/mode/rearm/recipients/quota policies and future synthetic end-to-end/recovery/security evidence. Percentage/technical/financial/inbound surfaces have independent later gates.

## Preserved source divergences and remaining disagreements

Brapi audit arithmetic remains22absolute mismatches and7internal percent differences; BPAC11~−0.012058% versus reported+25.6%. previousClose date/basis/actions unproved. COTAHIST RAW close-only/securityPARTIAL, adjustedNOT_READY, Brapi adjustment methodology incomplete: no automatic stitching. Scraper no cache-only HTTP/GET may refresh, adapter disabled. Python no technical indicators/exporter; audit suite setup failures not global readiness. KNHY11 FII proposal versus unsupported Python FIAGRO grouping unresolved officially. One-session age samples/publicized delay do not constitute SLA.

Unresolved human disagreements are explicit: initial CROSSING/LEVEL default and opt-ins; volatility v2 versus original requested scope; conservative OR-UNKNOWN policy/AST bounds; financial context-only versus automatic purchase/sale criteria; full real financial v1 dependent on separately authorized producer versus consumer-only delivery; partial catalog downstream gate versus full23 completion;45min coverage tolerance versus shorter notification expectations; ownership/isolation/quota/backup assumptions. [Q-01–27](OPEN-QUESTIONS.md) identifies evidence and approval owners.

## Verification and stop boundary

[PLANNING-VALIDATION](PLANNING-VALIDATION.md) records fresh GSD consistency/health, ownership/dependency/link/source integrity checks and their limits. No application tests, provider calls or upstream assurance are inferred. Eight phases, zero executable plans, zero executed phases; all57v1 requirements Pending. Work stops after documentation/verification. Human review of this packet does not automatically authorize implementation, migrations, services, sibling access, credentials, commit or deploy.
