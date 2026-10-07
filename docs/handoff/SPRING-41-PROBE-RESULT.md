# Spring Boot 4.1 migration probe — result (updated cycle 7)

**What this is.** A throwaway probe of a **sanitized copy** of the cycle-7 source tree (pom.xml + src)
in a scratch directory OUTSIDE the production working tree. The main tree is untouched and still on
Spring Boot 3.3.13. Item H asked to FINISH the probe (not just the first compile stop) and record every
additional blocker **without overclaiming**.

## Step 1 — stepping stone: Spring Boot 3.5.6
(Confirmed in cycle 6.) `mvn test` → BUILD SUCCESS, suite green, with `@MockBean`/`@SpyBean`
deprecation warnings.

## Step 2 — Spring Boot 4.1.0, full migration attempt (cycle 7)
Applied in the probe copy, in order:
1. parent `3.3.13 → 4.1.0`;
2. test annotations `@MockBean`/`@SpyBean` → `@MockitoBean`/`@MockitoSpyBean`
   (`org.springframework.test.context.bean.override.mockito.*`);
3. Jackson 2 → 3 in `RestClientBrapiClient`: import `com.fasterxml.jackson.databind.JsonNode` →
   `tools.jackson.databind.JsonNode`.

**Result — MAIN compiles under 4.1.0.** The cycle-6 first blocker (Jackson 2→3) is cleared by the
import change; `RestClient.bodyTo(JsonNode.class)` resolves against Jackson 3. So the earlier report's
careful wording was correct: Jackson was only *the first observed blocker*, not the whole migration.

**New blocker surfaced at TEST compile — Boot 4 test-slice modularization.** Every `@DataJpaTest` test
fails to compile:
```
package org.springframework.boot.test.autoconfigure.orm.jpa does not exist   (@DataJpaTest)
```
In Spring Boot 4 the test auto-configuration slices were split out of the monolithic
`spring-boot-test-autoconfigure` / `spring-boot-starter-test`. `@DataJpaTest` is no longer on the
classpath from `spring-boot-starter-test` alone — it now lives in a **separate modular test-autoconfigure
artifact** that must be added as a test dependency. A naive import rewrite does NOT fix it (the package
genuinely isn't present until the module is on the classpath); the fix is a POM dependency addition, not
a code edit. This affects all 8 `@DataJpaTest` slices + the `@SpringBootTest` IT uses the same family.

**Honest status:** `3.3 → 4.1` requires, at minimum: (a) Jackson 2→3 (done in probe, works);
(b) `@MockitoBean`/`@MockitoSpyBean` rename (done in probe); (c) **add the modular JPA test-slice
dependency** (and audit the other test slices — `@SpringBootTest`, mockito bean-override) to the test
scope. The full 123-test run under 4.1 was NOT reached because test compile stopped at (c). No further
blockers can be claimed beyond (c) until the dependency is added and the suite recompiles.

## Exact remaining migration checklist (dedicated follow-up, with a Git checkpoint)
1. `3.3.13 → 3.5.6` (green) → **commit** as a recoverable checkpoint.
2. `@MockBean`→`@MockitoBean`, `@SpyBean`→`@MockitoSpyBean`.
3. `3.5.6 → 4.1.0`: Jackson 2→3 in `RestClientBrapiClient`.
4. Add the Boot 4 modular test-slice dependency so `@DataJpaTest` (and the other slices) resolve; find
   the exact artifact from the Boot 4 BOM (do not guess the coordinates — read the Boot 4 migration
   guide / dependency list).
5. `mvn test`; then `mvn verify -Pdocker-it` on a Docker host.
6. Record any further blockers that only appear once the suite recompiles and runs.
7. Merge to main ONLY with a separate Git checkpoint/commit authorization.

## Decision
Confirmed `3→4` is a multi-step migration (Jackson 3 + annotation rename + **test-slice
modularization**), not two tweaks. Main tree stays on 3.3.13 this cycle (no commit authorization; P0/P1
correctness first). The probe is throwaway and left the production tree byte-unchanged.

---

## Cycle 8 — ATTEMPTED on the checkpoint branch, then REVERTED (migration BLOCKED)

With the authorized local checkpoint in place (`checkpoint/cycle7-reviewed`, functional fixes committed
at `0daada3`), the 4.1 migration was attempted ON the branch with the commit as the rollback point:

Applied: parent `3.3.13 → 4.1.0`; `spring-boot-starter-test` → **`spring-boot-starter-test-classic`**
(the Boot-4 backward-compat starter that keeps `@DataJpaTest` and the other old slices on the
classpath — this clears the cycle-7 test-slice blocker); Jackson 2 → 3 in `RestClientBrapiClient`
(`tools.jackson.databind.JsonNode`); `@MockBean`/`@SpyBean` → `@MockitoBean`/`@MockitoSpyBean` across
the tests; Testcontainers renamed `postgresql` → `testcontainers-postgresql` (per the Boot-4 testing
migration note).

**New blocker — Testcontainers 2.0 artifact coordinates.** `mvn test` on 4.1.0 failed at dependency
resolution:
```
Could not find artifact org.testcontainers:junit-jupiter:jar:2.0.0 in central
```
The Boot-4 BOM / Testcontainers 2.0 module coordinates are NOT `org.testcontainers:junit-jupiter:2.0.0`
as guessed — the groupId/artifactId/version for the 2.0 line must be read from the Boot 4.1 dependency
BOM (do not guess). The full 134-test suite under 4.1 was therefore not reached; the remaining
unknowns past this (any runtime/behavioral 4.1 differences) cannot yet be claimed.

**Reverted.** Per the cycle-8 plan's rollback rule, the migration edits (pom + Jackson + annotations)
were reverted with `git checkout -- pom.xml src`, returning to the green `0daada3` functional-fixes
commit on 3.3.13 (134 tests green). The migration is **BLOCKED**, not done; nothing migration-related
was committed.

### Exact remaining migration steps (next dedicated attempt)
1. From `0daada3`, bump to 4.1.0 + `spring-boot-starter-test-classic` + Jackson 3 + `@MockitoBean`/
   `@MockitoSpyBean` (all proven to compile/resolve except Testcontainers).
2. Resolve the correct **Testcontainers 2.0 coordinates** from the Spring Boot 4.1 dependency BOM
   (the BOM-managed groupId/artifactId; likely a renamed module and a 2.x version that actually exists
   in Central) — do NOT hardcode a guessed version.
3. `mvn test` → then `mvn verify -Pdocker-it` on a Docker host.
4. Commit as a SEPARATE local commit only if green; otherwise revert the migration commit and keep the
   functional branch.
