package dev.b3monitor.domain.rule;

import dev.b3monitor.domain.quote.Quote;
import dev.b3monitor.domain.quote.QuoteValidator;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.*;

import static org.junit.jupiter.api.Assertions.*;

class RuleEvaluatorTest {

    private static final Instant NOW = Instant.parse("2026-10-06T17:00:00Z");
    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
    private final QuoteValidator validator =
            new QuoteValidator(clock, Duration.ofMinutes(45), Duration.ofMinutes(2));
    private final RuleEvaluator evaluator = new RuleEvaluator(validator);

    private Quote quote(String req, String ret, BigDecimal price, boolean stale, Instant src) {
        return new Quote(req, ret, false, "BRL", price, null, null, src, NOW, stale);
    }

    private Quote remappedQuote(String req, String ret, BigDecimal price, Instant src) {
        return new Quote(req, ret, true, "BRL", price, null, null, src, NOW, false);
    }

    private Quote wege(BigDecimal price) {
        return quote("WEGE3", "WEGE3", price, false, NOW.minusSeconds(60));
    }

    private PriceRule aboveRule() {
        return new PriceRule("r1", "WEGE3", Comparator.ABOVE, new BigDecimal("50.00"), 2, new BigDecimal("0.10"));
    }

    // ---- Regression P0-C: wrong-asset quote must never fire ----
    @Test
    void quoteForDifferentAssetYieldsUnknownAndNeverFires() {
        PriceRule rule = aboveRule(); // WEGE3
        RuleState state = new RuleState();
        // PETR4/PETR4 at 100 (self-consistent identity) but WRONG asset for this rule
        Quote petr = quote("PETR4", "PETR4", new BigDecimal("100.00"), false, NOW.minusSeconds(60));
        var r = evaluator.evaluate(rule, state, petr);
        assertEquals(RuleOutcome.UNKNOWN, r.outcome());
        assertFalse(r.fired(), "must NOT fire on a quote for another asset");
        assertFalse(r.assetMatched());
        assertTrue(state.isUnbaselined(), "state untouched on asset mismatch");
    }

    // ---- Regression P0-D: first observation already-true must not fire ----
    @Test
    void freshRuleAlreadyAboveDoesNotFireOnFirstObservation() {
        PriceRule rule = aboveRule();
        RuleState state = new RuleState();
        var first = evaluator.evaluate(rule, state, wege(new BigDecimal("100.00"))); // above 50 at baseline
        assertEquals(RuleOutcome.TRUE, first.outcome());
        assertFalse(first.fired(), "baseline-true must NOT fire an initial alert");
        assertTrue(state.isLatched(), "baseline-true latches");
    }

    @Test
    void firesOnceOnUpwardCrossingThenDedups() {
        PriceRule rule = aboveRule();
        RuleState state = new RuleState();
        evaluator.evaluate(rule, state, wege(new BigDecimal("49.00")));   // baseline false → ARMED
        var cross = evaluator.evaluate(rule, state, wege(new BigDecimal("50.01")));
        assertEquals(RuleOutcome.TRUE, cross.outcome());
        assertTrue(cross.fired(), "first eligible crossing fires");
        var still = evaluator.evaluate(rule, state, wege(new BigDecimal("51.00")));
        assertFalse(still.fired(), "dedup while latched");
    }

    @Test
    void strictComparatorEqualityIsFalse() {
        PriceRule rule = aboveRule();
        RuleState state = new RuleState();
        evaluator.evaluate(rule, state, wege(new BigDecimal("49.00"))); // baseline ARMED
        var atThreshold = evaluator.evaluate(rule, state, wege(new BigDecimal("50.00")));
        assertEquals(RuleOutcome.FALSE, atThreshold.outcome());
        assertFalse(atThreshold.fired());
    }

    @Test
    void rearmRequiresHysteresisClearance() {
        PriceRule rule = aboveRule(); // threshold 50.00, hysteresis 0.10 → rearm at <= 49.90
        RuleState state = new RuleState();
        evaluator.evaluate(rule, state, wege(new BigDecimal("49.00"))); // baseline ARMED
        evaluator.evaluate(rule, state, wege(new BigDecimal("50.50"))); // fire + latch
        evaluator.evaluate(rule, state, wege(new BigDecimal("49.95"))); // above rearm boundary → no rearm
        var noRearm = evaluator.evaluate(rule, state, wege(new BigDecimal("50.60")));
        assertFalse(noRearm.fired(), "49.95 did not clear hysteresis");
        evaluator.evaluate(rule, state, wege(new BigDecimal("49.80"))); // clears → rearm
        var reFire = evaluator.evaluate(rule, state, wege(new BigDecimal("50.70")));
        assertTrue(reFire.fired(), "fires again after hysteresis clearance");
    }

    @Test
    void unknownQuoteNeverFiresOrChangesState() {
        PriceRule rule = aboveRule();
        RuleState state = new RuleState();
        var r = evaluator.evaluate(rule, state, quote("WEGE3", "WEGE3", new BigDecimal("99.00"), true, NOW.minusSeconds(60)));
        assertEquals(RuleOutcome.UNKNOWN, r.outcome());
        assertFalse(r.fired());
        assertTrue(state.isUnbaselined(), "UNKNOWN leaves state untouched (still unbaselined)");
    }

    @Test
    void missingSourceTimeYieldsUnknownNotFalse() {
        PriceRule rule = aboveRule();
        RuleState state = new RuleState();
        var r = evaluator.evaluate(rule, state, quote("WEGE3", "WEGE3", new BigDecimal("99.00"), false, null));
        assertEquals(RuleOutcome.UNKNOWN, r.outcome());
        assertFalse(r.fired());
    }

    @Test
    void remappedSymbolIsRejectedAsAssetMismatch() {
        PriceRule rule = aboveRule();
        RuleState state = new RuleState();
        // Brapi 'changed=true' is modelled as a SEPARATE flag; the literal symbol is preserved.
        Quote remapped = remappedQuote("WEGE3", "WEGE3", new BigDecimal("100.00"), NOW.minusSeconds(60));
        var r = evaluator.evaluate(rule, state, remapped);
        assertEquals(RuleOutcome.UNKNOWN, r.outcome());
        assertFalse(r.assetMatched(), "a provider-remapped quote must fail-closed for the rule asset");
        assertTrue(state.isUnbaselined(), "remap leaves state untouched");
    }

    @Test
    void belowRuleFiresOnDownwardCrossing() {
        PriceRule rule = new PriceRule("r2", "WEGE3", Comparator.BELOW, new BigDecimal("50.00"), 2, BigDecimal.ZERO);
        RuleState state = new RuleState();
        evaluator.evaluate(rule, state, wege(new BigDecimal("50.50"))); // baseline false (above) → ARMED
        var cross = evaluator.evaluate(rule, state, wege(new BigDecimal("49.99")));
        assertEquals(RuleOutcome.TRUE, cross.outcome());
        assertTrue(cross.fired());
    }
}
