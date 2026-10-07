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
