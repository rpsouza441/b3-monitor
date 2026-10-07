# Phase 1 — Identity Gate Design Review (targeted, read-only)

**Revision:** 1 · **Date:** 2026-10-06, America/Sao_Paulo · **Type:** critical design review + decision proposal.
**Mutates nothing:** no requirement, ADR, contract, approval state, or asset disposition is changed by this
file. All 23 assets stay `activation_status=NOT_AUTHORIZED`; KNHY11 stays `QUARANTINED_CLASSIFICATION_PENDING`;
the four candidates stay `EVIDENCE_PENDING`. No phase advanced, no commit, no network/sibling/credential access.
This document is a proposal for human approval only.

---

## 0. Executive finding

**The block is not caused by any approved requirement, ADR, or contract.** It is caused by the
**01-02-03 plan-task disposition rule**, which over-reads two words and over-propagates one conflict:

1. It treats CAT-01's **"datable"** (capable of carrying a date) as **"a documented historical validity
   interval (`valid_from`/`valid_to`) is required"** — a stricter thing the approved text never demands.
2. It treats **any** unresolved **field-level** conflict (e.g. a CNPJ suffix, an administrator-vs-manager
   label) as a **whole-row** blocker, contradicting the approved contract rule that "**Conflicts/duplicates
   block only affected activation**" and "**Eligibility is per use, not a single valid flag**."

Consequence: four assets with strong, officially-sourced **current** identity are blocked as if they lacked
**historical** identity — conflating three genuinely distinct concepts the question names.

---

## 1. Exact requirement / acceptance gate creating the block

**Not blocking (approved text — these are correctly scoped):**

- **CAT-01** (`.planning/REQUIREMENTS.md:13`): *"validated **unique/datable** identity/type mappings activate
  independently … Full 23-active coverage remains unfulfilled until every asset is approved; no suffix/FIAGRO
  inference."* — "datable", not "historically dated". Activation is **per-asset and independent**.
- **CAT-02** (`:14`): *"Preserve identity changes and **historical** mappings … rename, delisting and ticker
  reuse do not overwrite old observations."* — this is the **only** home of *historical* mapping, and it is a
  **preservation** rule, not a precondition on current identity.
- **CONTRACTS** (`docs/contracts/CONTRACTS.md:7`): *"ON/PN/ISIN/CNPJ and identity validity require evidence
  rather than suffix inference. **Conflicts/duplicates block only affected activation.**"*
- **CONTRACTS** (`:17`): *"**Eligibility is per use, not a single valid flag.**"*
- **ADR-010**: bars *"invented ISIN/CNPJ/**date mapping**"* — i.e. prohibits **fabrication**, not acceptance of
  a dated-but-interval-less identity.
- **Q-02** (`docs/planning/OPEN-QUESTIONS.md:8`): Phase 1 target = *"**dated identity mappings**"* — a mapping
  bearing a reference/observation date, which the official primary evidence supplies.

**Actually blocking (the over-strict rule — NOT an approved artifact):**

> **01-02-03 plan-task disposition rule** (as applied in `SUBSET-SELECTION.md` Rev 3):
> a row is `DOCUMENTARY_SUBSET_VALIDATED` only if **(a)** unique mapping, **(b)** a **dated** mapping = *"an
> official effective date — not observed_as_of"*, **(c)** **no unresolved conflict**, **(d)** stated scope;
> else `EVIDENCE_PENDING`.

Criterion **(b)** silently promotes "datable / dated-reference" to "**official effective-date interval**", and
criterion **(c)** is applied at **row** granularity instead of **field/use** granularity. Those two readings —
and only those — produced 0/4. They live in the *plan task*, not in CAT-01/CAT-02/CONTRACTS/ADR-010.

---

## 2. Which safeguards are necessary vs. excessive

| Safeguard | Verdict | Why |
|---|---|---|
| Official primary evidence required for identity | **Necessary** | ADR-010/CONTRACTS; Brapi ≠ official. Keep. |
| No suffix/FIAGRO inference | **Necessary** | CAT-01/ADR-010. Keep. |
| No invented ISIN/CNPJ/date | **Necessary** | ADR-010. Keep. |
| KNHY11 independent quarantine | **Necessary** | ADR-010/Q-15. Keep, unchanged. |
| All activation `NOT_AUTHORIZED` until explicit human gate | **Necessary** | threat T-01-02. Keep. |
| **Historical `valid_from`/`valid_to` required for *current* identity acceptance** | **Excessive** | No approved text requires it for current identity; it belongs to CAT-02/historical ops only. |
| **Any field conflict fails the whole row** | **Excessive** | Contradicts "conflicts block only affected activation" + "eligibility per use". Should fail only the affected field/use. |
| **`observed_as_of` cannot be a *reference date* for current identity** | **Partly excessive** | Correct that it is not a *validity interval*; wrong if it also forbids recording it as the current-identity observation date (which CAT-01/Q-02 want). |

---

## 3. Smallest possible amendments (proposals — not applied)

The three concepts the question raises map cleanly onto **three independent acceptance states**. Proposed names
(not approved schema):

- **`CURRENT_IDENTITY_VERIFIED`** — official primary evidence of the instrument the ticker denotes *now*, with
  source provenance and an observation/reference date. Does **not** require `valid_from`/`valid_to`.
  *Acceptance:* official primary source (CVM/B3/issuer-official) establishing legal name + class + ticker↔
  instrument mapping, provenance recorded, reference/observation date recorded. Field-level `UNKNOWN` (e.g. a
  CNPJ) does **not** block if it is not essential to denote the current instrument (see §4).
- **`HISTORICAL_IDENTITY_VERIFIED`** — a demonstrated ticker→instrument mapping over a **dated interval**
  (`valid_from`/`valid_to`), supporting rename/delisting/reuse reasoning. *Acceptance:* official dated interval
  evidence; **fail closed** when the historical mapping cannot be demonstrated — never invent dates, never
  infer from today's observation. This is the CAT-02 concept, kept strict.
- **`OPERATIONAL_ACTIVATION_AUTHORIZED`** — explicit human scope approval to begin **prospective live
  monitoring**. *Acceptance:* a 01-04 scoped human decision (actor/date/scope); independent of history unless a
  rule itself needs history. Remains `NOT_AUTHORIZED` for all 23 until that human gate.

**Smallest amendments to realize this (all require human approval):**

- **01-02-03 plan task (the real fix, smallest):** split criterion (b) into *(b-current)* "dated **reference/
  observation**" (satisfied) and *(b-historical)* "dated **interval**" (only for historical ops); apply
  criterion (c) at **field/use** granularity, not row. This alone unblocks current-identity acceptance without
  touching any approved artifact.
- **REQUIREMENTS CAT-01 (one clause):** add "current-identity activation does not require a historical validity
  interval; historical mapping is CAT-02." (Clarifies, does not weaken.)
- **CONTRACTS (one sentence):** state explicitly that current-identity eligibility and historical-mapping
  eligibility are **separate** per-use flags (already implied by `:17`; make it explicit).
- **ADR-010:** no change needed; optionally a one-line note that quarantine/`no-invented-date` are unaffected by
  the split.

---

## 4. Existing evidence sufficient for *prospective* identity (no new collection)

Assessed against the proposed **`CURRENT_IDENTITY_VERIFIED`** bar, from `OFFICIAL-IDENTITY-SOURCES.md` only:

| ticker | current identity sufficient now? | basis | residual (does NOT block current) |
|---|---|---|---|
| **WEGE3** | **Yes** | CVM/RAD IPE: "WEG S.A. (B3: WEGE3)"; ON line; unique mapping; dated reference | CNPJ `UNKNOWN` — **not essential** to denote the current ticker's instrument (ticker+legal name+class+exchange already identify it uniquely); keep CNPJ `UNKNOWN`, do not invent. |
| **BPAC11** | **Yes** | issuer RI + CVM: Banco BTG Pactual S.A.; Unit = 1 ON + 2 PNA; unique composition; dated reference | CNPJ **suffix** conflict is a *branch/establishment* detail (matriz/filial `/0001-xx` vs `/0002-26`), **not** an issuer-identity conflict — the legal entity (root `30.306.294`, "Banco BTG Pactual S.A.") is unambiguous. Suffix stays `UNKNOWN`/pending; current identity holds. |
| **VRTA11** | **Yes** | B3/FNET + CVM AGO: "Fator Verità FII", CNPJ `11.664.201/0001-00`, FII, unique mapping | administrator vs. gestor are **different roles**, not a contradiction — a fund can have Banco Fator as administrator and Far – Fator Administradora de Recursos as manager. Record both roles as PARTIAL-pending; it does **not** negate current identity. Keep VRTA11 ≠ VRTM11 (`51.870.412/0001-13`). |
| **SNAG11** | **Partial** | issuer "Dados oficiais": SUNO AGRO FIAGRO IMOBILIÁRIO, CNPJ `28.152.777/0001-90`, FIAGRO — issuer-official but **not** CVM-primary | **Keep `OFFICIAL_PARTIAL`** until the CVM-filed Regulamento confirms exact legal name/CNPJ/class. Do not promote on issuer sheet alone. |

**So under the proposed split, 3 of 4 (WEGE3, BPAC11, VRTA11) already meet `CURRENT_IDENTITY_VERIFIED` with
existing evidence; SNAG11 stays PARTIAL.** None gains `HISTORICAL_IDENTITY_VERIFIED` or
`OPERATIONAL_ACTIVATION_AUTHORIZED` — those remain separate human/evidence gates.

> No promotion is enacted here. This is the *assessment* the human gate would act on.

## 5. Genuinely missing primary documents

| ticker | missing primary doc (local) | needed for |
|---|---|---|
| WEGE3 | CVM FCA / cadastro with CNPJ + dated identity | upgrade CNPJ UNKNOWN→VERIFIED; `HISTORICAL_IDENTITY_VERIFIED` |
| BPAC11 | CVM FCA / B3 issuer record pinning matriz CNPJ suffix | resolve suffix; `HISTORICAL_IDENTITY_VERIFIED` |
| VRTA11 | CVM registration / regulamento naming administrator + gestor + dates | resolve role attribution; dated interval |
| SNAG11 | CVM-filed Regulamento (exact legal name/CNPJ/class) | PARTIAL→VERIFIED current; dated interval |

All four historical intervals (`valid_from`/`valid_to`) remain genuinely **absent** and must stay `UNKNOWN`
(fail closed for historical ops). None is required for *prospective-only* current identity.

## 6. Impact on CAT-01, CAT-02 and the 8-phase roadmap

- **CAT-01:** the amendment *clarifies*, does not weaken — current-identity activation becomes possible for
  independently-evidenced assets (its own stated intent), while "full 23/23" stays unmet and KNHY11 stays
  quarantined. Threat T-01-02 mitigations all retained.
- **CAT-02:** **strengthened by separation** — historical mapping gets its own fail-closed state
  (`HISTORICAL_IDENTITY_VERIFIED`) instead of being silently merged into CAT-01. Rename/delisting/reuse logic
  (01-03) is unaffected and still requires dated intervals.
- **Roadmap (Phases 2–8):** no structural change. Prospective quote monitoring (Phases 3+, QUO-01/02) depends on
  **current** identity + `OPERATIONAL_ACTIVATION_AUTHORIZED`, not on history — so the split removes an
  artificial Phase-1 stall without granting any activation. History-dependent features (COTAHIST/HIS-04,
  indicators needing series) continue to require `HISTORICAL_IDENTITY_VERIFIED`. No phase is advanced here.

## 7. Formal decision proposal (for human approval)

> **DP-01 — Separate current identity, historical identity, and operational activation into three independent
> acceptance states.**
>
> **Approve to:**
> 1. Amend the **01-02-03 disposition rule** so "dated" means *dated reference/observation* for current
>    identity and *dated interval* only for historical ops; evaluate conflicts at **field/use** granularity.
> 2. Add one clarifying clause to **CAT-01** (current identity ⇏ historical interval) and one to **CONTRACTS**
>    (current vs. historical eligibility are separate per-use flags). ADR-010 unchanged (optional one-line note).
> 3. Introduce the three proposed states (`CURRENT_IDENTITY_VERIFIED`, `HISTORICAL_IDENTITY_VERIFIED`,
>    `OPERATIONAL_ACTIVATION_AUTHORIZED`) as named schema, pending approval.
>
> **Effect if approved (to be enacted by a *separate* authorized task, not now):** WEGE3, BPAC11, VRTA11 would
> be eligible for `CURRENT_IDENTITY_VERIFIED` on existing evidence; SNAG11 stays PARTIAL; all four stay
> `OPERATIONAL_ACTIVATION_AUTHORIZED = NO`; KNHY11 stays quarantined; `valid_from`/`valid_to` stay `UNKNOWN`.
>
> **Explicitly NOT in this proposal:** no asset promoted to validated/authorized now; no approved contract
> auto-edited; no fabricated dates; no external collection; no phase advance.

<rollback>This review changes no state; superseding it needs only a newer review revision. Any amendment it proposes takes effect only through a separately authorized edit to the named artifact, with its own hash/consistency checks.</rollback>
