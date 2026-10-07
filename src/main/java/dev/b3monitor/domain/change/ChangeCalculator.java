package dev.b3monitor.domain.change;

import dev.b3monitor.domain.quote.Quote;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.Optional;

/**
 * Computes price change INDEPENDENTLY of any provider-reported variation field
 * (ADR-011): delta = price - previousClose, percent = 100 * delta / previousClose.
 * Fails closed (returns empty) when previousClose is unknown, non-positive, or its
 * trading date/basis is absent — provider change fields are diagnostic only and are
 * never trusted. The caller decides eligibility; this class never fabricates a value.
 */
public final class ChangeCalculator {

    private static final MathContext MC = new MathContext(16, RoundingMode.HALF_EVEN);

    private ChangeCalculator() {}

    /** @return absolute decimal change, or empty when previousClose is unusable. */
    public static Optional<BigDecimal> absoluteChange(Quote q) {
        if (!usablePreviousClose(q)) return Optional.empty();
        return Optional.of(q.price().subtract(q.previousClose(), MC));
    }

    /** @return signed percentage change (e.g. -0.012058), or empty when unusable. */
    public static Optional<BigDecimal> percentChange(Quote q) {
        if (!usablePreviousClose(q)) return Optional.empty();
        BigDecimal delta = q.price().subtract(q.previousClose(), MC);
        BigDecimal pct = delta.multiply(BigDecimal.valueOf(100), MC)
                              .divide(q.previousClose(), MC);
        return Optional.of(pct);
    }

    /** previousClose must be present, strictly positive, with a known trading date. */
    private static boolean usablePreviousClose(Quote q) {
        return q.previousClose() != null
                && q.previousClose().compareTo(BigDecimal.ZERO) > 0
                && q.previousCloseDate() != null
                && q.price() != null;
    }
}
