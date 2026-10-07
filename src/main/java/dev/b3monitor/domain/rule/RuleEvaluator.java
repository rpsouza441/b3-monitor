package dev.b3monitor.domain.rule;

import dev.b3monitor.domain.quote.Quote;
import dev.b3monitor.domain.quote.QuoteRejectionReason;
import dev.b3monitor.domain.quote.QuoteValidation;
import dev.b3monitor.domain.quote.QuoteValidator;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * Evaluates a {@link PriceRule} against a {@link Quote} using CROSSING semantics.
 *
 * <p>Hard rules (from the independent review P0-C / P0-D):
 * <ul>
 *   <li><b>Asset identity:</b> the quote's identity must match {@link PriceRule#ticker()}.
 *       A quote for a different asset (even if its own requested==returned) yields
 *       {@link RuleOutcome#UNKNOWN} and never fires.</li>
 *   <li><b>Mandatory baseline:</b> a rule starts {@code UNBASELINED}; the first eligible
 *       observation only establishes the baseline (ARMED or LATCHED) and can NEVER fire.
 *       There is no optional out-of-band baseline step.</li>
 *   <li>A fire happens only on an eligible FALSE→TRUE crossing while {@code ARMED}.</li>
 *   <li>UNKNOWN (ineligible quote or identity mismatch) leaves state untouched.</li>
 *   <li>Rearm requires clearing the threshold by at least {@code hysteresis}.</li>
 * </ul>
 */
public class RuleEvaluator {

    private final QuoteValidator validator;

    public RuleEvaluator(QuoteValidator validator) {
        this.validator = validator;
    }

    public EvaluationResult evaluate(PriceRule rule, RuleState state, Quote quote) {
        // 1) Quote-level eligibility (identity/currency/price/freshness/stale).
        QuoteValidation v = validator.validate(quote);

        // 2) Rule/asset binding: the quote MUST be for this rule's asset.
        boolean assetMatches = matchesRuleAsset(rule, quote);
        if (!v.eligible() || !assetMatches) {
            List<QuoteRejectionReason> reasons = new ArrayList<>(v.reasons());
            return new EvaluationResult(RuleOutcome.UNKNOWN, false, assetMatches, v, reasons);
        }

        boolean conditionTrue = conditionTrue(rule, quote);

        // 3) Mandatory in-band baselining on the first eligible observation.
        if (state.isUnbaselined()) {
            if (conditionTrue) {
                state.toLatched();   // already true at baseline → no initial alert
                return new EvaluationResult(RuleOutcome.TRUE, false, true, v, List.of());
            } else {
                state.toArmed();
                return new EvaluationResult(RuleOutcome.FALSE, false, true, v, List.of());
            }
        }

        // 4) Steady-state CROSSING.
        if (conditionTrue) {
            boolean fired = false;
            if (state.isArmed()) {
                state.toLatched();
                fired = true;
            } // else already latched → dedup
            return new EvaluationResult(RuleOutcome.TRUE, fired, true, v, List.of());
        }

        // condition false → rearm only when cleared past hysteresis
        if (state.isLatched() && clearedForRearm(rule, quote)) {
            state.toArmed();
        }
        return new EvaluationResult(RuleOutcome.FALSE, false, true, v, List.of());
    }

    private boolean matchesRuleAsset(PriceRule rule, Quote quote) {
        // Fail-closed: a provider remap (changed=true) never matches, and both the requested
        // and the LITERAL returned symbol must equal the rule ticker.
        if (quote.remapped()) {
            return false;
        }
        String ruleTicker = rule.ticker().trim();
        return ruleTicker.equalsIgnoreCase(quote.requestedTicker().trim())
                && ruleTicker.equalsIgnoreCase(quote.returnedTicker().trim());
    }

    private boolean conditionTrue(PriceRule rule, Quote quote) {
        BigDecimal price = quote.price().setScale(rule.precision(), RoundingMode.HALF_EVEN);
        BigDecimal threshold = rule.threshold().setScale(rule.precision(), RoundingMode.HALF_EVEN);
        return switch (rule.comparator()) {
            case ABOVE -> price.compareTo(threshold) > 0;   // strict
            case BELOW -> price.compareTo(threshold) < 0;   // strict
        };
    }

    private boolean clearedForRearm(PriceRule rule, Quote quote) {
        BigDecimal price = quote.price().setScale(rule.precision(), RoundingMode.HALF_EVEN);
        BigDecimal threshold = rule.threshold().setScale(rule.precision(), RoundingMode.HALF_EVEN);
        BigDecimal h = rule.hysteresis().setScale(rule.precision(), RoundingMode.HALF_EVEN);
        return switch (rule.comparator()) {
            case ABOVE -> price.compareTo(threshold.subtract(h)) <= 0;
            case BELOW -> price.compareTo(threshold.add(h)) >= 0;
        };
    }

    /**
     * Outcome of one evaluation.
     * @param outcome       tri-state result
     * @param fired         whether a NEW logical alert fired this evaluation
     * @param assetMatched  whether the quote was for the rule's asset
     * @param validation    quote validation detail
     * @param reasons       rejection reasons (quote-level) when UNKNOWN
     */
    public record EvaluationResult(
            RuleOutcome outcome,
            boolean fired,
            boolean assetMatched,
            QuoteValidation validation,
            List<QuoteRejectionReason> reasons) {}
}
