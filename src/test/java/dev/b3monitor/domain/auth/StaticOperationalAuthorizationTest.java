package dev.b3monitor.domain.auth;

import dev.b3monitor.domain.rule.Comparator;
import dev.b3monitor.domain.rule.PriceRule;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The default operational-authorization policy authorizes NOTHING on its own (cycle-5 review P1).
 * Every asset is {@code NOT_AUTHORIZED}; SNAG11 is {@code PARTIAL_IDENTITY}; KNHY11 is
 * {@code QUARANTINED}; an unknown ticker fails closed. None of these {@code mayFetch()}.
 */
class StaticOperationalAuthorizationTest {

    private final OperationalAuthorization auth = new StaticOperationalAuthorization();

    private PriceRule rule(String ticker) {
        return new PriceRule("r", ticker, Comparator.ABOVE, new BigDecimal("10"), 2, BigDecimal.ZERO);
    }

    @Test
    void everyKnownAssetDefaultsToNotAuthorized() {
        for (String t : new String[]{"WEGE3", "BPAC11", "VRTA11", "PETR4"}) {
            var d = auth.evaluate(rule(t));
            assertEquals(OperationalAuthorization.Status.NOT_AUTHORIZED, d.status(), t);
            assertFalse(d.mayFetch(), t + " must not be fetchable");
        }
    }

    @Test
    void snag11IsPartialIdentityAndMayNotFetch() {
        var d = auth.evaluate(rule("SNAG11"));
        assertEquals(OperationalAuthorization.Status.PARTIAL_IDENTITY, d.status());
        assertFalse(d.mayFetch());
    }

    @Test
    void knhy11IsQuarantinedAndMayNotFetch() {
        var d = auth.evaluate(rule("KNHY11"));
        assertEquals(OperationalAuthorization.Status.QUARANTINED, d.status());
        assertFalse(d.mayFetch());
    }

    @Test
    void unknownTickerFailsClosed() {
        var d = auth.evaluate(rule("ZZZZ99"));
        assertEquals(OperationalAuthorization.Status.NOT_AUTHORIZED, d.status());
        assertFalse(d.mayFetch());
    }

    @Test
    void caseAndWhitespaceAreNormalized() {
        assertEquals(OperationalAuthorization.Status.QUARANTINED, auth.evaluate(rule("  knhy11 ")).status());
    }
}
