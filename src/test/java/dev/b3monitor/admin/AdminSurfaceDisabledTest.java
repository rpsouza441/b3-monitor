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
}
