package dev.b3monitor.domain.rule;

import java.util.Optional;

/**
 * Minimal typed rule registry needed by the dispatch revision/pause checks (cycle-6 item H.1). It
 * answers two questions the eligibility guard needs: what is the CURRENT authorized revision of a
 * rule, and is the rule paused/disabled. This is a port; the persistent, authenticated admin-managed
 * implementation is future work. The production default ({@code EmptyRuleRegistry}) knows no rules
 * and therefore makes the guard fail closed — nothing dispatches until a rule is explicitly
 * registered by an operator surface.
 */
public interface RuleRegistry {

    /** A registered rule's dispatch-relevant status. */
    record RuleStatus(String ruleId, long currentRevision, boolean paused, boolean disabled) {}

    /** The current status of {@code ruleId}, or empty when the rule is unknown (→ guard fails closed). */
    Optional<RuleStatus> status(String ruleId);
}
