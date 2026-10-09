# Cycle 20 — Review Evidence

**Scope:** close the remaining verification/recoverability gaps after Cycle 19 — the PostgreSQL runtime gate,
the HTTP chunked body-size contract, and packaging/evidence integrity. No new product scope. No push. Docker
absent → PostgreSQL IT **NOT_RUN**. The seven protected inputs are unchanged.

## 1. Git state (verified)

- **Branch:** `checkpoint/cycle7-reviewed`
- **Preflight HEAD:** `23ded8c` (the prompt's "starting HEAD `6fb874c`" is one commit behind — `23ded8c` is the
  cycle-19 handoff-reorg commit that is the real tip; verified, not assumed).
- **Cycle-20 commits:** `1d2e948` (chunked-413 test), then the docs/evidence commit (this), then the final
  ZIP-rebuild is not committed (the ZIP is gitignored).
- **Divergence:** `git branch -r` = only `origin/main`; **ahead, 0 pushed**. Tree clean at close.
- **Flyway:** V1–V15 (unchanged).

### Seven protected hashes — identical to every prior cycle (SHA-256 prefix)
`536C21E8…` PROJECT · `B18E8A92…` REQUIREMENTS · `7798704B…` ROADMAP · `15378F08…` CONTRACTS ·
`9D698EDB…` SECURITY · `88E6412C…` ARCHITECTURE · `81E946B7…` ADRS.

## 2. PostgreSQL runtime gate — **NOT_RUN**

Docker CLI is not installed on the host (`Get-Command docker` → none; `docker info` → no server). Per the
guardrails, nothing was installed and no host infrastructure was changed. `mvn verify -Pdocker-it` was **not**
run; runtime PESSIMISTIC_WRITE remains **NOT_PROVEN**.

The ITs that would execute under Docker are present and compile:
- `OutboxPostgresIT.concurrentServiceCommitSameDocumentYieldsImportedAndNoop` — concurrent same-document
  commit through the real service → exactly one `IMPORTED` + one `IDEMPOTENT_NOOP`, one durable row, no 500.
- `OutboxPostgresIT.maxNumericRoundTripsExactlyOnRealPostgres` — exact `NUMERIC(24,12)` round-trip.
- `OutboxPostgresIT.concurrentSameSnapshotCommitYieldsExactlyOneDurable` — raw unique-constraint race.
- `OutboxPostgresIT.analyticsSnapshotPersistsAndIsUniqueOnRealPostgres` + structured context metrics (incl.
  quality) via `AnalyticsContextMetricEntity`.
- `LifecycleFencePostgresIT` — transaction-bound `pg_backend_pid()`/`pg_blocking_pids()` lock-wait proof.

The concurrent same-/different-document dispositions and SUCCESS-audit atomicity are proven **on H2** at the
service level (deterministic): `AnalyticsImportHardeningTest.loserOfSameDocumentRaceGetsIdempotentNoop` /
`loserOfDifferentDocumentRaceGetsConflict`. The real-Postgres aborted-transaction semantics that the P1-A fix
targets can only be executed where Docker exists — honestly recorded as NOT_RUN.

## 3. HTTP chunked body-size semantics — contract DETERMINED, DOCUMENTED, and proven

The cycle-19 test accepted `413 | 401 | 403`, which proved *rejection* but not a *deterministic 413*. The
required contract is now explicit (and encoded in `AnalyticsImportSizeLimitEndpointTest`):

1. **Declared `Content-Length` > MAX ⇒ deterministic 413**, before Spring Security and before
   `@RequestBody byte[]` materialization. The normal case (well-behaved clients send Content-Length) and the
   strong guarantee. Test: `declaredOversizeContentLengthRejectedBeforeMaterialization`,
   `oneByteOverRejected`; `exactlyAtLimitAcceptedByTransport` pins the boundary.
2. **Chunked / unknown-length body that REACHES import handling (authenticated + CSRF) ⇒ deterministic 413**
   from the bounded stream the instant more than MAX bytes are read, and **nothing is imported**. Proven at
   the real endpoint with a browser-style session (CSRF token harvested from the login page, sent as the
   `X-CSRF-TOKEN` header so the CSRF filter never reads the body) +
   `assertEquals(before, snapshots.count())`. Test: `authenticatedChunkedOversizeIsDeterministic413AndNotImported`.
3. **Chunked body NOT authorized to reach import handling ⇒ security-first 401/403** before the body is read.
   Spring Security's CSRF filter runs before the controller materializes the body, so a token-less/anonymous
   oversize chunked POST is rejected without materialization. This is correct and was **not** weakened to force
   a 413. Test: `unauthenticatedChunkedOversizeIsSecurityFirstRejection`.

The bounded-stream mechanism itself (abort past MAX; chain never invoked for declared oversize; at-limit
drains) is proven directly in `AnalyticsImportSizeLimitFilterTest`. **Disposition:** oversized chunked content
can never be materialized or imported on any path; a deterministic 413 is guaranteed for every request that is
authorized to reach import handling and for every request that declares its size.

## 4. Packaging & evidence integrity

- **ZIP builder is now POSIX-explicit:** entries are created with `ZipArchive.CreateEntry("<forward/slash>")`
  computed from the git-relative path with `\` → `/`, so entry names use `/` regardless of the Windows host —
  not left to the directory walker. Independent Python `zipfile` validation (reads stored names verbatim):
  **0 backslash, 0 absolute/`..`, 0 nested-zip, 0 real `.env`/`.git`/`target`/`.kiro`, 1 `.env.example`,
  15 Flyway migrations, 0 obsolete active handoff at the top level** (the active handoff lives under
  `archive/`). `testzip()` → None.
- **Test log in the package:** `docs/handoff/test-evidence-cycle20.log` (the full `mvn test` = 294 run) is
  **included in the ZIP** this cycle, so the review package carries first-party test evidence rather than only
  a referenced on-disk file.
- **Cycle-19 discrepancy corrected:** `archive/cycles/CYCLE19-REVIEW-EVIDENCE.md` previously read
  "final HEAD `dab1767` / 51 ahead" (the last commit visible while that doc was being written — a docs commit
  cannot name its own hash). It now records the authoritative closure `6fb874c` / 52 ahead, plus the later
  housekeeping `23ded8c` / 53. Final handoff, this evidence, and Git metadata agree.

## 5. mvn test & log

**294 passed, 0 failures/errors/skipped** (was 293). Full log: `docs/handoff/test-evidence-cycle20.log`
(included in the ZIP). The +1 is net: the cycle-19 `chunkedOversizeRejectedDeterministically` (413|401|403) was
replaced by two sharper endpoint tests — `unauthenticatedChunkedOversizeIsSecurityFirstRejection` and
`authenticatedChunkedOversizeIsDeterministic413AndNotImported`.

## 6. Remaining findings

- **P0:** none.
- **P1:** none open. The three cycle-18 P1s remain fixed; their real-Postgres execution is the only pending
  verification (gated on Docker, NOT_RUN).
- **P2:** none open. Legacy NULL-digest identity is fail-closed (409).

## 7. Real-producer readiness

The consumer boundary is **code-complete and adversarially hardened** (full-document token binding, exact
NUMERIC(24,12) bounds, lossless structured context, strict nested schema, transaction-sound concurrent commit,
deterministic body-size enforcement, fail-closed legacy identity). It is **NOT yet certified for real-producer
integration**, because two gates remain:
1. the PostgreSQL runtime proof is NOT_RUN (Docker absent) — the transaction-abort and lock-wait invariants
   must execute on real Postgres; and
2. the 11-point producer acceptance gate requires explicit, separately-authorized `projecao-carteira`
   sibling changes (shared canonical-checksum golden test, real exporter artifact, reconciliation) — not
   requested or performed here.
Until both pass, status stays `CONSUMER_VERIFIED_SYNTHETIC` / runtime `NOT_VERIFIED`.

## Unchanged / still gated

SEC-01 DONE, UI-03 DONE; UI-01/UI-02 PARTIAL; RUL-03/RUL-05 PARTIAL; Q-19 unresolved. All 23 assets
NOT_AUTHORIZED; workers disabled; no real producer snapshot; phase closure 0/8. Prohibited and untouched:
push, projecao-carteira/ticker-scraper, live Brapi, real WAHA, recipients, OPERATIONAL_ACTIVATION, production
DB/deploy, trading, LEVEL/false-confirmation/cooldown while Q-19 open.
