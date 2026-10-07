# Spring Boot 4.1.1 Migration — RESULT (COMPLETED, cycle 9)

Status: **DONE and green.** This supersedes the cycle-7/8 probe notes (which stopped at the Jackson 2→3
blocker and then a guessed Testcontainers 2.0 coordinate). The migration is committed on branch
`checkpoint/cycle7-reviewed` as `e864fad` (`chore: migrate to Spring Boot 4.1.1`), separate from the
cycle-9 functional commit `172e768`.

## Outcome

`mvn -DskipTests compile` ✓ · `mvn test` → **148 passed, 0 failures, 0 errors on Spring Boot 4.1.1**
(previously 134 on 3.3.13; the delta is the cycle-9 regressions, not migration churn).

## Exact coordinates used (the previously-guessed one was wrong)

| Concern | Cycle-8 guess (failed) | Cycle-9 (verified, works) |
|---|---|---|
| Parent | 3.3.13 | `spring-boot-starter-parent:4.1.1` |
| Testcontainers JUnit | `org.testcontainers:junit-jupiter:2.0.0` (absent) | `org.testcontainers:testcontainers-junit-jupiter` (BOM-managed **2.0.5**) |
| Testcontainers Postgres | `org.testcontainers:postgresql` | `org.testcontainers:testcontainers-postgresql` (BOM-managed **2.0.5**) |
| TC version pin | `<testcontainers.version>` override | removed — let the Boot 4.1.1 BOM manage it |
| `@DataJpaTest` | `…autoconfigure.orm.jpa.DataJpaTest` (gone from starter-test) | `org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest` + module `spring-boot-data-jpa-test` |
| Jackson | `com.fasterxml.jackson.databind.JsonNode` (Jackson 2, gone) | `tools.jackson.databind.JsonNode` (Jackson 3) |
| Mock test beans | `@MockBean` / `@SpyBean` | `@MockitoBean` / `@MockitoSpyBean` |

## Changes applied

- **pom.xml**: parent → 4.1.1; removed the `testcontainers.version` property; swapped the two
  Testcontainers artifacts to the `testcontainers-*` IDs with NO explicit `<version>`; added the modern
  `spring-boot-data-jpa-test` test module (NOT the `spring-boot-starter-test-classic` bridge — the modern
  modules were sufficient, so the bridge is not a dependency).
- **Jackson 2 → 3**: `RestClientBrapiClient` import only; the `JsonNode` node API (`path`/`get`/`asText`/
  `asBoolean`) compiled unchanged against Jackson 3's databind.
- **Test annotations**: `@DataJpaTest` import moved across 9 slice tests; `@MockBean`/`@SpyBean` →
  `@MockitoBean`/`@MockitoSpyBean` in `SchedulerTransactionTest`.

## Dependency-tree sanity (no accidental Jackson 2 shadow)

```
tools.jackson.core:jackson-databind:3.1.5          ← active databind (Jackson 3)
com.fasterxml.jackson.core:jackson-annotations:2.21 ← Jackson 3's OWN dependency (annotations package
                                                       retains com.fasterxml by design) — NOT a J2 shadow
org.testcontainers:testcontainers-junit-jupiter:2.0.5
org.testcontainers:testcontainers-postgresql:2.0.5
```

There is no `com.fasterxml.jackson.core:jackson-databind` on the classpath — the only `com.fasterxml`
artifact is the annotations jar, which Jackson 3 pulls in intentionally.

## Rollback plan (not needed — kept for the record)

The migration is a single isolated commit (`e864fad`) on top of the green functional commit (`172e768`).
If a later blocker surfaced, `git revert e864fad` (or `git reset --hard 172e768`) restores the
134-green-on-3.3.13 state with the cycle-9 functional fixes intact.

## Not covered by `mvn test`

The PostgreSQL IT (`-Pdocker-it`) did NOT run (Docker absent), so the Boot 4.1.1 + Testcontainers 2.0.5
PostgreSQL container STARTUP is not yet proven end-to-end on this host. That is the one remaining
migration-adjacent verification; run `mvn verify -Pdocker-it` where Docker is available.
