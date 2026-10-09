package dev.b3monitor.domain.quote;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Validates a {@link Quote} for signal eligibility. Freshness is MEASURED against an
 * injected {@link Clock}, never assumed. Rules (CONTRACTS, ADR-014):
 * <ul>
 *   <li>returned identity must equal requested identity;</li>
 *   <li>currency must be BRL;</li>
 *   <li>price must be strictly positive;</li>
 *   <li>a source/market timestamp must be present (else fail closed — MISSING_SOURCE_TIME);</li>
 *   <li>source time must not be in the future beyond {@code maxSkew};</li>
 *   <li>source time must be no older than {@code maxAge} (the 45-min hard guard by default);</li>
 *   <li>the provider stale flag makes the quote degraded/ineligible.</li>
 * </ul>
 * The validator reports ALL applicable reasons, not just the first.
 */
public class QuoteValidator {

    private final Clock clock;
    private final Duration maxAge;
    private final Duration maxSkew;

    public QuoteValidator(Clock clock, Duration maxAge, Duration maxSkew) {
        this.clock = clock;
        this.maxAge = maxAge;
        this.maxSkew = maxSkew;
    }

    public QuoteValidation validate(Quote q) {
        List<QuoteRejectionReason> reasons = new ArrayList<>();

        if (!q.identityMatches()) {
            reasons.add(QuoteRejectionReason.IDENTITY_MISMATCH);
        }
        if (q.currency() == null || !q.currency().trim().equalsIgnoreCase("BRL")) {
            reasons.add(QuoteRejectionReason.WRONG_CURRENCY);
        }
        if (q.price() == null || q.price().compareTo(BigDecimal.ZERO) <= 0) {
            reasons.add(QuoteRejectionReason.NON_POSITIVE_PRICE);
        }
        if (q.providerStale()) {
            reasons.add(QuoteRejectionReason.PROVIDER_STALE_FLAG);
        }

        Instant now = clock.instant();
        Instant src = q.sourceTime();
        if (src == null) {
            reasons.add(QuoteRejectionReason.MISSING_SOURCE_TIME); // fail closed
        } else {
            if (src.isAfter(now.plus(maxSkew))) {
                reasons.add(QuoteRejectionReason.FUTURE_SOURCE_TIME);
            }
            if (Duration.between(src, now).compareTo(maxAge) > 0) {
                reasons.add(QuoteRejectionReason.STALE_SOURCE_TIME);
            }
        }

        return reasons.isEmpty() ? QuoteValidation.pass() : QuoteValidation.fail(reasons);
    }
}
