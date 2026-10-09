package dev.b3monitor.domain.rule;

/**
 * Tri-state rule evaluation. UNKNOWN is distinct from FALSE: an ineligible/absent
 * input yields UNKNOWN and must NEVER trigger or rearm an alert.
 */
public enum RuleOutcome {
    TRUE,
    FALSE,
    UNKNOWN
}
