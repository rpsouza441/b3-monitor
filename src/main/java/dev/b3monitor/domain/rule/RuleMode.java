package dev.b3monitor.domain.rule;

/**
 * Evaluation mode of a rule (cycle-7 review P0-live). The approved contract says the default is
 * {@link #UNSELECTED} — there is NO implicit CROSSING. A human must explicitly select a mode before a
 * rule can operate, and the scheduler/guard FAIL CLOSED on {@link #UNSELECTED}. {@link #LEVEL} stays
 * NOT ACTIVATABLE until the Q-19 initial-token policy is explicitly approved; it is modelled but no
 * code path enables an initial-on-create/edit/resume opportunity.
 */
public enum RuleMode {
    /** No mode chosen yet — the default. Never collected, never dispatched (fail-closed). */
    UNSELECTED,
    /** Fire once on a persisted FALSE→TRUE crossing (the implemented semantics). */
    CROSSING,
    /** Initial-level opt-in — BLOCKED pending Q-19 approval; not implemented/enabled. */
    LEVEL;

    public boolean isOperable() { return this == CROSSING; }   // LEVEL blocked, UNSELECTED fail-closed
}
