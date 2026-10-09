package dev.b3monitor.domain.outbox;

import java.util.Map;
import java.util.Set;

import static dev.b3monitor.domain.outbox.OutboxState.*;

/**
 * The single source of truth for legal outbox state transitions (cycle-7 review P1-1 / item C).
 * Every state write goes through {@link #isLegal} so an operator action or a late result cannot drive
 * an illegal transition (e.g. {@code ACCEPTED → FAILED}, which would destroy submission evidence).
 *
 * <p>Legal edges:
 * <pre>
 *   PENDING    → IN_FLIGHT, CANCELLED, EXPIRED, SUPPRESSED
 *   IN_FLIGHT  → SENDING, PENDING (safe pre-send recovery), CANCELLED, EXPIRED, SUPPRESSED, UNKNOWN_OUTCOME
 *   SENDING    → ACCEPTED, UNKNOWN_OUTCOME, FAILED                          (only transport outcomes)
 *   UNKNOWN_OUTCOME → FAILED (operator abandon), PENDING (proof-gated requeue)
 *   FAILED     → FAILED (idempotent)
 *   ACCEPTED / CANCELLED / EXPIRED / SUPPRESSED → (none; terminal, evidence preserved)
 * </pre>
 * Notably a {@code SENDING}/{@code IN_FLIGHT} row is NEVER declared "definitely not sent": from
 * SENDING the only exits are the three transport outcomes; an ambiguous one is {@code UNKNOWN_OUTCOME}.
 */
public final class OutboxTransitions {

    private OutboxTransitions() {}

    private static final Map<OutboxState, Set<OutboxState>> LEGAL = Map.of(
            PENDING,         Set.of(IN_FLIGHT, CANCELLED, EXPIRED, SUPPRESSED),
            IN_FLIGHT,       Set.of(SENDING, PENDING, CANCELLED, EXPIRED, SUPPRESSED, UNKNOWN_OUTCOME),
            SENDING,         Set.of(ACCEPTED, UNKNOWN_OUTCOME, FAILED),
            UNKNOWN_OUTCOME, Set.of(FAILED, PENDING),
            FAILED,          Set.of(FAILED),
            ACCEPTED,        Set.of(),
            CANCELLED,       Set.of(),
            EXPIRED,         Set.of(),
            SUPPRESSED,      Set.of()
    );

    public static boolean isLegal(OutboxState from, OutboxState to) {
        if (from == to && to == FAILED) return true;           // idempotent abandon
        return LEGAL.getOrDefault(from, Set.of()).contains(to);
    }

    /** Throw if a transition is not permitted — used by the persistence layer to fail loudly. */
    public static void requireLegal(OutboxState from, OutboxState to) {
        if (!isLegal(from, to)) {
            throw new IllegalStateException("illegal outbox transition " + from + " → " + to);
        }
    }
}
