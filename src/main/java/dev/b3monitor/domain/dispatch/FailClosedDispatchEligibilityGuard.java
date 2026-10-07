package dev.b3monitor.domain.dispatch;

import dev.b3monitor.domain.auth.OperationalAuthorization;
import dev.b3monitor.domain.rule.Comparator;
import dev.b3monitor.domain.rule.PriceRule;
import dev.b3monitor.domain.rule.RuleRegistry;
import dev.b3monitor.persistence.OutboxEntity;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;

/**
 * Fail-closed production {@link DispatchEligibilityGuard}. It denies unless EVERY applicable
 * condition is provably satisfied (cycle-6 review P0-2):
 * <ul>
 *   <li>the row is not already in a terminal no-send / non-PENDING state;</li>
 *   <li>a {@link OperationalAuthorization} decision says the asset may fetch/operate;</li>
 *   <li>the {@link RuleRegistry} knows the rule, it is not paused/disabled, and the intent's
 *       {@code ruleRevision} equals the registry's current revision (a superseded or unknown
 *       revision is denied);</li>
 *   <li>the intent has not expired and its source-as-of is within the dispatch-time age policy.</li>
 * </ul>
 * Any missing evidence (unknown rule, null source time) ⇒ {@link Denial#UNKNOWN} / closed. The guard
 * performs NO provider I/O.
 */
@Component
public class FailClosedDispatchEligibilityGuard implements DispatchEligibilityGuard {

    private final OperationalAuthorization authorization;
    private final RuleRegistry registry;
    private final Duration maxSourceAge;

    public FailClosedDispatchEligibilityGuard(
            OperationalAuthorization authorization,
            RuleRegistry registry,
            @Value("${b3monitor.dispatch.max-source-age-seconds:2700}") long maxSourceAgeSeconds) {
        this.authorization = authorization;
        this.registry = registry;
        this.maxSourceAge = Duration.ofSeconds(maxSourceAgeSeconds);
    }

    @Override
    public Decision evaluate(OutboxEntity row, Instant now) {
        if (row.getState() != dev.b3monitor.domain.outbox.OutboxState.PENDING
                && row.getState() != dev.b3monitor.domain.outbox.OutboxState.IN_FLIGHT) {
            return new Decision(Denial.CANCELLED_OR_SUPPRESSED);
        }
        if (row.getSuppressionReason() != null) {
            return new Decision(Denial.CANCELLED_OR_SUPPRESSED);
        }
        // Asset authorization — reuse the same policy the collection path uses, fail-closed.
        var auth = authorization.evaluate(syntheticRule(row.getTicker()));
        if (!auth.mayFetch()) {
            return new Decision(Denial.ASSET_NOT_AUTHORIZED);
        }
        // Rule registry: known, not paused/disabled, and the intent's revision is current.
        var status = registry.status(row.getRuleId());
        if (status.isEmpty()) {
            return new Decision(Denial.UNKNOWN);               // unknown rule → fail closed
        }
        RuleRegistry.RuleStatus s = status.get();
        if (s.disabled() || s.paused()) {
            return new Decision(Denial.RULE_PAUSED_OR_DISABLED);
        }
        if (s.mode() == null || !s.mode().isOperable()) {
            return new Decision(Denial.UNKNOWN);               // UNSELECTED/LEVEL → fail closed
        }
        if (row.getRuleRevision() != s.currentRevision()) {
            return new Decision(Denial.SUPERSEDED_REVISION);
        }
        // Expiry + source-age.
        if (row.isExpired(now)) {
            return new Decision(Denial.EXPIRED);
        }
        Instant src = row.getSourceAsOf();
        if (src == null || src.plus(maxSourceAge).isBefore(now)) {
            return new Decision(src == null ? Denial.UNKNOWN : Denial.SOURCE_TOO_STALE);
        }
        return new Decision(Denial.OK);
    }

    /** The authorization port keys on the ticker; wrap it in a minimal rule to reuse that policy. */
    private static PriceRule syntheticRule(String ticker) {
        return new PriceRule("dispatch-check", ticker, Comparator.ABOVE, BigDecimal.ONE, 2, BigDecimal.ZERO);
    }
}
