package dev.b3monitor.admin;

import dev.b3monitor.domain.rule.Comparator;
import dev.b3monitor.domain.rule.RuleMode;
import dev.b3monitor.persistence.RuleAdminService;
import dev.b3monitor.persistence.RuleStateRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.*;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * SEC-01 security tests for the ENABLED admin surface (cycle-10 item E). The admin feature is turned on
 * with synthetic env values (a BCrypt hash of "admin-pass"); CSRF is enforced; reads require
 * authentication; mutations require ADMIN. No credential value is asserted into a response/log.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "b3monitor.admin.enabled=true",
        "b3monitor.admin.username=admin"
})
class AdminSecurityEnabledTest {

    static final String ADMIN_PASS = "s3cr3t-admin-pass";   // synthetic, test-only

    @org.springframework.test.context.DynamicPropertySource
    static void adminHash(org.springframework.test.context.DynamicPropertyRegistry r) {
        // Inject a REAL BCrypt hash so form-login/session tests exercise authentication; the plaintext
        // password lives only in this test, never in committed config.
        String hash = new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder().encode(ADMIN_PASS);
        r.add("b3monitor.admin.password-hash", () -> hash);
    }

    @Autowired MockMvc mvc;
    @Autowired RuleAdminService admin;
    @Autowired RuleStateRepository ruleStates;
    @Autowired AdminAuditRepository auditRepo;
    private final JsonMapper json = JsonMapper.builder().build();

    @BeforeEach
    void seed() {
        if (admin.find("sec-r").isEmpty()) {
            admin.create("sec-r", "WEGE3", Comparator.ABOVE, new BigDecimal("50.00"), 2, new BigDecimal("0.10"));
        }
    }

    // 7 — login/session works with the synthetic identity (HTTP Basic authenticates the real hash)
    @Test
    void basicAuthWithSyntheticAdminSucceeds() throws Exception {
        mvc.perform(get("/api/admin/status").with(httpBasic("admin", ADMIN_PASS)))
                .andExpect(status().isOk());
    }

    // 8 — a bad password fails (authentication really runs against the hash)
    @Test
    void basicAuthWithWrongPasswordFails() throws Exception {
        mvc.perform(get("/api/admin/status").with(httpBasic("admin", "wrong")))
                .andExpect(status().isUnauthorized());
    }

    // 10 — no credential value leaks into a response body
    @Test
    void responseNeverEchoesCredential() throws Exception {
        String bodyText = mvc.perform(get("/api/admin/status").with(httpBasic("admin", ADMIN_PASS)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        org.junit.jupiter.api.Assertions.assertFalse(bodyText.contains(ADMIN_PASS), "password must not appear in a response");
        org.junit.jupiter.api.Assertions.assertFalse(bodyText.toLowerCase().contains("password"), "no password field leaked");
    }

    // 1 — anonymous read fails
    @Test @WithAnonymousUser
    void anonymousReadIsUnauthorized() throws Exception {
        mvc.perform(get("/api/admin/status")).andExpect(status().isUnauthorized());
    }

    // 2 — anonymous mutation fails
    @Test @WithAnonymousUser
    void anonymousMutationIsUnauthorized() throws Exception {
        mvc.perform(post("/api/admin/rules/sec-r/pause").with(csrf()))
                .andExpect(status().isUnauthorized());
    }

    // 3/4 — authenticated non-admin: read allowed, mutation denied
    @Test
    void nonAdminCanReadButNotMutate() throws Exception {
        mvc.perform(get("/api/admin/rules").with(user("viewer").roles("VIEWER"))).andExpect(status().isOk());
        mvc.perform(post("/api/admin/rules/sec-r/pause").with(user("viewer").roles("VIEWER")).with(csrf()))
                .andExpect(status().isForbidden());
    }

    // 5 — ADMIN mutation WITHOUT CSRF denied
    @Test
    void adminMutationWithoutCsrfIsForbidden() throws Exception {
        mvc.perform(post("/api/admin/rules/sec-r/pause").with(user("admin").roles("ADMIN")))
                .andExpect(status().isForbidden());
    }

    // 6 — ADMIN mutation WITH CSRF succeeds
    @Test
    void adminMutationWithCsrfSucceeds() throws Exception {
        mvc.perform(post("/api/admin/rules/sec-r/pause").with(user("admin").roles("ADMIN")).with(csrf()))
                .andExpect(status().isOk());
    }

    // 11 — create returns UNSELECTED
    @Test
    void createReturnsUnselectedMode() throws Exception {
        var body = new AdminDtos.CreateRuleRequest("sec-new", "PETR4", Comparator.ABOVE,
                new BigDecimal("30.00"), 2, new BigDecimal("0.05"));
        mvc.perform(post("/api/admin/rules").with(user("admin").roles("ADMIN")).with(csrf())
                        .contentType("application/json").content(json.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.mode").value("UNSELECTED"));
    }

    // 12 — selecting LEVEL rejected
    @Test
    void selectLevelModeIsRejected() throws Exception {
        var body = new AdminDtos.SelectModeRequest(RuleMode.LEVEL);
        mvc.perform(post("/api/admin/rules/sec-r/mode").with(user("admin").roles("ADMIN")).with(csrf())
                        .contentType("application/json").content(json.writeValueAsString(body)))
                .andExpect(status().isBadRequest());
    }

    // 13 — stale expectedRevision edit returns conflict, no mutation
    @Test
    void staleExpectedRevisionEditIsConflict() throws Exception {
        long current = admin.find("sec-r").orElseThrow().getRevision();
        var body = new AdminDtos.EditRuleRequest(current + 99, Comparator.ABOVE,
                new BigDecimal("60.00"), 2, new BigDecimal("0.10"));
        mvc.perform(put("/api/admin/rules/sec-r").with(user("admin").roles("ADMIN")).with(csrf())
                        .contentType("application/json").content(json.writeValueAsString(body)))
                .andExpect(status().isConflict());
        // no mutation: revision unchanged
        org.junit.jupiter.api.Assertions.assertEquals(current, admin.find("sec-r").orElseThrow().getRevision());
    }

    // 14 — duplicate resume is idempotent and does not re-mark rebaseline
    @Test
    void duplicateResumeDoesNotReMarkRebaseline() throws Exception {
        admin.create("sec-resume", "VALE3", Comparator.ABOVE, new BigDecimal("70.00"), 2, BigDecimal.ZERO);
        admin.selectMode("sec-resume", RuleMode.CROSSING);
        // establish a rule_state and pause→resume once (sets the marker)
        ruleStates.save(new dev.b3monitor.persistence.RuleStateEntity("sec-resume"));
        admin.pause("sec-resume");
        admin.resume("sec-resume");
        boolean markerAfterFirst = ruleStates.findByRuleId("sec-resume").orElseThrow().isRebaselineRequired();
        // consume the marker, then a duplicate/retried resume on an ALREADY-ACTIVE rule must be a no-op
        var rs = ruleStates.findByRuleId("sec-resume").orElseThrow();
        rs.clearRebaselineRequired(); ruleStates.save(rs);
        mvc.perform(post("/api/admin/rules/sec-resume/resume").with(user("admin").roles("ADMIN")).with(csrf()))
                .andExpect(status().isOk());
        org.junit.jupiter.api.Assertions.assertTrue(markerAfterFirst, "first resume marked rebaseline");
        org.junit.jupiter.api.Assertions.assertFalse(
                ruleStates.findByRuleId("sec-resume").orElseThrow().isRebaselineRequired(),
                "duplicate resume on an active rule must NOT re-mark rebaseline");
    }

    // 15 — duplicate CROSSING desired-state request does not create an extra revision
    @Test
    void duplicateModeSelectionDoesNotMintExtraRevision() throws Exception {
        admin.create("sec-mode", "ITUB4", Comparator.ABOVE, new BigDecimal("25.00"), 2, BigDecimal.ZERO);
        long rev = admin.selectMode("sec-mode", RuleMode.CROSSING);     // first selection: real bump
        var body = new AdminDtos.SelectModeRequest(RuleMode.CROSSING);
        mvc.perform(post("/api/admin/rules/sec-mode/mode").with(user("admin").roles("ADMIN")).with(csrf())
                        .contentType("application/json").content(json.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.revision").value((int) rev));    // SAME revision, no extra bump
    }

    // D — real browser session: form login, session reuse without Basic, logout invalidates
    @Test
    void browserFormLoginThenSessionReuseThenLogout() throws Exception {
        // 1) form login with the synthetic credential inside the active chain → authenticated + session
        var loginResult = mvc.perform(formLogin("/admin/login").user("admin").password(ADMIN_PASS))
                .andExpect(authenticated().withUsername("admin"))
                .andReturn();
        var session = (org.springframework.mock.web.MockHttpSession) loginResult.getRequest().getSession(false);
        org.junit.jupiter.api.Assertions.assertNotNull(session, "a session was established by form login");

        // 2) a later GET reuses the session WITHOUT HTTP Basic
        mvc.perform(get("/api/admin/status").session(session)).andExpect(status().isOk());

        // 3) logout invalidates the session; the invalidated session no longer authenticates the API
        mvc.perform(post("/admin/logout").with(csrf()).session(session));
        mvc.perform(get("/api/admin/status").session(session)).andExpect(status().isUnauthorized());
    }

    // D — bad form credentials never authenticate
    @Test
    void browserFormLoginWithWrongPasswordFails() throws Exception {
        mvc.perform(formLogin("/admin/login").user("admin").password("nope"))
                .andExpect(unauthenticated());
    }

    // G — a successful mutation creates exactly one audit event; no credential leaks into it
    @Test
    void successfulMutationCreatesOneAuditEvent() throws Exception {
        admin.create("sec-audit", "BBAS3", Comparator.ABOVE, new BigDecimal("40.00"), 2, BigDecimal.ZERO);
        admin.selectMode("sec-audit", RuleMode.CROSSING);
        long before = auditRepo.countByRuleId("sec-audit");
        mvc.perform(post("/api/admin/rules/sec-audit/pause").with(user("admin").roles("ADMIN")).with(csrf()))
                .andExpect(status().isOk());
        long after = auditRepo.countByRuleId("sec-audit");
        org.junit.jupiter.api.Assertions.assertEquals(before + 1, after, "pause writes one audit event");
        var latest = auditRepo.findByOrderByOccurredAtDescIdDesc(
                org.springframework.data.domain.PageRequest.of(0, 5));
        org.junit.jupiter.api.Assertions.assertTrue(
                latest.stream().noneMatch(e -> e.getDetail() != null && e.getDetail().contains(ADMIN_PASS)),
                "no credential appears in audit content");
    }

    // G — the audit read endpoint is ADMIN-only (VIEWER forbidden) and bounded
    @Test
    void auditEndpointIsAdminOnly() throws Exception {
        mvc.perform(get("/api/admin/audit").with(user("viewer").roles("VIEWER")))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/audit?size=10000").with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk());   // service hard-caps the page size regardless of the request
    }

    // H — the private UI: login page is reachable unauthenticated; an authenticated session renders a page
    @Test
    void uiLoginPageIsReachableAndRulesPageRequiresAuth() throws Exception {
        mvc.perform(get("/admin/login")).andExpect(status().isOk());
        // unauthenticated UI page is DENIED (never public): a browser Accept gets a login redirect,
        // a non-browser client gets 401 — both prove it is not reachable without a session.
        int unauth = mvc.perform(get("/admin/rules").accept(org.springframework.http.MediaType.TEXT_HTML))
                .andReturn().getResponse().getStatus();
        org.junit.jupiter.api.Assertions.assertTrue(unauth == 302 || unauth == 401,
                "unauthenticated /admin/rules must be denied (redirect or 401), got " + unauth);
        // authenticated → the rules page renders
        mvc.perform(get("/admin/rules").with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/html"));
    }

    // ---- cycle-12 B: UI RBAC — VIEWER may read ordinary pages but NEVER mutate or see audit ----

    @Test
    void viewerCanReadOrdinaryUiPages() throws Exception {
        mvc.perform(get("/admin/rules").with(user("viewer").roles("VIEWER"))).andExpect(status().isOk());
        mvc.perform(get("/admin/assets").with(user("viewer").roles("VIEWER"))).andExpect(status().isOk());
        mvc.perform(get("/admin/alerts").with(user("viewer").roles("VIEWER"))).andExpect(status().isOk());
        mvc.perform(get("/admin/readiness").with(user("viewer").roles("VIEWER"))).andExpect(status().isOk());
    }

    @Test
    void readinessIsReadOnlyAndViewerReadable() throws Exception {
        // UI-02 readiness is a read-only JSON snapshot: viewer-readable, reports catalog + NOT_INTEGRATED
        // components, and introduces no mutation/activation endpoint.
        mvc.perform(get("/api/admin/readiness").with(user("viewer").roles("VIEWER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.catalogAssets").value(23))
                .andExpect(jsonPath("$.workersEnabled").value(false))
                .andExpect(jsonPath("$.components[?(@.component=='python_daily_indicators')].state")
                        .value(org.hamcrest.Matchers.hasItem("NOT_INTEGRATED")))
                .andExpect(jsonPath("$.components[?(@.component=='catalog_import')].state")
                        .value(org.hamcrest.Matchers.hasItem("NOT_INTEGRATED")));
        // no POST activation/import endpoint exists under readiness
        mvc.perform(post("/api/admin/readiness").with(user("admin").roles("ADMIN")).with(csrf()))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void viewerUiAuditIsForbidden() throws Exception {
        mvc.perform(get("/admin/audit").with(user("viewer").roles("VIEWER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void viewerUiMutationsAreForbiddenAndStateUnchanged() throws Exception {
        admin.create("ui-rbac", "WEGE3", Comparator.ABOVE, new BigDecimal("50.00"), 2, BigDecimal.ZERO);
        long rev = admin.find("ui-rbac").orElseThrow().getRevision();
        // every mutating POST under /admin/** with a VIEWER + valid CSRF → 403
        mvc.perform(post("/admin/rules/ui-rbac/pause").with(user("viewer").roles("VIEWER")).with(csrf()))
                .andExpect(status().isForbidden());
        mvc.perform(post("/admin/rules/ui-rbac/resume").with(user("viewer").roles("VIEWER")).with(csrf()))
                .andExpect(status().isForbidden());
        mvc.perform(post("/admin/rules/ui-rbac/disable").with(user("viewer").roles("VIEWER")).with(csrf()))
                .andExpect(status().isForbidden());
        mvc.perform(post("/admin/rules/ui-rbac/mode").with(user("viewer").roles("VIEWER")).with(csrf())
                        .param("mode", "CROSSING")).andExpect(status().isForbidden());
        mvc.perform(post("/admin/rules").with(user("viewer").roles("VIEWER")).with(csrf())
                        .param("ruleId", "ui-rbac2").param("ticker", "PETR4")
                        .param("comparator", "ABOVE").param("threshold", "10"))
                .andExpect(status().isForbidden());
        // state unchanged by the denied VIEWER mutations
        org.junit.jupiter.api.Assertions.assertEquals(rev, admin.find("ui-rbac").orElseThrow().getRevision());
        org.junit.jupiter.api.Assertions.assertFalse(admin.find("ui-rbac").orElseThrow().isPaused());
        org.junit.jupiter.api.Assertions.assertTrue(admin.find("ui-rbac2").isEmpty(), "VIEWER create was denied");
    }

    @Test
    void adminUiMutationSucceedsWithCsrf() throws Exception {
        admin.create("ui-admin", "WEGE3", Comparator.ABOVE, new BigDecimal("50.00"), 2, BigDecimal.ZERO);
        mvc.perform(post("/admin/rules/ui-admin/pause").with(user("admin").roles("ADMIN")).with(csrf()))
                .andExpect(status().is3xxRedirection());   // PRG redirect to /admin/rules
        org.junit.jupiter.api.Assertions.assertTrue(admin.find("ui-admin").orElseThrow().isPaused());
    }

    @Test
    void adminUiMutationWithoutCsrfIsForbidden() throws Exception {
        admin.create("ui-nocsrf", "WEGE3", Comparator.ABOVE, new BigDecimal("50.00"), 2, BigDecimal.ZERO);
        mvc.perform(post("/admin/rules/ui-nocsrf/pause").with(user("admin").roles("ADMIN")))
                .andExpect(status().isForbidden());
        org.junit.jupiter.api.Assertions.assertFalse(admin.find("ui-nocsrf").orElseThrow().isPaused());
    }

    @Test
    void anonymousUiMutationIsDenied() throws Exception {
        int st = mvc.perform(post("/admin/rules/x/pause").with(csrf()))
                .andReturn().getResponse().getStatus();
        org.junit.jupiter.api.Assertions.assertTrue(st == 401 || st == 403, "anonymous UI mutation denied, got " + st);
    }

    // UI-01 / UI-03 JSON surfaces render for an authenticated reader
    @Test
    void assetsAndAlertsJsonRenderForAuthenticated() throws Exception {
        mvc.perform(get("/api/admin/assets").with(user("viewer").roles("VIEWER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assets").isArray());
        mvc.perform(get("/api/admin/alerts?size=5000").with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.alerts").isArray());
    }
}
