# Spring Boot support & migration study (cycle 5)

**Context.** The cycle-4 review correctly flagged the Spring Boot support window. Verified against
current sources (2026-10): **the entire Spring Boot 3.x line is now OSS end-of-life** — 3.5 was the
last 3.x branch and reached OSS EOL on **2026-06-30**
([danvega.dev](https://www.danvega.dev/blog/spring-boot-end-of-life),
[endoflife.date](https://endoflife.date/spring-boot)). So **3.3.13 is not merely "last of 3.3" — it is
on a fully-unsupported major line.** This document is the objective compatibility study the review
asked for. **No stack bump was performed this cycle** — the P0 quota correctness takes precedence, and
an indiscriminate upgrade during a correctness fix is explicitly out of scope.

> ⚠️ Correction vs the cycle-4 review note: the review cited 3.3 EOL as June 2025 and 4.0/4.1 as the
> maintained lines. The current reality (2026-10) is sharper: ALL 3.x is EOL, **4.0 itself loses OSS
> support in December 2026** (≈2 months out), and **4.1** (released 2026-06-10, built on Spring
> Framework 7.0) is the branch with runway. Starting at 4.0 means upgrading twice. The 3→4 move also
> carries a **Jackson 2 → 3** change.

## Current pins (verified)

| Component        | Current            | Notes |
|------------------|--------------------|-------|
| Java             | 21 (LTS)           | Compatible with Spring Boot 3.3, 3.4, 3.5 and 4.0 (4.0 **requires** Java 17+; 21 is fine). |
| Spring Boot      | 3.3.13 (OSS EOL)   | Managed via parent. |
| Spring Framework | 6.1.x (via Boot)   | Boot 3.3 → Framework 6.1. |
| Hibernate ORM    | 6.5.x (via Boot)   | |
| Flyway           | (Boot-managed)     | V1–V6 are plain ALTER/CREATE, portable across Flyway majors. |
| TA4J             | 0.15               | Independent of Spring; no Spring coupling. **Not yet used** by the MVP vertical. |
| Testcontainers   | 1.20.1             | Independent of Spring; current line supports PostgreSQL 16. |

## Target options (verified 2026-10)

| Target             | OSS status (2026-10) | Framework | Risk | Verdict |
|--------------------|----------------------|-----------|------|---------|
| **3.3.13 (now)**   | EOL (whole 3.x line) | 6.1       | unsupported | Not a destination. |
| **3.5.x**          | EOL 2026-06-30 (was last 3.x) | 6.2 | unsupported | Not a destination. |
| **4.0.x**          | OSS support ends **Dec 2026** | 7.0 | moderate | Stepping stone only — upgrading here means upgrading again almost immediately. |
| **4.1.x**          | Current, released 2026-06-10 | 7.0 | moderate–high (newest) | **Recommended destination** — the branch with runway. |

## Breaking-change surface for a 3.3 → 4.1 move (to validate before executing)

1. **Spring Framework 6.1 → 7.0.** Removal of long-deprecated APIs; this project uses only
   mainstream `@Service`/`@Transactional`/`RestClient`/Spring Data JPA — low exposure, but must be
   compiled against 7.0 and the full suite re-run.
2. **Jackson 2 → 3.** The 3→4 line moves to Jackson 3 (new `tools.jackson` coordinates/packages).
   `RestClientBrapiClient` parses responses via `JsonNode`/`bodyTo(JsonNode.class)` — verify the
   `JsonNode` API and the `RestClient` message converters under Jackson 3. **This is the second
   highest-churn point after the test annotations.**
3. **`RestClient`** (used by `RestClientBrapiClient`) — stable since 6.1; verify the
   `exchange(...)`/`bodyTo(...)` signatures are unchanged in 7.0.
4. **Spring Data JPA** — repository + `@Query` JPQL used here is stable; confirm `JpaRepository`
   method-name derivation and `@Version` optimistic-lock semantics are unchanged.
5. **Testing** — `@DataJpaTest`, `@SpringBootTest`, `@MockBean`/`@SpyBean`. **`@MockBean`/`@SpyBean`
   are deprecated since Boot 3.4 and removed/replaced by `@MockitoBean`/`@MockitoSpyBean`.** This
   project uses `@MockBean` + `@SpyBean` in `SchedulerTransactionTest` and `@MockBean` in
   `OutboxPostgresIT` — the **highest-churn migration point**; convert first.
6. **Flyway/Hibernate majors** bundled by Boot 4.1 — re-validate V1–V6 under the new Hibernate DDL
   validation; the migrations are standard SQL and expected to pass, but `ddl-auto=validate` on real
   Postgres (the gated IT) is the proof.
7. **Jakarta EE baseline** — already on `jakarta.*`; no `javax.*` left, so no namespace migration.
8. **Java** — 4.x requires Java 17+; this project is already on 21 (fine).

## Controlled migration plan (execute only when it will not block the P0)

1. Branch; bump parent to **4.1.x**; let dependency management re-resolve.
2. Convert `@MockBean`/`@SpyBean` → `@MockitoBean`/`@MockitoSpyBean` (item 5 above).
3. Address Jackson 2 → 3 in `RestClientBrapiClient` (item 2): verify `JsonNode` parsing + the
   `RestClient` converters under Jackson 3.
4. `mvn test` (H2 + mocks) must stay green — treat any red as a migration finding, not a flake.
5. `mvn verify -Pdocker-it` on isolated Docker to re-validate V1–V6 + concurrency under real Postgres.
6. If any step regresses and cannot be fixed within the window, **revert the bump** (it is an isolated
   pom change) and stay on 3.3.13 with this study recording the exact blocker.

**Reversibility.** The bump is a single `pom.xml` parent-version change plus the test-annotation
rename and the Jackson parsing check; revert is one commit. No runtime data or migration is affected
by trying it.

## This cycle's decision

Stay on **3.3.13**; record the EOL honestly (the whole 3.x line is unsupported, not just 3.3); do
**not** bump during the quota P0 fix. The migration is scoped and reversible, targets **4.1**, and is
ready to execute in a dedicated follow-up where the test matrix (unit + the Docker IT) can gate it
end-to-end.
