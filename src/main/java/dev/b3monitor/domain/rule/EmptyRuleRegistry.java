package dev.b3monitor.domain.rule;

import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Production default rule registry: knows NO rules. Because the dispatch eligibility guard fails
 * closed on an unknown rule, this guarantees nothing can be dispatched until a real, operator-managed
 * registry (persistent, authenticated — future work) registers a rule explicitly. There is
 * deliberately no permissive production default (cycle-6 review P0-2).
 */
@Component
public class EmptyRuleRegistry implements RuleRegistry {
    @Override
    public Optional<RuleStatus> status(String ruleId) {
        return Optional.empty();   // unknown → guard denies (fail closed)
    }
}
