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
        long revision,
        RuleMode mode
) {
    public PriceRule {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(ticker, "ticker");
        Objects.requireNonNull(comparator, "comparator");
        Objects.requireNonNull(threshold, "threshold");
        Objects.requireNonNull(mode, "mode");
        if (precision < 0) throw new IllegalArgumentException("precision must be >= 0");
        if (hysteresis == null || hysteresis.signum() < 0)
            throw new IllegalArgumentException("hysteresis must be >= 0");
        if (revision < 1) throw new IllegalArgumentException("revision must be >= 1");
    }

    /**
     * Convenience constructor defaulting revision to 1 and mode to {@link RuleMode#UNSELECTED}
     * (cycle-8 review C: there is NO implicit CROSSING — an operational rule must select its mode
     * explicitly via {@code withMode}/the registry, and the evaluator fails closed on UNSELECTED).
     */
    public PriceRule(String id, String ticker, Comparator comparator,
                     BigDecimal threshold, int precision, BigDecimal hysteresis) {
        this(id, ticker, comparator, threshold, precision, hysteresis, 1, RuleMode.UNSELECTED);
    }

    /** Convenience constructor with an explicit revision, defaulting mode to UNSELECTED (fail-closed). */
    public PriceRule(String id, String ticker, Comparator comparator,
                     BigDecimal threshold, int precision, BigDecimal hysteresis, long revision) {
        this(id, ticker, comparator, threshold, precision, hysteresis, revision, RuleMode.UNSELECTED);
    }

    /** Return a copy of this rule with an explicit {@link RuleMode} — the only way to make it operable. */
    public PriceRule withMode(RuleMode newMode) {
        return new PriceRule(id, ticker, comparator, threshold, precision, hysteresis, revision, newMode);
    }
}
