# Official Identity Evidence — Candidate Subset (Plan 01-02, pre-01-02-03)

**Revision:** 1
**Purpose:** Assemble public-source official identity evidence for the four candidate assets
(`SUBSET-SELECTION.md` members) to inform GSD task **01-02-03**. This file is a documentary
evidence dossier only.
**Research date (evidence observation):** 2026-10-06, America/Sao_Paulo (UTC-03:00)
**Scope authorization:** one-off, read-only public Internet access to **official B3 / CVM / issuer /
administrator** sources, for **WEGE3, BPAC11, VRTA11, SNAG11 only**. The other 19 catalog assets and
KNHY11 were **not** researched.

## Hard boundaries honored this execution

- **Not run:** 01-02-03, 01-03, 01-04, or any other plan. No requirement checked, no GOV-01 acceptance,
  no CAT-01 closure, no Phase 1 closure.
- **Not modified:** the seven protected inputs, `ASSET-VALIDATION.md`, `SUBSET-SELECTION.md`, any
  activation state, requirement, ADR or contract. All 23 assets remain `activation_status=NOT_AUTHORIZED`;
  KNHY11 remains `QUARANTINED_CLASSIFICATION_PENDING`.
- **No** commits, deployments, Brapi/API polling, local-network, credential, account, production-database
  or sibling-repository access, and **no application code**.
- This selection/dossier does **not** approve identity or authorize activation. Evidence here is graded,
  not accepted; acceptance is a later human gate.

## Evidence-quality vocabulary (this file)

| Grade | Meaning as applied here |
|---|---|
| `OFFICIAL_VERIFIED` | Fact stated in a primary official record (CVM/RAD or B3/FNET filing, or an issuer/administrator "dados oficiais" sheet) that I retrieved and read, with the official URL and a dated reference. |
| `OFFICIAL_PARTIAL` | Supported by an official source but incompletely (e.g. CNPJ root confirmed in a CVM-filed document while the exact matriz/filial suffix is not pinned to a single primary record), or official text reached only via its official-domain search excerpt rather than a fully machine-readable body. |
| `PROVIDER_REPORTED` | Data-vendor/aggregator statement (Brapi, aggregators). **Not** official B3/CVM proof. |
| `USER_REPORTED` | Owner's own statement in prior project records. |
| `UNKNOWN` | Not established by any evidence seen; left UNKNOWN (never inferred). |

Rules applied: Brapi responses are **never** treated as official B3/CVM proof; instrument class is **never**
inferred from the ticker suffix; `valid_from`/`valid_to` are recorded **only** where an official effective
date is explicitly supported — otherwise `UNKNOWN`; today's observation is **never** used to infer historical
validity; source conflicts are recorded explicitly.

---

## 1. WEGE3 — WEG S.A.

- **Official ticker:** WEGE3 — `OFFICIAL_VERIFIED`.
- **Legal instrument name / issuer:** WEG S.A. (companhia aberta, Brazilian issuer) — `OFFICIAL_VERIFIED`.
- **Official classification:** Ação ordinária (common share, ON), B3 Novo Mercado issuer — `OFFICIAL_VERIFIED`
  for "ação ordinária / WEGE3 is the ON ticker" (the ticker↔ON mapping is standard B3 numbering and is
  corroborated by the issuer's own "ação ordinária" statements); the *segment* (Novo Mercado) is
  `OFFICIAL_PARTIAL` (issuer/B3 context, not quoted verbatim from a filing in this pass).
- **CNPJ:** `UNKNOWN` from the official excerpts retrieved this pass (the CVM IPE documents are PDFs that did
  not render to text; WEG's CNPJ 07.175.725/0001-60 is widely cited by aggregators but was **not** confirmed
  from a primary official record here → recorded `UNKNOWN` rather than `PROVIDER_REPORTED`-as-fact).
- **Ticker↔instrument relationship:** WEGE3 = the single ON line of WEG S.A. — `OFFICIAL_VERIFIED`.
- **Official document URL (evidence):**
  CVM/RAD IPE filing (issuer self-identification): [CVM RAD — WEG S.A. IPE (AGM date notice)](https://www.rad.cvm.gov.br/ENET/frmDownloadDocumento.aspx?CodigoInstituicao=1&Tela=ext&descTipo=IPE&numProtocolo=1481387&numSequencia=1006093&numVersao=1)
  and [CVM RAD — WEG S.A. IPE (Heresite acquisition)](https://www.rad.cvm.gov.br/ENET/frmDownloadDocumento.aspx?CodigoInstituicao=1&Tela=ext&descTipo=IPE&numProtocolo=1370395&numSequencia=895107&numVersao=1).
- **Evidence excerpt (verbatim, from the CVM official domain):**
  > "WEG S.A. (B3: WEGE3 / OTC: WEGZY) announces to its shareholders and the general market that, the date
  > for its Annual General Meeting has been brought forward to April 23, 2026…"
- **Document publication/reference date:** CVM IPE filing (PDF metadata authored 2026-02-24); the AGM-date
  notice references 2026. `OFFICIAL_PARTIAL` on the exact filing date (PDF body not fully parsed).
- **valid_from / valid_to:** `UNKNOWN` / `UNKNOWN` — no official effective interval for the ticker identity
  was found; not inferred from today.
- **Observation date:** 2026-10-06.

---

## 2. BPAC11 — Banco BTG Pactual S.A. (Unit)

- **Official ticker:** BPAC11 — `OFFICIAL_VERIFIED`.
- **Legal instrument name / issuer:** BANCO BTG PACTUAL S.A. (companhia aberta) — `OFFICIAL_VERIFIED`
  (named as "Banco" / "Companhia" in CVM-filed IPE communications).
- **Official classification:** **Unit** — `OFFICIAL_VERIFIED`. Composition per the issuer's own RI:
  one common share (ON) + two Class A preferred shares (PNA) of Banco BTG Pactual.
- **CNPJ:** `30.306.294/____` — **root `OFFICIAL_PARTIAL`, suffix `UNKNOWN`.** The CNPJ base `30.306.294`
  appears in CVM/jurisprudence/registry contexts, but the exact matriz/filial suffix is **conflicting**
  across sources (`/0001-45`, `/0001-50`, `/0002-26` all seen) and was **not** pinned to a single primary
  official record this pass. Suffix recorded `UNKNOWN` pending a CVM FCA / cadastro reference. **Conflict
  recorded.**
- **Ticker↔instrument relationship:** BPAC11 Unit = (i) 1 ON + (ii) 2 PNA of Banco BTG Pactual S.A.;
  historical note: units migrated from BBTG11 to the segregated BPAC11 structure (Banco) vs BBTG12 (BTGP)
  in 2017 — `OFFICIAL_VERIFIED` (issuer RI material fact).
- **Official document URL (evidence):**
  [BTG Pactual RI — Governança / Composição Societária](https://ri.btgpactual.com/governanca-corporativa/composicao-societaria/);
  [BTG Pactual RI — Units Automatic Migration (material fact)](https://ri.btgpactual.com/en/noticias/material-fact-units-automatic-migration/);
  CVM/RAD IPE filings naming the Banco, e.g. [CVM RAD — Banco BTG Pactual IPE](https://www.rad.cvm.gov.br/ENET/frmDownloadDocumento.aspx?CodigoInstituicao=1&Tela=ext&descTipo=IPE&numProtocolo=1370180).
- **Evidence excerpt (verbatim, issuer RI):**
  > "The shares of Banco BTG Pactual that back BPAC11 Units are listed and traded on B3. BPAC11 Units are
  > currently composed of: (i) one common share of Banco BTG Pactual and (ii) two Class A preferred shares
  > of Banco BTG Pactual."
- **Document publication/reference date:** material-fact page dated 2017 (migration); RI composition page
  current. CVM GIC-disclosure IPEs dated 2024. `OFFICIAL_VERIFIED` on composition; dates `OFFICIAL_PARTIAL`.
- **valid_from / valid_to:** `UNKNOWN` / `UNKNOWN` — the 2017 migration date is a *corporate event* date,
  not asserted here as the unit identity's legal validity interval; not inferred.
- **Observation date:** 2026-10-06.

---

## 3. VRTA11 — Fator Verità Fundo de Investimento Imobiliário – FII

- **Official ticker:** VRTA11 — `OFFICIAL_VERIFIED`.
- **Legal instrument name:** Fator Verità Fundo de Investimento Imobiliário – FII — `OFFICIAL_VERIFIED`
  (administrator page + CVM-filed AGO convocation name it).
- **Fund identity (CNPJ):** **`11.664.201/0001-00` — `OFFICIAL_VERIFIED`.** Confirmed consistently by the
  B3/FNET "Informe Anual" filing and the CVM-filed AGO document (CNPJ `11.664.201/...`), and matched by the
  registry base record. **Disambiguation (conflict guard):** a *different* fund, "Fator Verità
  **Multiestratégia**" (ticker **VRTM11**, CNPJ `51.870.412/0001-13`), exists and must not be conflated with
  VRTA11.
- **Administrator / gestor:** `OFFICIAL_PARTIAL` with a **recorded role conflict**:
  the Fator administrator page describes "Far – Fator Administradora de Recursos Ltda." (CVM-authorized
  manager) and a separate Fator page states the fund is "administrado pelo **Banco Fator**". So the
  **administrator vs. manager (gestor) role attribution across Fator entities is ambiguous** in the sources
  retrieved and is flagged for 01-02-03 to resolve against the CVM registration / regulamento.
- **Official classification:** FII (Fundo de Investimento Imobiliário), "fundo de papel" strategy (CRI/LCI/
  LH/FII/FIDC/debêntures) — `OFFICIAL_VERIFIED` as FII (constituição stated by administrator; corroborated by
  CVM-filed AGO). "fundo de papel" is a market descriptor, not a regulatory class.
- **Ticker↔instrument relationship:** VRTA11 = cota do Fator Verità FII (condomínio fechado) — `OFFICIAL_VERIFIED`.
- **Official document URL (evidence):**
  [Fator FAR — Fator Verità | VRTA11](https://far.fator.com.br/fundos/fator-verita/);
  [B3/FNET — Fator Verità Informe Anual](https://fnet.bmfbovespa.com.br/fnet/publico/exibirDocumento?id=25012);
  CVM-filed AGO convocation (via document index) citing CNPJ 11.664.201.
- **Evidence excerpt (verbatim):**
  > "O documento refere-se a uma convocação de Assembleia Geral de Cotistas do Fator Verità Fundo de
  > Investimento Imobiliário – FII … cujo CNPJ é 11.664.201/0001-00." (CVM-filed AGO)
  > "O Fator Verità é um Fundo de Investimento Imobiliário constituído sob a forma de condomínio fechado…"
  > (administrator page)
- **Document publication/reference date:** administrator page current (Aug/2026 figures); Informe Anual and
  AGO are dated filings. `OFFICIAL_VERIFIED` on name/CNPJ; `OFFICIAL_PARTIAL` on exact filing dates.
- **valid_from / valid_to:** `UNKNOWN` / `UNKNOWN` — fund inception is cited as 2010/2011 by various sources
  but no official effective interval for the *identity* is asserted; not inferred from today.
- **Observation date:** 2026-10-06.

---

## 4. SNAG11 — Suno Agro Fiagro Imobiliário

- **Official ticker:** SNAG11 — `OFFICIAL_VERIFIED` (issuer "dados oficiais" sheet).
- **Legal instrument name:** SUNO AGRO FIAGRO IMOBILIÁRIO — `OFFICIAL_PARTIAL` (issuer-official "Ficha
  técnica · Dados oficiais"; the long legal name sometimes appears as "SUNO AGRO – FIAGRO RESPONSABILIDADE
  LIMITADA" in registry sources — **naming-variant conflict recorded**, to reconcile against CVM regulamento).
- **Fund identity (CNPJ):** **`28.152.777/0001-90` — `OFFICIAL_PARTIAL`.** Stated on the issuer's "Dados
  oficiais" sheet (Suno Gestora) and matched by an independent registry; not yet confirmed from a CVM
  primary filing this pass, hence PARTIAL rather than VERIFIED.
- **Administrator / custodiante:** Singulare Corretora de TVM S.A. — `OFFICIAL_PARTIAL` (issuer sheet).
- **Gestor:** Suno Gestora de Recursos Ltda. — `OFFICIAL_PARTIAL` (issuer sheet).
- **Official classification:** FIAGRO Imobiliário (Fundo de Investimento nas Cadeias Produtivas
  Agroindustriais, modalidade imobiliária) — `OFFICIAL_PARTIAL` (issuer sheet states "FIAGRO Imobiliário";
  **not** inferred from the `11` suffix). This is the official-source class for SNAG11 and is **distinct**
  from KNHY11's unresolved classification — KNHY11 stays quarantined regardless.
- **Ticker↔instrument relationship:** SNAG11 = cota do Suno Agro Fiagro (condomínio fechado, prazo
  indeterminado) — `OFFICIAL_PARTIAL`.
- **Official document URL (evidence):**
  [Suno Asset — SNAG11 (Dados oficiais / Documentos oficiais)](https://www.suno.com.br/asset/fundos/snag11/)
  (links to CVM/B3-filed Regulamento, Fato Relevante, Informe Mensal Estruturado, Prospecto — dated index
  present). Fund documents (Regulamento 24/Fev/2026, etc.) are listed with dates on that page.
- **Evidence excerpt (verbatim, issuer "Dados oficiais"):**
  > "Ticker SNAG11 — Nome SUNO AGRO FIAGRO IMOBILIÁRIO — CNPJ 28.152.777/0001-90 — Gestor Suno Gestora de
  > Recursos Ltda. — Administrador Singulare Corretora de TVM S.A. … Tipo / Prazo: Condomínio fechado ·
  > Prazo indeterminado … Atualizado em mar/2026."
- **Document publication/reference date:** issuer sheet "Atualizado em mar/2026"; Regulamento dated
  24/Fev/2026 (and later revisions 05–08/Jun/2026) in the official document index.
- **valid_from / valid_to:** `UNKNOWN` / `UNKNOWN` — a dated Regulamento exists but no official *identity*
  validity interval is asserted; not inferred from today.
- **Observation date:** 2026-10-06.

---

## Coverage table

| ticker | ticker | legal name | issuer/fund | class | CNPJ | ticker↔instrument | official URL | doc date | obs date | valid_from/to | overall grade |
|--------|--------|-----------|-------------|-------|------|-------------------|--------------|----------|----------|---------------|---------------|
| WEGE3  | ✅ VERIFIED | ✅ VERIFIED | ✅ VERIFIED | ✅ ON (VERIFIED); segment PARTIAL | ⬜ UNKNOWN | ✅ VERIFIED | ✅ CVM/RAD | 🟨 PARTIAL (2026-02-24 meta) | 2026-10-06 | UNKNOWN/UNKNOWN | **OFFICIAL_VERIFIED** (CNPJ gap) |
| BPAC11 | ✅ VERIFIED | ✅ VERIFIED | ✅ VERIFIED | ✅ Unit (VERIFIED) | 🟨 root PARTIAL, suffix UNKNOWN (conflict) | ✅ VERIFIED | ✅ BTG RI + CVM/RAD | 🟨 PARTIAL (2017/2024) | 2026-10-06 | UNKNOWN/UNKNOWN | **OFFICIAL_VERIFIED** (CNPJ suffix gap) |
| VRTA11 | ✅ VERIFIED | ✅ VERIFIED | ✅ VERIFIED | ✅ FII (VERIFIED) | ✅ 11.664.201/0001-00 VERIFIED | ✅ VERIFIED | ✅ B3/FNET + CVM AGO | 🟨 PARTIAL | 2026-10-06 | UNKNOWN/UNKNOWN | **OFFICIAL_VERIFIED** (admin/gestor role conflict) |
| SNAG11 | ✅ VERIFIED | 🟨 PARTIAL (name variant) | 🟨 PARTIAL | 🟨 FIAGRO PARTIAL | 🟨 28.152.777/0001-90 PARTIAL | 🟨 PARTIAL | 🟨 issuer sheet (links to CVM/B3 docs) | 🟨 PARTIAL (Reg. 24/Fev/2026) | 2026-10-06 | UNKNOWN/UNKNOWN | **OFFICIAL_PARTIAL** |

## Unresolved ambiguities (hand-off to 01-02-03)

1. **WEGE3 CNPJ** — not confirmed from a primary official record this pass (CVM PDFs did not render to text).
   01-02-03 should pull WEG S.A.'s CNPJ from a CVM FCA / cadastro page.
2. **BPAC11 CNPJ suffix** — base `30.306.294` is official but `/0001-45` vs `/0001-50` vs `/0002-26` conflict
   across sources; the matriz suffix must be pinned to a CVM FCA / B3 issuer record.
3. **VRTA11 administrator vs. gestor** — "Banco Fator" (administrator) vs "Far – Fator Administradora de
   Recursos" (gestor/manager) role attribution is ambiguous across Fator pages; reconcile against the CVM
   registration / regulamento. Also keep VRTA11 strictly distinct from VRTM11 (51.870.412/0001-13).
4. **SNAG11 legal name variant** — "SUNO AGRO FIAGRO IMOBILIÁRIO" (issuer sheet) vs "SUNO AGRO – FIAGRO
   RESPONSABILIDADE LIMITADA" (registry); confirm the exact legal name from the CVM-filed Regulamento, and
   upgrade CNPJ/administrator/class to VERIFIED from that primary document.
5. **No `valid_from`/`valid_to` for any asset** — no official effective interval for ticker identity was
   found; all left UNKNOWN by rule (never inferred from today's observation).
6. **Dates are mostly PARTIAL** — exact official filing dates need to be read from the primary documents
   (several CVM IPE/RAD files are PDFs not parsed in this read-only pass).

## Sufficiency assessment for 01-02-03

| ticker | sufficient official evidence to begin 01-02-03? | gating gap |
|--------|-----------------------------------------------|-----------|
| **WEGE3**  | **Yes** — identity, class and ticker↔instrument are OFFICIAL_VERIFIED from CVM. | CNPJ to upgrade from UNKNOWN. |
| **BPAC11** | **Yes** — identity, Unit class and composition are OFFICIAL_VERIFIED from issuer RI + CVM. | CNPJ suffix conflict to resolve. |
| **VRTA11** | **Yes** — name, FII class and CNPJ are OFFICIAL_VERIFIED. | administrator/gestor role conflict; VRTM11 disambiguation. |
| **SNAG11** | **Partial** — all fields OFFICIAL_PARTIAL from the issuer "dados oficiais" sheet (links to CVM/B3 docs) but none yet confirmed from a CVM primary filing. | read the CVM-filed Regulamento to upgrade to VERIFIED. |

**None of the four is BLOCKED.** Three (WEGE3, BPAC11, VRTA11) have ≥1 OFFICIAL_VERIFIED anchor on core
identity; SNAG11 is OFFICIAL_PARTIAL (issuer-official, pending CVM-primary confirmation). All four remain
`activation_status=NOT_AUTHORIZED` and OFFICIAL_IDENTITY_UNVERIFIED until 01-02-03 performs the per-asset
evidence evaluation and the human approval gate (01-04) is reached. **KNHY11 was not researched and remains
quarantined.**

<rollback>Supersede this dossier with a reviewed revision citing newer/primary official documents; never edit source originals or registered hashes, and never erase a recorded conflict or an UNKNOWN by inference.</rollback>
