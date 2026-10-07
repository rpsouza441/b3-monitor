package dev.b3monitor.admin;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Cycle-13 P2 — the session timeout is genuinely BOUNDED (not merely non-negative): default 1800, a short
 * test value preserved, non-positive coerced to the default, and a value above the 24h hard cap clamped.
 */
class AdminSessionTimeoutTest {

    private AdminProperties props(int seconds) {
        var p = new AdminProperties();
        p.setSessionTimeoutSeconds(seconds);
        return p;
    }

    @Test void defaultIs1800() { assertEquals(1800, new AdminProperties().effectiveSessionTimeout().getSeconds()); }
    @Test void shortTestValuePreserved() { assertEquals(1, props(1).effectiveSessionTimeout().getSeconds()); }
    @Test void nonPositiveCoercedToDefault() {
        assertEquals(1800, props(0).effectiveSessionTimeout().getSeconds());
        assertEquals(1800, props(-5).effectiveSessionTimeout().getSeconds());
    }
    @Test void aboveHardCapIsClamped() {
        assertEquals(AdminProperties.MAX_SESSION_TIMEOUT_SECONDS,
                props(AdminProperties.MAX_SESSION_TIMEOUT_SECONDS + 10_000).effectiveSessionTimeout().getSeconds());
    }
    @Test void atHardCapIsKept() {
        assertEquals(AdminProperties.MAX_SESSION_TIMEOUT_SECONDS,
                props(AdminProperties.MAX_SESSION_TIMEOUT_SECONDS).effectiveSessionTimeout().getSeconds());
    }
}
