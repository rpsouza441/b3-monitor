package dev.b3monitor.domain.dispatch;

import dev.b3monitor.domain.auth.OperationalAuthorization;
import dev.b3monitor.domain.rule.RuleRegistry;
import dev.b3monitor.persistence.OutboxEntity;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The dispatch eligibility guard FAILS CLOSED (cycle-6 review P0-2): it denies unless the asset is
 * authorized, the rule is known/current/not-paused, the intent is unexpired and its source is fresh.
 */
class FailClosedDispatchEligibilityGuardTest {

    static final Instant NOW = Instant.parse("2026-10-06T17:00:00Z");

    private final OperationalAuthorization allowAsset = r ->
            new OperationalAuthorization.Decision(OperationalAuthorization.Status.AUTHORIZED, "test");
    private final OperationalAuthorization denyAsset = r ->
            new OperationalAuthorization.Decision(OperationalAuthorization.Status.NOT_AUTHORIZED, "test");

    private OutboxEntity row(long revision, Instant sourceAsOf, Instant expiresAt) {
        return new OutboxEntity("k", "r1", "WEGE3", "msg", revision, 1, sourceAsOf, NOW, expiresAt);
    }

    private RuleRegistry registry(long currentRev, boolean paused, boolean disabled) {
        return ruleId -> Optional.of(new RuleRegistry.RuleStatus(ruleId, currentRev, paused, disabled));
    }

    private FailClosedDispatchEligibilityGuard guard(OperationalAuthorization auth, RuleRegistry reg) {
        return new FailClosedDispatchEligibilityGuard(auth, reg, 2700);
    }

    @Test
    void unknownRuleFailsClosed() {
        var g = guard(allowAsset, ruleId -> Optional.empty());  // EmptyRuleRegistry behaviour
        assertEquals(DispatchEligibilityGuard.Denial.UNKNOWN,
                g.evaluate(row(1, NOW.minusSeconds(60), null), NOW).denial());
    }

    @Test
    void authorizedCurrentFreshPasses() {
        var g = guard(allowAsset, registry(1, false, false));
        assertTrue(g.evaluate(row(1, NOW.minusSeconds(60), null), NOW).mayDispatch());
    }

    @Test
    void deauthorizedAssetDenied() {
        var g = guard(denyAsset, registry(1, false, false));
        assertEquals(DispatchEligibilityGuard.Denial.ASSET_NOT_AUTHORIZED,
                g.evaluate(row(1, NOW.minusSeconds(60), null), NOW).denial());
    }

    @Test
    void pausedRuleDenied() {
        var g = guard(allowAsset, registry(1, true, false));
        assertEquals(DispatchEligibilityGuard.Denial.RULE_PAUSED_OR_DISABLED,
                g.evaluate(row(1, NOW.minusSeconds(60), null), NOW).denial());
    }

    @Test
    void supersededRevisionDenied() {
        var g = guard(allowAsset, registry(3, false, false));   // current rev is 3
        assertEquals(DispatchEligibilityGuard.Denial.SUPERSEDED_REVISION,
                g.evaluate(row(1, NOW.minusSeconds(60), null), NOW).denial());  // intent is rev 1
    }

    @Test
    void expiredIntentDenied() {
        var g = guard(allowAsset, registry(1, false, false));
        assertEquals(DispatchEligibilityGuard.Denial.EXPIRED,
                g.evaluate(row(1, NOW.minusSeconds(60), NOW.minusSeconds(1)), NOW).denial());
    }

    @Test
    void staleSourceDenied() {
        var g = guard(allowAsset, registry(1, false, false));
        assertEquals(DispatchEligibilityGuard.Denial.SOURCE_TOO_STALE,
                g.evaluate(row(1, NOW.minusSeconds(3000), null), NOW).denial());  // > 2700s
    }

    @Test
    void nullSourceFailsClosed() {
        var g = guard(allowAsset, registry(1, false, false));
        assertEquals(DispatchEligibilityGuard.Denial.UNKNOWN,
                g.evaluate(row(1, null, null), NOW).denial());
    }
}
