package dev.b3monitor.domain.rule;

import java.util.List;

/**
 * The single source of active monitor rules for the scheduler (cycle-7 item E). The scheduler no
 * longer takes a caller-provided {@code List<PriceRule>}; it reads from here, and the dispatch
 * eligibility guard reads revision/pause from the SAME backing store via {@link RuleRegistry}, so
 * collection and dispatch cannot use two independent sources of truth. A paused/disabled rule is
 * absent from {@link #activeRules()} (no collection) AND reports paused/absent to the guard
 * (no dispatch).
 */
public interface RuleSource {
    /** Enabled, non-paused rules at their current revision. Empty when none are configured. */
    List<PriceRule> activeRules();
}
