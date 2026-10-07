package dev.b3monitor.domain.quote;

import java.util.List;

/**
 * Result of validating a {@link Quote} for a given use. Carries structured reasons
 * so an alert can explain WHY a quote was eligible or rejected. {@code eligible()} is
 * the generated record accessor; the factory methods are {@link #pass()} / {@link #fail(List)}.
 */
public record QuoteValidation(boolean eligible, List<QuoteRejectionReason> reasons) {

    public static QuoteValidation pass() {
        return new QuoteValidation(true, List.of());
    }

    public static QuoteValidation fail(List<QuoteRejectionReason> reasons) {
        return new QuoteValidation(false, List.copyOf(reasons));
    }

    public boolean hasReason(QuoteRejectionReason r) {
        return reasons.contains(r);
    }
}
