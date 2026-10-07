package dev.b3monitor.admin;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * SEC-01 fail-closed test (cycle-10 item E #9): with {@code b3monitor.admin.enabled=false} (the default),
 * the admin controller bean is NOT registered and the deny-all chain refuses every {@code /api/admin/**}
 * request — no anonymous fallback, no reachable surface.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminSurfaceDisabledTest {

    @Autowired MockMvc mvc;
    @Autowired ApplicationContext ctx;

    @Test
    void adminControllerBeanIsAbsentWhenDisabled() {
        assertEquals(0, ctx.getBeanNamesForType(AdminController.class).length,
                "AdminController must not be registered while the admin surface is disabled");
        assertEquals(0, ctx.getBeanNamesForType(AdminUiController.class).length,
                "AdminUiController must not be registered while the admin surface is disabled");
    }

    @Test
    void adminReadIsForbiddenWhenDisabled() throws Exception {
        mvc.perform(get("/api/admin/status")).andExpect(status().isForbidden());
    }

    @Test
    void adminMutationIsForbiddenWhenDisabled() throws Exception {
        mvc.perform(post("/api/admin/rules/anything/pause").with(csrf()))
                .andExpect(status().isForbidden());
    }

    /** Cycle-11 A: no Boot-generated default user exists when the admin surface is disabled. The only
     *  UserDetailsService is our deliberate EMPTY store (no generated password is created or logged). */
    @Test
    void noGeneratedOrDefaultUserWhenDisabled() {
        var uds = ctx.getBean(org.springframework.security.core.userdetails.UserDetailsService.class);
        assertThrows(org.springframework.security.core.userdetails.UsernameNotFoundException.class,
                () -> uds.loadUserByUsername("user"),
                "no Boot-generated default 'user' principal must exist");
        // the deny-all disabled chain is present
        assertTrue(ctx.getBeanNamesForType(org.springframework.security.web.SecurityFilterChain.class).length >= 1);
    }

    /** Cycle-11 B: an UNAPPROVED route outside /api/admin/** must NOT be public — the global deny-all
     *  catch-all covers it (fail-closed), so no future controller leaks by missing a matcher. */
    @Test
    void unapprovedRouteIsDeniedNotPublic() throws Exception {
        int status = mvc.perform(get("/unapproved-surface")).andReturn().getResponse().getStatus();
        assertTrue(status == 403 || status == 401,
                "an unmatched route must be denied by the global catch-all, got " + status);
    }
}
