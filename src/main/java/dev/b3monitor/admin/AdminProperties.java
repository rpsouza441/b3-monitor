package dev.b3monitor.admin;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Admin-surface configuration (cycle-10 item C / SEC-01). FAIL-CLOSED defaults:
 * <ul>
 *   <li>{@code enabled=false} — the private admin HTTP surface is OFF unless explicitly turned on;</li>
 *   <li>{@code bindAddress=127.0.0.1} — loopback only, never a public interface, by default;</li>
 *   <li>no password is ever stored here or in committed config — when enabled, the username and a
 *       one-way password HASH come from the environment
 *       ({@code B3MONITOR_ADMIN_USERNAME} / {@code B3MONITOR_ADMIN_PASSWORD_HASH}); if either is missing
 *       while {@code enabled=true}, the context fails to start (no anonymous fallback, no generated Boot
 *       password acting as an accidental credential).</li>
 * </ul>
 */
@ConfigurationProperties(prefix = "b3monitor.admin")
public class AdminProperties {

    /** Master switch for the private admin surface. Default false (fail-closed). */
    private boolean enabled = false;

    /** HTTP bind address; loopback by default. (Enforced via server.address at runtime.) */
    private String bindAddress = "127.0.0.1";

    /** Admin username (from env B3MONITOR_ADMIN_USERNAME when enabled). Never a committed default. */
    private String username = "";

    /** One-way password hash (from env B3MONITOR_ADMIN_PASSWORD_HASH when enabled). Never plaintext. */
    private String passwordHash = "";

    /**
     * Browser-session inactivity timeout in seconds (SEC-01, cycle-12 C). Safe bounded default of 30
     * minutes; NEVER infinite. A browser session idle longer than this can no longer access the private
     * UI/API — distinct from HTTP Basic, which re-authenticates whenever credentials are resent. The
     * value is applied to {@code server.servlet.session.timeout} via {@link #effectiveSessionTimeout()}
     * and shown in the operator status view.
     */
    private int sessionTimeoutSeconds = 1800;

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public String getBindAddress() { return bindAddress; }
    public void setBindAddress(String bindAddress) { this.bindAddress = bindAddress; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }
    public int getSessionTimeoutSeconds() { return sessionTimeoutSeconds; }
    public void setSessionTimeoutSeconds(int s) { this.sessionTimeoutSeconds = s; }

    /** Effective, bounded session timeout; coerces a non-positive value to the safe default (never infinite). */
    public java.time.Duration effectiveSessionTimeout() {
        return java.time.Duration.ofSeconds(sessionTimeoutSeconds > 0 ? sessionTimeoutSeconds : 1800);
    }
}
