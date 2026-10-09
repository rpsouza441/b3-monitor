package dev.b3monitor.admin;

import org.junit.jupiter.api.Test;
import org.springframework.boot.security.autoconfigure.SecurityAutoConfiguration;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.security.core.userdetails.UserDetailsService;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Cycle-11 items A + C at the context level: the admin security configuration fails closed. Uses a
 * {@link WebApplicationContextRunner} with only {@link AdminSecurityConfig} + Spring Security
 * auto-configuration so the invariants are proven without the full application context.
 */
class AdminSecurityContextTest {

    private final WebApplicationContextRunner runner = new WebApplicationContextRunner()
            .withConfiguration(org.springframework.boot.autoconfigure.AutoConfigurations.of(SecurityAutoConfiguration.class))
            .withUserConfiguration(AdminSecurityConfig.class);

    @Test
    void disabledStartsWithEmptyUserStoreAndNoGeneratedPassword() {
        runner.withPropertyValues("b3monitor.admin.enabled=false").run(ctx -> {
            assertThat(ctx).hasNotFailed();
            // our deliberate empty UserDetailsService is present; no Boot-generated InMemory user manager
            assertThat(ctx).hasBean("noAdminUsers");
            assertThat(ctx).doesNotHaveBean("adminUserDetailsService");
        });
    }

    @Test
    void enabledWithoutCredentialsFailsToStart() {
        runner.withPropertyValues("b3monitor.admin.enabled=true", "b3monitor.admin.bind-address=127.0.0.1")
                .run(ctx -> assertThat(ctx).hasFailed());
    }

    @Test
    void enabledWithWildcardBindFailsToStart() {
        runner.withPropertyValues("b3monitor.admin.enabled=true",
                        "b3monitor.admin.username=admin",
                        "b3monitor.admin.password-hash=$2a$10$abcdefghijklmnopqrstuv",
                        "b3monitor.admin.bind-address=0.0.0.0")
                .run(ctx -> assertThat(ctx).hasFailed());
    }

    @Test
    void enabledWithLanBindFailsToStart() {
        runner.withPropertyValues("b3monitor.admin.enabled=true",
                        "b3monitor.admin.username=admin",
                        "b3monitor.admin.password-hash=$2a$10$abcdefghijklmnopqrstuv",
                        "b3monitor.admin.bind-address=192.168.0.9")
                .run(ctx -> assertThat(ctx).hasFailed());
    }

    @Test
    void enabledWithLoopbackAndCredentialsStarts() {
        runner.withPropertyValues("b3monitor.admin.enabled=true",
                        "b3monitor.admin.username=admin",
                        "b3monitor.admin.password-hash=$2a$10$abcdefghijklmnopqrstuv",
                        "b3monitor.admin.bind-address=127.0.0.1")
                .run(ctx -> {
                    assertThat(ctx).hasNotFailed();
                    assertThat(ctx).hasSingleBean(UserDetailsService.class);
                    assertThat(ctx).hasBean("loopbackBindValidator");
                });
    }
}
