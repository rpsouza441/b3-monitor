package dev.b3monitor.domain.rule;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Immutable absolute-price rule definition. Comparison is done at the declared
 * decimal {@code precision} BEFORE any display rounding. {@code hysteresis} is the
 * boundary a value must clear (on the opposite side) before the rule can rearm,
 * suppressing flapping around the threshold.
 *
 * @param id         stable rule id
 * @param ticker     asset this rule watches
 * @param comparator ABOVE/BELOW (strict)
 * @param threshold  BRL price threshold
 * @param precision  decimal places compared
 * @param hysteresis non-negative rearm margin (0 = rearm as soon as condition is false)
 * @param revision   monotonic revision; a bump re-baselines the persisted rule state
 */
public record PriceRule(
        String id,
        String ticker,
        Comparator comparator,
        BigDecimal threshold,
        int precision,
        BigDecimal hysteresis,
        long revision
) {
    public PriceRule {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(ticker, "ticker");
        Objects.requireNonNull(comparator, "comparator");
        Objects.requireNonNull(threshold, "threshold");
        if (precision < 0) throw new IllegalArgumentException("precision must be >= 0");
        if (hysteresis == null || hysteresis.signum() < 0)
            throw new IllegalArgumentException("hysteresis must be >= 0");
        if (revision < 1) throw new IllegalArgumentException("revision must be >= 1");
    }

    /** Convenience constructor defaulting revision to 1. */
    public PriceRule(String id, String ticker, Comparator comparator,
                     BigDecimal threshold, int precision, BigDecimal hysteresis) {
        this(id, ticker, comparator, threshold, precision, hysteresis, 1);
    }
}
