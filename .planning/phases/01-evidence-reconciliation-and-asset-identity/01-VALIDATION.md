---
phase: 1
slug: evidence-reconciliation-and-asset-identity
status: draft
nyquist_compliant: false
wave_0_complete: false
created: 2026-10-06
---

# Phase 1 — Validation Strategy

**PARTIAL_DOCUMENTARY_EXECUTION (2026-10-06).** Only tasks01-01-01 and01-01-02 were authorized and executed; their bounded integrity/traceability checks passed. Actual evidence is in docs/planning/phase-1/EVIDENCE-BASELINE.md and01-01-SUMMARY.md. Other eight tasks, case traces, human approvals and runtime tests remain unexecuted. Full phase acceptance remains pending.

## Validation system

Documentation-only phase: existing PowerShell, Node and installed GSD CLI suffice. No app test framework, dependency installation, external service, database or Wave0 implementation is needed. Later persistence/runtime identity verification belongs to its separately authorized implementation scope. A paper trace does not prove implemented CAT-02 behavior.

Planning quick checks: `frontmatter validate <PLAN> --schema plan`, `verify plan-structure <PLAN>`, `verify references <PLAN>`.
Planning full checks: `query check.decision-coverage-plan <phase-dir> <context>`, `query validate.consistency`, `query validate.health`, `phase-plan-index 1`, `gap-analysis --phase-dir <phase-dir>`.
Canonical CLI: `node C:/Users/Rodrigo/.codex/get-shit-done/bin/gsd-tools.cjs`.

Future documentary checks: exact source hash/ID sets; exact23 catalog ticker set; selected/validated/approved subset relationships; dated identity invariants; explicit human scope and outcomes. Pattern presence checks in PLAN files are smoke checks; they never establish set equality, chronology, official evidence, independent case results or human approval.

## Task verification map

| Task | Owner | Wave | Automated smoke/integrity check | Required acceptance beyond smoke | Expected failure / response | Execution |
|------|-------|------|--------------------------------|----------------------------------|-----------------------------|-----------|
| 01-01-01 | GOV-01 | 1 | Source IDs/status fields present | Exactly10 unique IDs/path/hash/section match prior register, scoped evidence labels | Missing citation => CITATION_PENDING | PASS — documentary task; sign-off pending |
| 01-01-02 | GOV-01 | 1 | Get-FileHash SHA256; GSD consistency | 10/10 equals registered hashes;23 identities without source edits | Hash mismatch => INTEGRITY_BLOCKED; no dependent approval | PASS — hash/byte/set and23-row equality only |
| 01-02-01 | CAT-01 | 2 | Matrix fields/quarantine present | Exact23 ticker set, no duplicate, UNKNOWN dates retained, all activation NOT_AUTHORIZED | Ambiguous row => EVIDENCE_PENDING | PENDING |
| 01-02-02 | CAT-01 | 2 | Selection/status fields present | Explicit human candidate-only list or hold; members unique/in catalog; KNHY11 excluded | No/ambiguous response => blocking checkpoint | PENDING |
| 01-02-03 | CAT-01 | 2 | Evidence/activation status fields present | Validate each selected dated source independently; validated subset-of-selected; exact counts; not full23 | Unsupported date/type/identity => affected row pending | PENDING |
| 01-03-01 | CAT-02 | 2 | Mapping/ineligible fields present | Immutable IDs/revisions, proven half-open intervals, unknown not infinity; current contract precedence | Overlap/unknown => reject lookup, no merge | PENDING |
| 01-03-02 | CAT-02 | 2 | Six synthetic case IDs/results present | Independent expected/actual date-boundary and retained O1 traces IC01–06; explicit documentary limitation | Invariant failure => FAIL and block acceptance | PENDING |
| 01-04-01 | GOV-02 | 3 | GSD consistency/health; packet fields present | Four acceptance rows;57IDs/owners preserved; all unsigned gates pending | Missing dependency => NEEDS_REVIEW | PENDING |
| 01-04-02 | GOV-02 | 3 | Actor/date/revision/scope fields present | Explicit scoped decision; approved subset-of-validated; KNHY11 official evidence mandatory | Silence/blanket unsupported approval => checkpoint pending | PENDING |
| 01-04-03 | GOV-02 | 3 | GSD consistency/health/roadmap; diff check | Current actual outcomes, full CAT-01/Phase1 still pending on partial coverage; other53 unaffected; no auto/commit | Parser/criterion conflict => hold progression | PENDING |

## Future reproducible assertions

Run only after separately authorized documentary execution creates the relevant artifacts. No generic text match grants acceptance.

- Source integrity: enumerate the ten exact paths already registered in EVIDENCE, compare SHA256 and distinct ID/path sets. A mismatch must fail the task and block downstream review.
- Catalog sets: extract the explicitly designated23 rows, compare requested/returned tickers with the immutable prior catalog/CSV. Reject duplicates, extra/missing members and any mismatch. Record parsed row counts and actual difference sets.
- Membership: record selected, validated and approved as separate explicit sets. Validate `validated ⊆ selected ⊆ catalog` and `approved ⊆ validated`; assert KNHY11 absent while official gate pending. Show nonselected and selected totals sum to23. Never derive approval from file presence.
- Mapping cases: use synthetic timestamps ordered t0<t1<t2. Calculate every interval membership independently of the expected column; record actual versus expected. At t1, ended [t0,t1) must reject; a proven new [t1,t2) may accept. Unknown chronology and overlaps reject. O1 retains original asset/mapping revision after rename/reuse/correction.
- Governance: compare57IDs and owner rows to pre-execution baseline; only the four in-scope statuses may change, and only after full criterion acceptance. Record actor/date/artifact digest/exact scope; no unsigned approval.
- Preserve input bytes: hash originals before/after. Do not edit the source register to make a failing source pass.

## Sampling and feedback

Each task has a smoke check followed by its semantic/manual acceptance. Run checks after each changed documentary artifact, the task-specific checks before its completion, and GSD consistency/health at closeout. No three consecutive tasks lack verification. Proposed feedback budget: local smoke <=60 seconds; integrity/full checks <=60 seconds for these small artifacts, measured during future execution rather than promised. Human checkpoints have no timeout approval.

## Manual-only gates and scope limits

Human subset membership, official KNHY11 evidence interpretation, dated real identity evidence, independent case tracing and scoped approval require review; automation cannot supply them. External searches are prohibited in this task: list the missing official artifact, do not fetch it. No candidate asset is preselected. User statement “knhy11 é fii de papel” is USER_REPORTED.

A named evidenced subset may receive documentary/downstream scope approval only through an explicit human exception; this is not full CAT-01, full Phase1 or23/23 completion, live activation, or implementation authorization. Broader policy defaults remain open in their existing questions.

## Validation sign-off

- [ ] Future ten-source integrity and23-row/subset assertions executed.
- [ ] Six documentary identity traces executed and limitations reviewed.
- [ ] Human selection and scoped acceptance recorded.
- [ ] Full phase success criteria independently evaluated; partial debt retained.
- [ ] Later runtime identity verification completed where required.
- [x] Task acceptance design present; historical planning checks recorded in 01-REVIEW.md (renamed during controlled01-01execution because the installed scanner counted the old report name as a plan).
- [ ] nyquist_compliant: true / wave_0_complete: true — not asserted by this planning-only run.

Only Plan01-01 documentary execution evidence exists; phase-wide draft flags remain intentional. The ten-source hashes and23-row documentary equality passed; subset assertions and approval remain unexecuted, so their combined sign-off checkbox stays unchecked. Do not manufacture other-plan summaries or a passed Phase1 VERIFICATION artifact.
