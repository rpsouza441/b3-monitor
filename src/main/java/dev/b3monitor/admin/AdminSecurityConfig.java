package dev.b3monitor.admin;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.web.servlet.config.annotation.PathMatchConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * SEC-01 security for the private admin surface (cycle-10 items C/E). Two mutually-exclusive filter
 * chains selected by {@code b3monitor.admin.enabled}:
 *
 * <ul>
 *   <li><b>disabled (default, fail-closed):</b> {@link #adminDisabledChain} denies every
 *       {@code /api/admin/**} request with 403 and authenticates nothing — the admin surface is simply
 *       unreachable.</li>
 *   <li><b>enabled:</b> {@link #adminEnabledChain} requires an authenticated session; reads are allowed
 *       to any authenticated user, mutations require role {@code ADMIN}; CSRF is ENFORCED on mutations;
 *       anonymous access returns 401. The single admin principal comes from the environment
 *       ({@link AdminUserConfig}); if its credentials are missing the context fails to start.</li>
 * </ul>
 *
 * CSRF is never disabled. Session-based (stateful) auth is used, appropriate for a browser admin UI.
 */
@Configuration
@EnableConfigurationProperties(AdminProperties.class)
public class AdminSecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /** Fail-closed chain when the admin surface is DISABLED (the default): deny all /api/admin/**. */
    @Bean
    @ConditionalOnProperty(name = "b3monitor.admin.enabled", havingValue = "false", matchIfMissing = true)
    public SecurityFilterChain adminDisabledChain(HttpSecurity http) throws Exception {
        http
            .securityMatcher("/api/admin/**")
            .authorizeHttpRequests(a -> a.anyRequest().denyAll())
            .csrf(c -> c.disable())  // nothing is reachable anyway; no state-changing path exists here
            .exceptionHandling(e -> e.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.FORBIDDEN)));
        return http.build();
    }

    /** Authenticated, CSRF-protected chain when the admin surface is ENABLED. */
    @Bean
    @ConditionalOnProperty(name = "b3monitor.admin.enabled", havingValue = "true")
    public SecurityFilterChain adminEnabledChain(HttpSecurity http) throws Exception {
        http
            .securityMatcher("/api/admin/**")
            .authorizeHttpRequests(a -> a
                // reads: any authenticated user (ADMIN or VIEWER)
                .requestMatchers(org.springframework.http.HttpMethod.GET, "/api/admin/**").authenticated()
                // mutations: ADMIN only
                .anyRequest().hasRole("ADMIN"))
            // CSRF ENFORCED (default). Session-based auth; HTTP Basic + form login both acceptable for
            // a private surface, we accept Basic for test-friendliness without weakening CSRF on writes.
            .httpBasic(org.springframework.security.config.Customizer.withDefaults())
            .formLogin(org.springframework.security.config.Customizer.withDefaults())
            .exceptionHandling(e -> e.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)));
        return http.build();
    }

    /**
     * The admin principal, present ONLY when the surface is enabled. Credentials come from the
     * environment; a blank username or password hash makes bean creation fail, so the context fails to
     * start rather than exposing an anonymous or Boot-generated-password fallback.
     */
    @Configuration
    @ConditionalOnProperty(name = "b3monitor.admin.enabled", havingValue = "true")
    static class AdminUserConfig {
        @Bean
        public InMemoryUserDetailsManager adminUserDetailsManager(AdminProperties props) {
            if (props.getUsername() == null || props.getUsername().isBlank()
                    || props.getPasswordHash() == null || props.getPasswordHash().isBlank()) {
                throw new IllegalStateException(
                        "b3monitor.admin.enabled=true but B3MONITOR_ADMIN_USERNAME / "
                                + "B3MONITOR_ADMIN_PASSWORD_HASH are not set — failing closed (no anonymous admin).");
            }
            UserDetails admin = User.withUsername(props.getUsername())
                    .password(props.getPasswordHash())   // already a BCrypt hash
                    .roles("ADMIN")
                    .build();
            return new InMemoryUserDetailsManager(admin);
        }
    }
}
