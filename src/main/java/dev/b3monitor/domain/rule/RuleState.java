package dev.b3monitor.domain.rule;

/**
 * Durable per-rule runtime state for CROSSING semantics (ADR-012), with an explicit,
 * persistent lifecycle so that baselining is NEVER an optional out-of-band step:
 *
 * <pre>
 *   UNBASELINED --(first eligible observation)--> ARMED (if condition false)
 *                                              \-> LATCHED (if condition already true: no initial alert)
 *   ARMED      --(eligible FALSE->TRUE)--------> LATCHED  (fires once)
 *   LATCHED    --(cleared past hysteresis)-----> ARMED    (rearm; no fire)
 * </pre>
 *
 * A freshly created rule starts {@link Phase#UNBASELINED}. The very first eligible
 * observation only establishes a baseline — it can never fire. An ineligible (UNKNOWN)
 * observation leaves the phase untouched.
 */
public final class RuleState {

    public enum Phase { UNBASELINED, ARMED, LATCHED }

    private Phase phase = Phase.UNBASELINED;

    public RuleState() {}

    /** Rehydrate a persisted state at a known phase (used by the persistence layer). */
    public static RuleState at(Phase phase) {
        RuleState s = new RuleState();
        s.phase = phase;
        return s;
    }

    public Phase getPhase() { return phase; }
    public boolean isUnbaselined() { return phase == Phase.UNBASELINED; }
    public boolean isArmed() { return phase == Phase.ARMED; }
    public boolean isLatched() { return phase == Phase.LATCHED; }

    void toArmed()   { this.phase = Phase.ARMED; }
    void toLatched() { this.phase = Phase.LATCHED; }

    @Override public String toString() { return "RuleState{phase=" + phase + '}'; }
}
