package dev.b3monitor.admin;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Cycle-11 item C — loopback is an INVARIANT (not just a default) when the admin surface is enabled.
 * The validator fails context startup for a wildcard or LAN/WAN bind address; loopback literals pass.
 */
class LoopbackBindValidatorTest {

    private void construct(String addr) {
        new AdminSecurityConfig.LoopbackBindValidator(addr);
    }

    @Test void loopbackIpv4Starts()      { assertDoesNotThrow(() -> construct("127.0.0.1")); }
    @Test void loopbackIpv6Starts()      { assertDoesNotThrow(() -> construct("::1")); }
    @Test void loopbackSubnetStarts()    { assertDoesNotThrow(() -> construct("127.0.0.5")); }
    @Test void localhostStarts()         { assertDoesNotThrow(() -> construct("localhost")); }

    @Test void wildcardIpv4Fails()       { assertThrows(IllegalStateException.class, () -> construct("0.0.0.0")); }
    @Test void wildcardIpv6Fails()       { assertThrows(IllegalStateException.class, () -> construct("::")); }
    @Test void lanAddressFails()         { assertThrows(IllegalStateException.class, () -> construct("192.168.1.10")); }
    @Test void publicAddressFails()      { assertThrows(IllegalStateException.class, () -> construct("10.0.0.5")); }
    @Test void blankFails()              { assertThrows(IllegalStateException.class, () -> construct("")); }
}
