package dev.b3monitor.admin;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;

import java.util.List;

/**
 * SEC-01 security for the private admin surface (cycle-10 C/E, HARDENED cycle-11 A/B/C/D).
 *
 * <h2>Global deny-by-default (cycle-11 B)</h2>
 * Both chains cover ALL requests (no {@code securityMatcher} scoping), ending in
 * {@code anyRequest().denyAll()} — so a future controller/UI route on any path can NEVER be public
 * merely because no filter chain matched.
 *
 * <h2>No Boot-generated credential (cycle-11 A)</h2>
 * {@code UserDetailsServiceAutoConfiguration} is excluded on the application class; this config supplies
 * the {@link UserDetailsService} deliberately: an EMPTY one when the surface is disabled (no principal,
 * nothing to log), and an env-backed single ADMIN when enabled. A blank username/hash while enabled
 * fails the context.
 *
 * <h2>Loopback invariant (cycle-11 C)</h2>
 * When enabled, {@link #validateLoopback} rejects a non-loopback / wildcard bind address at startup —
 * loopback is an invariant, not merely a default. No remote-admin escape hatch exists this cycle.
 *
 * <h2>Browser session (cycle-11 D)</h2>
 * The enabled chain serves a real {@code formLogin} (login page + processing URL inside the chain),
 * {@code logout} (invalidates the session + clears the cookie), and a session policy. CSRF is enforced
 * on every mutation; it is never disabled.
 */
@Configuration
@EnableConfigurationProperties(AdminProperties.class)
@org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
@org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity
public class AdminSecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    // ---- A: deliberate UserDetailsService (no Boot-generated password) ----

    /** Disabled surface → an EMPTY user store. No principal exists, so nothing is generated or logged. */
    @Bean
    @ConditionalOnProperty(name = "b3monitor.admin.enabled", havingValue = "false", matchIfMissing = true)
    public UserDetailsService noAdminUsers() {
        return new InMemoryUserDetailsManager(List.of());
    }

    /** Enabled surface → the single env-backed ADMIN principal; fail closed if its config is missing. */
    @Bean
    @ConditionalOnProperty(name = "b3monitor.admin.enabled", havingValue = "true")
    public UserDetailsService adminUserDetailsService(AdminProperties props) {
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

    // ---- C: loopback invariant when enabled ----

    @Bean
    @ConditionalOnProperty(name = "b3monitor.admin.enabled", havingValue = "true")
    public LoopbackBindValidator loopbackBindValidator(AdminProperties props) {
        return new LoopbackBindValidator(props.getBindAddress());
    }

    /** Loopback is an invariant when enabled (see {@link LoopbackBindValidator}); the bounded browser-session
     *  inactivity timeout (SEC-01, cycle-12 C) is surfaced in the status view via
     *  {@link AdminProperties#effectiveSessionTimeout()}. It is ENFORCED to the exact second by
     *  {@link #sessionTimeoutListener}, because the container's own {@code session.timeout} truncates to
     *  whole minutes and so cannot honor a sub-minute policy. */
    @Bean
    @ConditionalOnProperty(name = "b3monitor.admin.enabled", havingValue = "true")
    public org.springframework.boot.web.servlet.ServletListenerRegistrationBean<jakarta.servlet.http.HttpSessionListener>
            sessionTimeoutListener(AdminProperties props) {
        int seconds = (int) props.effectiveSessionTimeout().getSeconds();
        jakarta.servlet.http.HttpSessionListener listener = new jakarta.servlet.http.HttpSessionListener() {
            @Override public void sessionCreated(jakarta.servlet.http.HttpSessionEvent se) {
                se.getSession().setMaxInactiveInterval(seconds);   // exact seconds, honored by the container
            }
        };
        return new org.springframework.boot.web.servlet.ServletListenerRegistrationBean<>(listener);
    }

    /** Cycle-18 item J: register the analytics import body-size-limit filter (admin-enabled only). */
    @Bean
    @ConditionalOnProperty(name = "b3monitor.admin.enabled", havingValue = "true")
    public org.springframework.boot.web.servlet.FilterRegistrationBean<AnalyticsImportSizeLimitFilter>
            analyticsImportSizeLimitFilter() {
        var reg = new org.springframework.boot.web.servlet.FilterRegistrationBean<>(new AnalyticsImportSizeLimitFilter());
        reg.addUrlPatterns("/api/admin/imports/*", "/admin/imports/*");
        reg.setOrder(org.springframework.core.Ordered.HIGHEST_PRECEDENCE);
        return reg;
    }

    /** Fails context startup unless the configured bind address is a loopback literal. */
    static final class LoopbackBindValidator {
        LoopbackBindValidator(String bindAddress) {
            String a = bindAddress == null ? "" : bindAddress.trim();
            if (!isLoopbackLiteral(a)) {
                throw new IllegalStateException(
                        "b3monitor.admin.enabled=true requires a loopback bind address (127.0.0.1 or ::1); "
                                + "refusing non-loopback/wildcard '" + bindAddress + "' — no remote-admin gate is approved.");
            }
        }
        static boolean isLoopbackLiteral(String a) {
            // Accept only explicit loopback literals; reject wildcards (0.0.0.0, ::) and LAN/WAN literals.
            if (a.equals("127.0.0.1") || a.equals("::1") || a.equals("[::1]") || a.equalsIgnoreCase("localhost")) {
                return true;
            }
            // 127.0.0.0/8 is all loopback for IPv4.
            return a.startsWith("127.");
        }
    }

    // ---- B + D: global chains ----

    /** Fail-closed GLOBAL chain when the admin surface is DISABLED (default): deny every request. */
    @Bean
    @ConditionalOnProperty(name = "b3monitor.admin.enabled", havingValue = "false", matchIfMissing = true)
    public SecurityFilterChain adminDisabledChain(HttpSecurity http) throws Exception {
        http
            .authorizeHttpRequests(a -> a.anyRequest().denyAll())
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .csrf(Customizer.withDefaults())
            .exceptionHandling(e -> e.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.FORBIDDEN)));
        return http.build();
    }

    /** Authenticated, CSRF-protected GLOBAL chain when the admin surface is ENABLED. */
    @Bean
    @ConditionalOnProperty(name = "b3monitor.admin.enabled", havingValue = "true")
    public SecurityFilterChain adminEnabledChain(HttpSecurity http) throws Exception {
        http
            .authorizeHttpRequests(a -> a
                // the login page + its POST processing URL and logout are the only unauthenticated routes
                .requestMatchers("/admin/login", "/admin/logout").permitAll()
                // UI RBAC (cycle-12 B): the audit view and EVERY mutating POST under /admin/** are ADMIN-only,
                // matching the JSON API. These must precede the generic /admin/** authenticated rule.
                .requestMatchers(HttpMethod.GET, "/admin/audit").hasRole("ADMIN")
                .requestMatchers(HttpMethod.POST, "/admin/**").hasRole("ADMIN")
                // ordinary read-only UI pages: any authenticated user (ADMIN or a future VIEWER)
                .requestMatchers(HttpMethod.GET, "/admin/**").authenticated()
                // admin API: reads authenticated, mutations ADMIN
                .requestMatchers(HttpMethod.GET, "/api/admin/audit").hasRole("ADMIN")
                .requestMatchers(HttpMethod.GET, "/api/admin/**").authenticated()
                .requestMatchers("/api/admin/**").hasRole("ADMIN")
                // everything else is denied — nothing is public by omission
                .anyRequest().denyAll())
            .httpBasic(Customizer.withDefaults())    // retained for the local JSON API (resubmits creds each request)
            .formLogin(form -> form
                .loginPage("/admin/login").loginProcessingUrl("/admin/login")
                .defaultSuccessUrl("/admin/status", true).permitAll())
            .logout(logout -> logout
                .logoutUrl("/admin/logout").logoutSuccessUrl("/admin/login?logout")
                .invalidateHttpSession(true).deleteCookies("JSESSIONID").permitAll())
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
            .exceptionHandling(e -> e.defaultAuthenticationEntryPointFor(
                    new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED),
                    request -> request.getRequestURI().startsWith("/api/admin/")));
        return http.build();
    }
}
